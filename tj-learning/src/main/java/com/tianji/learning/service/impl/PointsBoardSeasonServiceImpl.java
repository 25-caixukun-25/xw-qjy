package com.tianji.learning.service.impl;

import com.tianji.common.utils.BeanUtils;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author author
 * @since 2026-05-25
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {
    @Override
    public Integer querySeasonByTime(LocalDateTime time) {
        Optional<PointsBoardSeason> optional = lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .gt(PointsBoardSeason::getEndTime, time)
                .oneOpt();

        return optional.map(PointsBoardSeason::getId).orElse(null);
    }
    @Override
    public List<PointsBoardSeasonVO> getBoardSeasons() {
        //查询所有赛季信息，按id降序排列
        List<PointsBoardSeason> seasons =lambdaQuery()
                .orderByDesc(PointsBoardSeason::getId)
                .list();

        //将PO转换为VO
        return seasons.stream()
                .map(season -> {
                    PointsBoardSeasonVO vo = new PointsBoardSeasonVO();
                    BeanUtils.copyProperties(season, vo);
                    return vo;
                })
                .collect(Collectors.toList());
    }


}
