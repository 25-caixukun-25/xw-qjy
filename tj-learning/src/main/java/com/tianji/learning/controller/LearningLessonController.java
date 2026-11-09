package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-03-30
 */
@RestController
@RequestMapping("/lessons")
@Api(tags = "我的课表相关接口")
@RequiredArgsConstructor
public class LearningLessonController {

    private final ILearningLessonService lessonService;

    @GetMapping("/{courseId}/valid")
    @ApiOperation("校验播放视频资格")
    public Long isLessonValid(
            @ApiParam(value = "课程id", example = "1") @PathVariable("courseId") Long courseId) {
        return lessonService.isLessonValid(courseId);
    }
    @GetMapping("/plans")
    @ApiOperation("查询学习计划")
    public LearningPlanPageVO queryMyPlans(PageQuery query){
        return lessonService.queryMyPlans(query);
    }


    @PostMapping("/plans")
    @ApiOperation("创建学习计划")
    public void createLearningPlans(@Valid @RequestBody LearningPlanDTO planDTO){
        lessonService.createLearningPlan(planDTO.getCourseId(),planDTO.getFreq());
    }


    /**
     * 统计课程学习人数
     * @param courseId 课程id
     * @return 学习人数
     */
    @GetMapping("/lessons/{courseId}/count")
    public Integer countLearningLessonByCourse(@PathVariable("courseId") Long courseId){
        return lessonService.countLearning(courseId);
    }



    @GetMapping("/{courseId}")
    @ApiOperation("查询指定课程信息")
    public LearningLessonVO queryLessonByCourseId(
            @ApiParam(value = "课程id" ,example = "1") @PathVariable("courseId") Long courseId) {
        return lessonService.queryLessonByCourseId(courseId);
    }
    @DeleteMapping("/{courseId}")
    @ApiOperation("删除指定课程信息")
    public void deleteCourseFromLesson(
            @ApiParam(value = "课程id", example = "1") @PathVariable("courseId") Long courseId) {
        lessonService.deleteCourseFromLesson(null, courseId);
    }

    @GetMapping("/page")
    @ApiOperation("分页查询我的课表")
    public PageDTO<LearningLessonVO> queryMyLesson(PageQuery query) {
        return lessonService.queryMyLesson(query);
    }

    @GetMapping("/now")
    @ApiOperation("查询正在学习的课程")
    public LearningLessonVO queryLearning() {
        return lessonService.queryMyLearning();
    }
}
