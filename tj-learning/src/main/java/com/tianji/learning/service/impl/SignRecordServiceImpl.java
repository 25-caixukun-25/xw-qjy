package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-05-25
 */
@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;
       @Override
    //查询签到记录
    public int[] getSignRecord() {
        Long userId = UserContext.getUser();
        //获取当前时间
        LocalDate now = LocalDate.now();
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        int monthLength = now.lengthOfMonth();
        int[] arr = new int[monthLength];
        //获取本月从第一天开始到今天为止的所有签到记录
        List<Long> result = redisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create().get(
                        BitFieldSubCommands.BitFieldType.unsigned(now.getDayOfMonth())).valueAt(0));
        if (CollUtils.isEmpty(result)) {
            return null;
        }
        int num = result.get(0).intValue();
        int count = now.getDayOfMonth() - 1;
        while (num >0) {
           arr[count] = (num & 1);
           count--;
           num>>>=1;
        }
        return arr;
    }
// ... existing code ...


    @Override
    //签到
    public SignResultVO addSignRecords() {
        //获取当前登录用户
        Long userid = UserContext.getUser();
        //获取当前时间
        LocalDate now = LocalDate.now();
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userid + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        int offset = now.getDayOfMonth() -1;
        //保存签到信息
        Boolean exits = redisTemplate.opsForValue().setBit(key, offset, true);
        if(BooleanUtils.isTrue(exits)){
            throw new BizIllegalException("不允许重复签到");
        }
        //计算连续签到天数
        int signDays = countSignDays(key,now.getDayOfMonth());
        //计算签到得分
        int rewardPoints =0;
        switch (signDays){
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }
        //保存积分明细记录
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userid,rewardPoints +1)
        );
        //封装返回
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signDays);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }

    private int countSignDays(String key, int len) {
        //获取本月从第一天开始到今天为止的所有签到记录
        List<Long> result = redisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create().get(
                        BitFieldSubCommands.BitFieldType.unsigned(len)).valueAt(0));
        if (CollUtils.isEmpty(result)) {
            return 0;
        }
        int count =0;
        int num = result.get(0).intValue();
        //循环 ， 与1 做与运算 得到最后一个bit位 判断是否是零 为零终止
        while ((num & 1) == 1) {
            count++;
            //   右移一位
            num>>>=1;
        }
        return count;
    }


































}
