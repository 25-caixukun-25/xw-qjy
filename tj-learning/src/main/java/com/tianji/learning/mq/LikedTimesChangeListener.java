package com.tianji.learning.mq;

import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.QA_LIKED_TIMES_KEY;


@Slf4j
@RequiredArgsConstructor
@Component
public class LikedTimesChangeListener {

    private final IInteractionReplyService replyService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.liked.times.queue",durable = "true"),
            exchange = @Exchange(name = LIKE_RECORD_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = QA_LIKED_TIMES_KEY
    ))
    public void listenReplyLikedTimesChange(List<LikeTimesDTO> likeTimesDTOs){
        log.debug("监听到评论回复数或点赞数变更的消息");
        List<InteractionReply> list = new ArrayList<>(likeTimesDTOs.size());
        for (LikeTimesDTO dto : likeTimesDTOs) {
            InteractionReply r = new InteractionReply();
            r.setLikedTimes(dto.getLikeTimes());
            r.setId(dto.getBizId());
            list.add(r);
        }

        replyService.updateBatchById(list);
    }
}
