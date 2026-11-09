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
@RequestMapping("/replies")
@RequiredArgsConstructor
@Api(tags = "互动问答的评论或回答")
public class InteractionReplyController {

    private final IInteractionReplyService replyService;

    @GetMapping("/page")
    @ApiOperation("分页查询回答")
    public PageDTO<ReplyVO> pageReply(ReplyPageQuery pageQuery){
       return replyService.pageReply(pageQuery);
    }

    @PostMapping
    @ApiOperation("新增评论或回答")
    public void saveReply(@RequestBody ReplyDTO replyDTO){
        replyService.saveReply(replyDTO);
    }
}
