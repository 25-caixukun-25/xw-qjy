package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-05-11
 */
@RestController
@RequestMapping("/admin/replies")
@RequiredArgsConstructor
@Api(tags = "管理端互动问答的评论或回答")
public class InteractionReplyAdminController {

    private final IInteractionReplyService replyService;

    @PutMapping("/{id}/hidden/{hidden}")
    @ApiOperation("互动问题是否显示")
    public void updateHidden(@PathVariable("id") Long id,@PathVariable("hidden") Boolean hidden){
        replyService.updateHidden(id,hidden);
    }

    @GetMapping("/page")
    @ApiOperation("管理端分页查询回答")
    public PageDTO<ReplyVO> pageAdminReply(ReplyPageQuery pageQuery){
        return replyService.pageAdminReply(pageQuery);
    }
}
