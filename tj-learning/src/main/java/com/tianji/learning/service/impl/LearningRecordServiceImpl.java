package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.api.dto.leanring.LearningRecordFormDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-04-01
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {
    private final LearningLessonServiceImpl lessonService;
    private final CourseClient courseClient;
    private final LearningRecordDelayTaskHandler taskHandler;
    private final RabbitMqHelper mqHelper;
    //提交学习记录
    @Override
    @Transactional
    public void addLearningRecord(LearningRecordFormDTO recordDTO) {
        //获取登录用户
        Long userid = UserContext.getUser();
        //处理学习记录
        boolean finished = false;
        if (recordDTO.getSectionType() == SectionType.VIDEO.getValue()) {
            //处理视频
            finished = handleVideoRecord(userid,recordDTO);
        }else {
            //处理考试
            finished = handleExamRecord(userid,recordDTO);
        }
        if (!finished) {
            //没有新学完的小节 不需要更新课表中的学习进度
            return;
        }
        //处理课表数据
        handleLearNingLessonsChange(recordDTO);
        //发送mq通知积分添加
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.LEARN_SECTION,
                userid
        );

    }

    private void handleLearNingLessonsChange(LearningRecordFormDTO recordDTO) {
        //查询课表
        LearningLesson lesson = lessonService.getById(recordDTO.getLessonId());
        if (lesson == null) {
            throw new BizIllegalException("课程不存在，无法更新数据");
        }
        boolean allLearned = false;
            //新完成的小节，查询课程数据
            CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
            if (cInfo == null) {
                throw new BizIllegalException("课程不存在，无法更新数据!");
            }
            //比较课程是否全部学完 以学习小节 >= 总小节
             allLearned = lesson.getLearnedSections() + 1 >= cInfo.getSectionNum();

        // 5.更新课表
        lessonService.lambdaUpdate()
                .set(lesson.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .set(allLearned, LearningLesson::getStatus, LessonStatus.FINISHED.getValue())
                .setSql("learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();


    }

    private boolean handleExamRecord(Long userid, LearningRecordFormDTO recordDTO) {
        //转换dto为po
        LearningRecord record = BeanUtils.copyBean(recordDTO, LearningRecord.class);
        //填充数据
        record.setUserId(userid);
        record.setFinished(true);
        record.setFinishTime(recordDTO.getCommitTime());
        boolean success = save(record);
        if (!success) {
            throw new DbException("新增考试记录失败");
        }
        return true;
    }

    private boolean handleVideoRecord(Long userid, LearningRecordFormDTO recordDTO) {
       //查询旧的学习记录
        LearningRecord old = queryOldRecord(recordDTO.getLessonId(),recordDTO.getSectionId());
        if (old == null) {
            //不存在，新增
            //转换dto为po
            LearningRecord record = BeanUtils.copyBean(recordDTO, LearningRecord.class);
            //填充数据
            record.setUserId(userid);
            boolean success = save(record);
            if (!success) {
                throw new DbException("新增考试记录失败");
            }
            return false;
        }
        //存在，更新
         //判断是否是第一次完成
        boolean finished = recordDTO.getMoment() *2 >=recordDTO.getDuration() && !old.getFinished();
        if (!finished) {
            LearningRecord record = new LearningRecord();
            record.setLessonId(recordDTO.getLessonId());
            record.setSectionId(recordDTO.getSectionId());
            record.setMoment(recordDTO.getMoment());
            record.setId(old.getId());
            record.setFinished(old.getFinished());
            taskHandler.addLearningRecordTask(record);
            return false;
        }
        //更新数据

        boolean success = lambdaUpdate()
                .set(LearningRecord::getMoment, recordDTO.getMoment())
                .set(LearningRecord::getFinished, true)
                .set(LearningRecord::getFinishTime, recordDTO.getCommitTime())
                .eq(LearningRecord::getId, old.getId())
                .update();
        if (!success) {
            throw new DbException("更新视频数据失败");
        }
        //清理缓存
        taskHandler.cleanRecordCaChe(recordDTO.getLessonId(),recordDTO.getSectionId());
        return true;
    }

    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        //查询缓存
        LearningRecord record = taskHandler.readRecordCache(lessonId, sectionId);
        if (record != null) {
            //缓存命中直接返回
            return record;
        }
        //未命中查询数据库并且写入redis
        record = lambdaQuery()
                .eq(LearningRecord::getLessonId,lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();

            taskHandler.writeRecordCache(record);
            return record;

    }

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        Long userid = UserContext.getUser();
        //查询课表
        LearningLesson lesson = lessonService.queryByUserIdAndCourseId(userid,courseId);
        if (lesson == null) {
            return null;
        }
        //查询学习记录
        List<LearningRecord> records = lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId())
                .list();
        //转化为dto
        LearningLessonDTO dto = new LearningLessonDTO();
        List<LearningRecordDTO> list = BeanUtils.copyToList(records, LearningRecordDTO.class);
        dto.setRecords(list);
        dto.setLatestSectionId(lesson.getLatestSectionId());
        dto.setId(lesson.getId());
        return dto;
    }






























}
