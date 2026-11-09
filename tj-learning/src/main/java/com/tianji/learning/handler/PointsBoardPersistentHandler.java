package com.tianji.learning.handler;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final StringRedisTemplate redisTemplate;
    private final IPointsBoardSeasonService pointsBoardSeasonService;
    private final IPointsBoardService boardService;

    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason() {
        //获取上个月时间
        LocalDateTime time = LocalDateTime.now().minusMinutes(1);
        //查询赛季id
        Integer season = pointsBoardSeasonService.querySeasonByTime(time);
        if (season == null) {
            return;
        }
        //创建表
        boardService.createPointsBoardTableBySeason(season);
    }

    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB() {
        //获取上个月时间
        LocalDateTime time = LocalDateTime.now().minusMinutes(1);
        //查询赛季信息
        Integer season = pointsBoardSeasonService.querySeasonByTime(time);
        //存入threadLocal
        TableInfoContext.setInfo("points_board_" + season);
        //查询榜单数据
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        int pageNo = 1;
        int pageSize = 1000;
        //查询数据
        while (true) {
            List<PointsBoard> boardList = boardService.queryCurrentBoardList(key, pageNo, pageSize);
            if (CollUtils.isEmpty(boardList)) {
                break;
            }
            //将排名信息写入id
            boardList.forEach(b -> {
                b.setId(b.getRank().longValue());
                b.setRank(null);
            });
            //持久化到数据库 
            boardService.saveBatch(boardList);
            //翻页
            pageNo++;
        }
        TableInfoContext.remove();

    }

    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis() {
        LocalDateTime time = LocalDateTime.now().minusMinutes(1);
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        redisTemplate.unlink(key);
    }


}
