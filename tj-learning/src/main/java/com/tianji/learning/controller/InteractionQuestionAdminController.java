package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
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
@RequestMapping("/admin/questions")
@RequiredArgsConstructor
@Api(tags = "管理端互动问答相关接口")
public class InteractionQuestionAdminController {

    private final IInteractionQuestionService questionService;

    @GetMapping("/{id}")
    @ApiOperation("管理端根据id查询互动问题详情信息")
    public QuestionAdminVO selectById(@PathVariable Long id){
         return questionService.selectById(id);
    }
    @GetMapping("/page")
    @ApiOperation("管理端分页查询互动问题")
    public PageDTO<QuestionAdminVO> queryQuestionAdminPage(QuestionAdminPageQuery query){
        return questionService.queryQuestionAdminPage(query);
    }

    @PutMapping("/{id}/hidden/{hidden}")
    @ApiOperation("互动问题是否显示")
    public void updateHidden(@PathVariable("id") Long id,@PathVariable("hidden") Boolean hidden){
        questionService.updateHidden(id,hidden);
    }
}
