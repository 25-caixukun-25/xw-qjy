package com.tianji.learning.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Length;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-03-30
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final LearningRecordMapper recordMapper;

    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        LearningPlanPageVO result = new LearningPlanPageVO();
        //获取当前登录用户
        Long userId = UserContext.getUser();
        //获取本周起始时间
        LocalDate now = LocalDate.now();
        LocalDateTime begin = DateUtils.getWeekBeginTime(now);
        LocalDateTime end = DateUtils.getWeekEndTime(now);
        //查询总的统计数据
            //本周总的以学习小结
        Integer weekFinished = recordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .lt(LearningRecord::getFinishTime, end)
                .gt(LearningRecord::getFinishTime, begin)
        );
        result.setWeekFinished(weekFinished);
        //本周总的计划学习小节数量
        Integer weekTotalPlan = getBaseMapper().queryTotalPlan(userId);
        result.setWeekTotalPlan(weekTotalPlan);
            //todo 本周学习积分

        //查询分页数据
        //分页查询课表信息以及学习计划信息
        Page<LearningLesson> p = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = p.getRecords();
        if (CollUtils.isEmpty(records)) {
            return result.pageInfo(PageDTO.empty(p));
        }
            //查询课表对应的课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);
        //统计每个课程本周已学习的小节数量
        List<IdAndNumDTO> list = recordMapper.countLearnedSections(userId,begin,end);
        Map<Long, Integer> countMap = IdAndNumDTO.toMap(list);
        List<LearningPlanVO> voList = new ArrayList<>(records.size());
        //组装数据vo
        for (LearningLesson r : records) {
            LearningPlanVO vo = BeanUtil.copyProperties(r, LearningPlanVO.class);
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            if (cInfo != null) {
                vo.setCourseName(cInfo.getName());
                vo.setSections(cInfo.getSectionNum());
            }
            vo.setWeekLearnedSections(countMap.getOrDefault(r.getId(),0));
            voList.add(vo);
        }
        return result.pageInfo(p.getTotal(),p.getPages(),voList);
    }

    @Override
    public void createLearningPlan(Long courseId, Integer freq) {
        Long userid = UserContext.getUser();
        //查询课表中指定课程有关的数据
        LearningLesson lesson = queryByUserIdAndCourseId(userid, courseId);
        AssertUtils.isNotNull(lesson,"课程信息不存在");
        //修改数据
        LearningLesson l = new LearningLesson();
        l.setId(lesson.getId());
        l.setWeekFreq(freq);
        if (lesson.getPlanStatus() == PlanStatus.NO_PLAN) {
            l.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        updateById(l);
    }

    @Override
    public LearningLesson queryByUserIdAndCourseId(Long userid, Long courseId) {
        return getOne(buildUserIdAndCourseIdWrapper(userid, courseId));
    }

    @Override
    public Integer countLearning(Long courseId) {
        return lambdaQuery()
                .eq(LearningLesson::getCourseId,courseId)
                .in(LearningLesson::getStatus,
                        LessonStatus.LEARNING.getValue()
                        ,LessonStatus.FINISHED.getValue()
                        ,LessonStatus.NOT_BEGIN.getValue())
                .count();
    }

    @Override
    public LearningLessonVO queryLessonByCourseId(Long courseId) {
        //获取课程信息
        Long userid = UserContext.getUser();
        LearningLesson lesson = getOne(buildUserIdAndCourseIdWrapper(userid, courseId));
        if (lesson == null) {
            return null;
        }
        //转化为vo
        return BeanUtils.toBean(lesson, LearningLessonVO.class);
    }

    @Override
    public Long isLessonValid(Long courseId) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        if (userId == null) {
            return null;
        }
        // 2.查询课程
        LearningLesson lesson = getOne(buildUserIdAndCourseIdWrapper(userId, courseId));
        if (lesson == null) {
            return null;
        }
        return lesson.getId();
    }

    @Override
    //mq异步删除课程
    public void deleteCourseFromLesson(Long userId, Long courseId) {
        //获取userid
        if (userId == null){
            userId = UserContext.getUser();
        }
        //删除课程
        remove(buildUserIdAndCourseIdWrapper(userId, courseId));
    }

    private Wrapper<LearningLesson> buildUserIdAndCourseIdWrapper(Long userId, Long courseId) {
        return new QueryWrapper<LearningLesson>()
                .lambda()
                .eq(LearningLesson::getUserId,userId)
                .eq(LearningLesson::getCourseId,courseId);
    }

    @Override
    //查询我正在学习的课程
    public LearningLessonVO queryMyLearning() {
    //获取当前登录的用户id
        Long userid = UserContext.getUser();
        if(userid == null){
            log.error("当前用户id异常");

            throw new RuntimeException("当前用户id异常");
        }
        //获取当前用户的所有课程
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userid)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if(lesson == null){
            return null;
        }
        //转换为vo对象
        LearningLessonVO vo = BeanUtils.toBean(lesson, LearningLessonVO.class);
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(vo.getCourseId(), false, false);
        if (cInfo == null) {
            throw new BadRequestException("课程不存在");
        }
        vo.setCourseCoverUrl(cInfo.getCoverUrl());
        vo.setCourseName(cInfo.getName());
        vo.setSections(cInfo.getSectionNum());
        //根据userid来统计课程的数量
        Integer countAmount = lambdaQuery().eq(LearningLesson::getUserId, userid).count();
        vo.setCourseAmount(countAmount);
        //查询小节信息
        List<CataSimpleInfoDTO> cataSimpleInfoDTOList = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(lesson.getLatestSectionId()));
        if(!CollUtils.isEmpty(cataSimpleInfoDTOList)){
            CataSimpleInfoDTO cataInfo = cataSimpleInfoDTOList.get(0);
            vo.setLatestSectionName(cataInfo.getName());
            vo.setLatestSectionIndex(cataInfo.getCIndex());
        }
        return vo;
    }


    //分页查询我的课表
    @Override
    public PageDTO<LearningLessonVO> queryMyLesson(PageQuery query) {
        // 获取当前登录userid
        Long userid = UserContext.getUser();
        //分页查询
        // select * from learning_lesson where user_id = #{userId} order by latest_learn_time limit 0, 5
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userid)
                .page(query.toMpPage("latest_learn_time", false));

        List<LearningLesson> records = page.getRecords();
        //判断是否为空
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        //查询课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);
        //创建vo对象
        List<LearningLessonVO> list = new ArrayList<>(records.size());
        for (LearningLesson r : records) {
            LearningLessonVO vo = BeanUtils.toBean(r, LearningLessonVO.class);
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            vo.setCourseName(cInfo.getName());
            vo.setCourseCoverUrl(cInfo.getCoverUrl());
            vo.setSections(cInfo.getSectionNum());
            list.add(vo);
        }
        return PageDTO.of(page,list);
    }


    //添加课程到我的课表
    @Override
    public void addUserLessons(Long userId, List<Long> courseIds) {
        List<CourseSimpleInfoDTO> cinfoList = courseClient.getSimpleInfoList(courseIds);
        if(CollUtils.isEmpty(cinfoList)){
            //如果查询结果为空直接返回
            log.error("课程信息不存在，无法添加到课表");
            return;
        }
        List<LearningLesson> list = new ArrayList<>(cinfoList.size());
        for (CourseSimpleInfoDTO cInfo : cinfoList) {
            LearningLesson lesson = new LearningLesson();
            Integer validDuration = cInfo.getValidDuration();
            LocalDateTime now = LocalDateTime.now();
            //获取有效期判断课程是否过期
            if(validDuration != null && validDuration > 0){
                //获取过期时间
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            //填充其他信息
            lesson.setCreateTime(now);
            lesson.setUserId(userId);
            lesson.setCourseId(cInfo.getId());
            list.add(lesson);
        }
        //批量新增
        saveBatch(list);
    }


    private Map<Long, CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<LearningLesson> records) {
        //获取课程id集合
        Set<Long> cIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        //查询课程信息
        List<CourseSimpleInfoDTO> cinfoList = courseClient.getSimpleInfoList(cIds);
        if(CollUtils.isEmpty(cinfoList)){
            //如果查询结果为空直接返回
            log.error("课程信息不存在，无法添加到课表!");
            throw new BadRequestException("课程信息不存在");
        }
        return cinfoList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
    }


}
