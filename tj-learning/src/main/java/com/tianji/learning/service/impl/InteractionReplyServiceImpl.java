package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.remark.fallback.RemarkClientFallback;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-05-11
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final IInteractionQuestionService questionService;
    private final UserClient userClient;
    private final RemarkClient remarkClient;
    private final RabbitMqHelper mqHelper;

    @Override
    //显示或隐藏问题
    public void updateHidden(Long id, Boolean hidden) {
        // 1.查询
        InteractionReply old = getById(id);
        if (old == null) {
            return;
        }

        // 2.隐藏回答
        InteractionReply reply = new InteractionReply();
        reply.setId(id);
        reply.setHidden(hidden);
        updateById(reply);

        // 3.隐藏评论，先判断是否是回答，回答才需要隐藏下属评论
        if (old.getAnswerId() != null && old.getAnswerId() != 0) {
            // 3.1.有answerId，说明自己是评论，无需处理
            return;
        }
        // 3.2.没有answerId，说明自己是回答，需要隐藏回答下的评论
        lambdaUpdate()
                .set(InteractionReply::getHidden, hidden)
                .eq(InteractionReply::getAnswerId, id)
                .update();
    }

    @Override
    //管理端分页查询回答列表
    public PageDTO<ReplyVO> pageAdminReply(ReplyPageQuery pageQuery) {
        if (pageQuery.getQuestionId() == null && pageQuery.getAnswerId() == null) {
            throw new BadRequestException("章节和问题不能都为空！");
        }
        InteractionQuestion question = questionService.getById(pageQuery.getQuestionId());
        if (question == null) {
            return PageDTO.empty(0L, 0L);
        }

        Page<InteractionReply> page = lambdaQuery()
                .eq(InteractionReply::getQuestionId, pageQuery.getQuestionId())
                .eq(InteractionReply::getAnswerId, 0)
                .page(pageQuery.toMpPage("liked_times",false));
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(0L, 0L);
        }
        List<Long> ids = new ArrayList<>();
        for (InteractionReply r : records) {
            ids.add(r.getUserId());
        }
        Set<Long> bizLiked = remarkClient.isBizLiked(ids);
        //封装vo属性
        List<ReplyVO> vos = new ArrayList<>(records.size());
        for (InteractionReply r : records) {
            ReplyVO vo = BeanUtils.toBean(r, ReplyVO.class);
            vo.setLikedTimes(r.getLikedTimes());
            UserDTO user = userClient.queryUserById(r.getUserId());
            vo.setUserIcon(user.getIcon());
            vo.setUserName(user.getName());
            vo.setLikedTimes(ids.size());
            vo.setLiked(bizLiked.contains(UserContext.getUser()));
            Long targetReplyId = r.getTargetReplyId();
            if (targetReplyId != 0L) {
                UserDTO user2 = userClient.queryUserById(r.getTargetUserId());
                vo.setTargetUserName(user2.getName());
            }

            vos.add(vo);
        }
        return PageDTO.of(page, vos);
    }

    @Override
    //分页查询回答列表
    public PageDTO<ReplyVO> pageReply(ReplyPageQuery pageQuery) {
        if (pageQuery.getQuestionId() == null && pageQuery.getAnswerId() == null) {
            throw new BadRequestException("章节和问题不能都为空！");
        }
        InteractionQuestion question = questionService.getById(pageQuery.getQuestionId());
        if (question == null) {
            return PageDTO.empty(0L, 0L);
        }
        Page<InteractionReply> page = lambdaQuery()
                .eq(InteractionReply::getQuestionId, pageQuery.getQuestionId())
                .eq(InteractionReply::getAnswerId, 0)
                .page(pageQuery.toMpPage("liked_times",false));
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(0L, 0L);
        }
        List<Long> ids = new ArrayList<>();
        for (InteractionReply r : records) {
            ids.add(r.getUserId());
        }
        Set<Long> bizLiked = remarkClient.isBizLiked(ids);
        //封装vo属性
        List<ReplyVO> vos = new ArrayList<>(records.size());
        for (InteractionReply r : records) {
            ReplyVO vo = BeanUtils.toBean(r, ReplyVO.class);
            vo.setLikedTimes(ids.size());
            vo.setLiked(bizLiked.contains(UserContext.getUser()));
            if (!r.getAnonymity()) {
                UserDTO user = userClient.queryUserById(r.getUserId());
                vo.setUserIcon(user.getIcon());
                vo.setUserName(user.getName());
            }
            //判断被评论的用户是否是匿名的
            Long targetReplyId = r.getTargetReplyId();
            if (targetReplyId != 0L) {
                InteractionReply reply = getById(targetReplyId);
                if (!reply.getAnonymity()) {
                    //如果不是匿名才返回
                    UserDTO user = userClient.queryUserById(r.getTargetUserId());
                    vo.setTargetUserName(user.getName());
                }
            }
            vo.setHidden(null);
            vo.setUserId(null);
            vos.add(vo);
        }
        return PageDTO.of(page, vos);
    }

    @Override
    @Transactional
    //新增回答或评论
    public void saveReply(ReplyDTO replyDTO) {
        Long userId = UserContext.getUser();
        InteractionReply reply = BeanUtils.copyBean(replyDTO, InteractionReply.class);
        reply.setUserId(userId);
        save(reply);
        //判断是否是回答
        // 3.累加评论数或者累加回答数
        // 3.1.判断当前回复的类型是否是回答
        boolean isAnswer = replyDTO.getAnswerId() == null;
        if (!isAnswer) {
            // 3.2.是评论，则需要更新上级回答的评论数量
            lambdaUpdate()
                    .setSql("reply_times = reply_times + 1")
                    .eq(InteractionReply::getId, replyDTO.getAnswerId())
                    .update();
        }
        // 3.3.尝试更新问题表中的状态、 最近一次回答、回答数量
        questionService.lambdaUpdate()
                .set(isAnswer, InteractionQuestion::getLatestAnswerId, reply.getAnswerId())
                .setSql(isAnswer, "answer_times = answer_times + 1")
                .set(replyDTO.getIsStudent(), InteractionQuestion::getStatus, QuestionStatus.UN_CHECK.getValue())
                .eq(InteractionQuestion::getId, replyDTO.getQuestionId())
                .update();
        //发送mq通知积分添加
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.LEARN_SECTION,
                userId
        );

    }
}
