package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.type.LocalDateTypeHandler;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    private final StringRedisTemplate redisTemplate;

    @Override
    //新增积分记录
    public void addPointsRecord(Long userId, int points, PointsRecordType type) {
        LocalDateTime now = LocalDateTime.now();
        //判断当前方式是否有积分上限
        int maxPoints = type.getMaxPoints();
        int p = points;
        if (maxPoints > 0) {
            //有 判断是否达到上限

            LocalDateTime begin = DateUtils.getDayEndTime(now);
            LocalDateTime end = DateUtils.getDayEndTime(now);
            int currentPoints = queryUserPointsByTypeAndDate(userId, type,begin,end);
            //超过 结束
            if (currentPoints >= maxPoints) {
                return;
            }
            //没有 新增
            if (currentPoints + points > maxPoints) {
                p =  maxPoints - currentPoints;
            }
        }
        //新增
        PointsRecord pr = new PointsRecord();
        pr.setPoints(p);
        pr.setUserId(userId);
        pr.setType(type);
        save(pr);
        // 累计积分数据到redis的ZSet中
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        redisTemplate.opsForZSet().add(key, userId.toString(),p);
    }

    @Override
    public List<PointsStatisticsVO> queryMyPointsToday() {
        Long userid = UserContext.getUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = DateUtils.getDayEndTime(now);
        LocalDateTime begin = DateUtils.getDayStartTime(now);
        //构建查询条件
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(PointsRecord::getUserId,userid)
                .between(PointsRecord::getCreateTime,begin,end);
        //查询
        List<PointsRecord> list = getBaseMapper().queryUserPointsByDate(wrapper);
        if(CollUtils.isEmpty(list)){
            return CollUtils.emptyList();
        }
        List<PointsStatisticsVO> vos = new ArrayList<>(list.size());
        for (PointsRecord p : list) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setType(p.getType().getDesc());
            vo.setMaxPoints(p.getType().getMaxPoints());
            vo.setPoints(p.getPoints());
            vos.add(vo);
        }
        return vos;
    }

    private int queryUserPointsByTypeAndDate(Long userId, PointsRecordType type, LocalDateTime begin, LocalDateTime end) {
        LambdaQueryWrapper<PointsRecord> wrapper = new QueryWrapper<PointsRecord>().lambda()
                .eq(PointsRecord::getUserId, userId)
                .eq(type != null, PointsRecord::getType, type)
                .between(begin != null && end != null, PointsRecord::getCreateTime, begin, end);
        Integer points = getBaseMapper().queryUserPointsByTypeAndDate(wrapper);
        return points == null ? 0:points;
    }
}
