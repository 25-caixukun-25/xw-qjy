package com.tianji.learning.controller;


import com.tianji.api.dto.exam.QuestionDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-05-11
 */
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
@Api(tags = "互动问答相关接口")
public class InteractionQuestionController {

    private final IInteractionQuestionService questionService;



    @GetMapping("/{id}")
    @ApiOperation("根据id查看互动问题")
    public QuestionVO queryQuestionById(@PathVariable Long id){
        return questionService.queryQuestionById(id);
    }
    @DeleteMapping("/{id}")
    @ApiOperation("根据id删除互动问题")
    public void deleteQuestionById(@PathVariable Long id){
         questionService.deleteQuestionById(id);
    }

    @PutMapping("/{id}")
    @ApiOperation("修改互动问题")
    public void updateQuestion(@Valid @RequestBody QuestionFormDTO questionFormDTO,@PathVariable Long id){
        questionService.updateQuestion(questionFormDTO,id);
    }
    @PostMapping
    @ApiOperation("新增互动问题")
    public void saveQuestion(@Valid @RequestBody QuestionFormDTO questionFormDTO){
        questionService.saveQuestion(questionFormDTO);
    }

    @GetMapping("/page")
    @ApiOperation("分页查询互动问题")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query){
        return questionService.queryQuestionPage(query);
    }

}
