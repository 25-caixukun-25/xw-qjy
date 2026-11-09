package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.TableInfoContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.common.constants.Constant.POINTS_BOARD_TABLE_PREFIX;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-05-25
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final StringRedisTemplate redisTemplate;
    private final UserClient userClient;
    @Override
    public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query) {
         //判断是否是查询当前赛季
        Long season = query.getSeason();
        boolean isCurrent = season == null || season == 0;
        LocalDateTime now = LocalDateTime.now();
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);

        //查询我的积分排名
        PointsBoard myBoard = isCurrent ? queryMyCurrentBoard(key) ://查询当前赛季榜单 redis
                                          queryMyHistoryBoard(season); // 查询历史赛季榜单 mysql
        //查询榜单列表
        List<PointsBoard> list = isCurrent ? queryCurrentBoardList(key,query.getPageNo(),query.getPageSize()) ://查询当前赛季榜单 redis
                                             queryHistoryBoardList(query); // 查询历史赛季榜单 mysql
        //封装vo
        PointsBoardVO vo = new PointsBoardVO();
        if (myBoard != null) {
            vo.setRank(myBoard.getRank());
            vo.setPoints(myBoard.getPoints());
        }
        if (CollUtils.isEmpty(list)) {
            return vo;
        }

        //处理用户信息
        List<PointsBoardItemVO> items = new ArrayList<>(list.size());
        Set<Long> uIds = list.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> users = userClient.queryUserByIds(uIds);
        Map<Long, String> userMap = new HashMap<>(users.size());
        if (CollUtils.isNotEmpty(users)) {
           userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        }
        for (PointsBoard p : list) {
            PointsBoardItemVO v = new PointsBoardItemVO();
            v.setRank(p.getRank());
            v.setName(userMap.get(p.getUserId()));
            v.setPoints(p.getPoints());
            items.add(v);
        }
        vo.setBoardList(items);
        return vo;
    }

    @Override
    public void createPointsBoardTableBySeason(Integer season) {
        getBaseMapper().createPointsBoardTable(POINTS_BOARD_TABLE_PREFIX+season);
    }

    private PointsBoard queryMyHistoryBoard(Long season) {
        Long userid = UserContext.getUser();
        //计算表名
        TableInfoContext.setInfo(POINTS_BOARD_TABLE_PREFIX + season);
        Optional<PointsBoard> opt = lambdaQuery().eq(PointsBoard::getUserId, userid).oneOpt();
        if (opt.isEmpty()) {
            return null;
        }
        PointsBoard pb = opt.get();
        //将排名转换为id
        pb.setId(pb.getRank().longValue());
        pb.setRank(null);
        return pb;
    }

    private List<PointsBoard> queryHistoryBoardList(PointsBoardQuery query) {
        //计算表名
        TableInfoContext.setInfo(POINTS_BOARD_TABLE_PREFIX + query.getSeason());
        Page<PointsBoard> page = page(query.toMpPage());
        List<PointsBoard> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return CollUtils.emptyList();
        }
        records.forEach(p->{
            p.setId(p.getRank().longValue());
            p.setRank(null);
        });
        return records;
    }

    @Override
    public List<PointsBoard> queryCurrentBoardList(String key, int pageNo, int pageSize) {
        int from = (pageNo-1)*pageSize;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key, from, pageSize + from - 1);
        if(CollUtils.isEmpty(tuples)){
            return CollUtils.emptyList();
        }
        int rank = 1 + from;
        List<PointsBoard> list= new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            Double point = t.getScore();
            String userid = t.getValue();
            if (point == null || userid == null) {
                continue;
            }
            PointsBoard p = new PointsBoard();
            p.setPoints(point.intValue());
            p.setUserId(Long.valueOf(userid));
            p.setRank(rank);
            list.add(p);
        }
        return list;

    }



    private PointsBoard queryMyCurrentBoard(String key) {
        //绑定key 后续不用重复将key传入
        BoundZSetOperations<String, String> ops = redisTemplate.boundZSetOps(key);
        //获取用户
        String userid = UserContext.getUser().toString();
        Double points = ops.score(userid);
        Long rank = ops.reverseRank(userid);
        PointsBoard p = new PointsBoard();
        p.setRank(rank==null? 0:rank.intValue() +1);
        p.setPoints(points==null? 0:points.intValue());
        return p;
    }
}
