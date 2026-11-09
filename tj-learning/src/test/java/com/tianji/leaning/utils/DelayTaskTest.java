package com.tianji.leaning.utils;

import com.tianji.learning.utils.DelayTask;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.DelayQueue;


@Slf4j
public class DelayTaskTest {

    @Test
    void testDelayQueue() throws InterruptedException {
        DelayQueue<DelayTask<String>> delayed = new DelayQueue<>();
        log.info("开始执行任务");
        delayed.add(new DelayTask<>("演示任务一", Duration.ofSeconds(1)));
        delayed.add(new DelayTask<>("演示任务2", Duration.ofSeconds(2)));
        delayed.add(new DelayTask<>("演示任务3", Duration.ofSeconds(3)));
        while (true) {
            DelayTask<String> take = delayed.take();
            String data = take.getData();
            log.info("开始执行任务{}",data);

        }
    }

}
