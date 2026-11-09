package com.tianji.learning.mq;

import com.tianji.common.constants.MqConstants;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LearningPointsListener {

    private final IPointsRecordService recordService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.points.queue",durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.WRITE_REPLY
    ))
    public void listenWriteReplyMessage(Long userid){
         recordService.addPointsRecord(userid,5, PointsRecordType.QA);
    }
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "sign.points.queue",durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.SIGN_IN
    ))
    //
    public void listenSignInReplyMessage(SignInMessage signInMessage){
        recordService.addPointsRecord(signInMessage.getUserId(),signInMessage.getPoints(), PointsRecordType.SIGN);
    }
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "learning.points.queue",durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.LEARN_SECTION
    ))
    //问答
    public void listenLearnSectionReplyMessage(Long userid){
        recordService.addPointsRecord(userid,10, PointsRecordType.LEARNING);
    }
}
