package com.tianji.learning.utils;

import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;

@Slf4j
@RequiredArgsConstructor
@Component
public class LearningRecordDelayTaskHandler {

    private final StringRedisTemplate redisTemplate;
    private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();
    private static final String RECORD_KEY_TEMPLATE = "learning:record:{}";
    private final LearningRecordMapper recordMapper;
    private final ILearningLessonService lessonService;
    private static volatile Boolean begin = true;

    @PostConstruct
    public void init() {
        CompletableFuture.runAsync(this::handleDelayTask);
    }

    @PreDestroy
    public void destroy() {
        begin = false;
    }

    public void handleDelayTask() {
        while (begin) {
            try {
                //获取到期的延迟任务
                DelayTask<RecordTaskData> task = queue.take();
                RecordTaskData data = task.getData();
                //查询redis缓存
                LearningRecord record = readRecordCache(data.getLessonId(), data.getSectionId());
                if (record == null) {
                    continue;
                }
                if (!Objects.equals(data.getMoment(), record.getMoment())) {
                    //不一致说明用户还在提交学习进度
                    continue;
                }
                //持久化更新数据库
                //更新学习记录的moment
                record.setFinished(null);
                recordMapper.updateById(record);
                //更新课表
                LearningLesson lesson = new LearningLesson();
                lesson.setId(data.getLessonId());
                lesson.setLatestSectionId(data.getSectionId());
                lesson.setLatestLearnTime(LocalDateTime.now());
                lessonService.updateById(lesson);
            } catch (InterruptedException e) {
                log.error("处理延时任务异常", e);
            }
        }
    }


    public void addLearningRecordTask(LearningRecord record) {
        //添加数据到redis缓存
        writeRecordCache(record);
        //提交延迟任务到延时队列DelayQueQue
        queue.add(new DelayTask<>(new RecordTaskData(record), Duration.ofSeconds(20)));
    }

    public void writeRecordCache(LearningRecord record) {
        log.info("开始更新学习记录数据");
        try {
            //数据转换
            String json = JsonUtils.toJsonStr(new RecordCacheData(record));
            //写入redis
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, record.getLessonId());
            redisTemplate.opsForHash().put(key, record.getSectionId().toString(), json);
            //设置过期时间
            redisTemplate.expire(key, Duration.ofMillis(1));
        } catch (Exception e) {
            log.error("更新学习视频数据失败", e);
        }

    }

    public LearningRecord readRecordCache(Long lessonId, Long sectionId) {
        try {
            //获取缓存
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
            Object cacheData = redisTemplate.opsForHash().get(key, sectionId.toString());
            if (cacheData == null) {
                return null;
            }
            return JsonUtils.toBean(cacheData.toString(), LearningRecord.class);
        } catch (Exception e) {
            log.error("获取缓存失败", e);
            return null;
        }
    }

    public void cleanRecordCaChe(Long lessonId, Long sectionId) {
        String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
        redisTemplate.opsForHash().delete(key, sectionId.toString());
    }


    @Data
    @NoArgsConstructor
    private static class RecordCacheData {
        private Long id;
        private Integer moment;
        private Boolean finished;

        public RecordCacheData(LearningRecord record) {
            this.id = record.getId();
            this.moment = record.getMoment();
            this.finished = record.getFinished();
        }
    }

    @Data
    @NoArgsConstructor
    private static class RecordTaskData {
        private Long lessonId;
        private Integer moment;
        private Long sectionId;

        public RecordTaskData(LearningRecord record) {
            this.lessonId = record.getLessonId();
            this.moment = record.getMoment();
            this.sectionId = record.getSectionId();
        }
    }

}
