package com.tianji.learning.service;

import com.tianji.learning.domain.vo.SignResultVO;

/**
 * <p>
 * 学霸天梯榜 服务类
 * </p>
 *
 * @author author
 * @since 2026-05-25
 */

public interface ISignRecordService{

    SignResultVO addSignRecords();

    int[] getSignRecord();
}
