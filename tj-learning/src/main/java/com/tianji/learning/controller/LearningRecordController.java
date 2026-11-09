package com.tianji.learning.controller;


import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.api.dto.leanring.LearningRecordFormDTO;
import com.tianji.learning.service.ILearningRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学习记录表 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-04-01
 */
@RestController
@RequiredArgsConstructor
@Api(tags = "学习记录相关接口")
@RequestMapping("/learning-records")
public class LearningRecordController {

    private final ILearningRecordService recordService;

    @GetMapping("/course/{courseId}")
    @ApiOperation("查询指定课程的学习记录")
    public LearningLessonDTO queryLearningRecordByCourse(
            @ApiParam(value = "课程id",example = "2") @PathVariable("courseId") Long courseId){

        return recordService.queryLearningRecordByCourse(courseId);
    }

    @ApiOperation("提交学习记录")
    @PostMapping
    public void addLearningRecord(@RequestBody LearningRecordFormDTO formDTO){
        recordService.addLearningRecord(formDTO);
    }

}
