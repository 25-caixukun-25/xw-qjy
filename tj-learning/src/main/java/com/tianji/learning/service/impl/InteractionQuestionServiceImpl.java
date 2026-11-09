package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.controller.InteractionQuestionAdminController;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import nonapi.io.github.classgraph.utils.LogNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.invoke.LambdaMetafactory;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.learning.enums.QuestionStatus.CHECKED;
import static com.tianji.learning.enums.QuestionStatus.UN_CHECK;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-05-11
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {
    private final InteractionReplyMapper replyService;
    private final UserClient userClient;
    private final CourseClient courseClient;
    private final SearchClient searchClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;


    @Override
    //管理端根据id查询问题详情
    public QuestionAdminVO selectById(Long id) {
        InteractionQuestion question = getById(id);
        if (question == null) {
            throw new RuntimeException("该问题不存在");
        }
        QuestionAdminVO vo = BeanUtils.toBean(question, QuestionAdminVO.class);
        //将 课程id 章节id 转换为对应的名称
        Long courseId = question.getCourseId();
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(courseId, false, true);
        if(cInfo != null){
            vo.setCourseName(cInfo.getName());
            vo.setCategoryName(categoryCache.getCategoryNames(cInfo.getCategoryIds()));
            //处理教师名称
            List<Long> teacherIds = cInfo.getTeacherIds();
            List<UserDTO> teachers = userClient.queryUserByIds(teacherIds);
            if(CollUtils.isNotEmpty(teachers)){
                vo.setTeacherName(teachers.stream()
                        .map(UserDTO::getName).collect(Collectors.joining("/")));
            }
        }
        Long chapterId = question.getChapterId();
        Long sectionId = question.getSectionId();
        List<Long> list = new ArrayList<>();
        list.add(chapterId);
        list.add(sectionId);
        List<CataSimpleInfoDTO> cataList = catalogueClient.batchQueryCatalogue(list);
        if (cataList != null) {
            Map<Long, String> cataMap = cataList.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
            vo.setChapterName(cataMap.getOrDefault(chapterId, ""));
            vo.setSectionName(cataMap.getOrDefault(sectionId, ""));
        }
        //将用户id转换为对应的头像及昵称
        UserDTO user = userClient.queryUserById(question.getUserId());
        if(user != null){
            vo.setUserName(user.getName());
            vo.setUserIcon(user.getIcon());
        }
        if(question.getStatus() == UN_CHECK){
            vo.setStatus(CHECKED);
        }
        return vo;
    }

    @Override
    public void updateHidden(Long id, Boolean hidden) {
        InteractionQuestion question =  new InteractionQuestion();
        question.setId(id);
        question.setHidden(hidden);
        updateById(question);
    }

    @Override
    //管理端分页查询互动问题
    public PageDTO<QuestionAdminVO> queryQuestionAdminPage(QuestionAdminPageQuery query) {
        //处理课程名称 得到课程id
        List<Long> ids = null;
        if(StringUtils.isNotBlank(query.getCourseName())){
           ids = searchClient.queryCoursesIdByName(query.getCourseName());
           if(CollUtils.isEmpty(ids)){
               return PageDTO.empty(0L,0L);
           }
        }
        LocalDateTime begin = query.getBeginTime();
        LocalDateTime end = query.getEndTime();
        Integer status = query.getStatus();
        //分页查询
        Page<InteractionQuestion> page = lambdaQuery()
                .in(ids != null, InteractionQuestion::getCourseId, ids)
                .gt(begin != null, InteractionQuestion::getCreateTime, begin)
                .lt(end != null, InteractionQuestion::getCreateTime, end)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        // 3.准备VO需要的数据：用户数据、课程数据、章节数据
        Set<Long> userIds = new HashSet<>();
        Set<Long> cIds = new HashSet<>();
        Set<Long> cataIds = new HashSet<>();
        // 3.1.获取各种数据的id集合
        for (InteractionQuestion q : records) {
            userIds.add(q.getUserId());
            cIds.add(q.getCourseId());
            cataIds.add(q.getChapterId());
            cataIds.add(q.getSectionId());
        }
        // 3.2.根据id查询用户
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        Map<Long,UserDTO> userMap = new HashMap<>(users.size());
        if(CollUtils.isNotEmpty(users)){
            userMap =users.stream().collect(Collectors.toMap(UserDTO::getId,u->u));
        }
        // 3.3.根据id查询课程
        List<CourseSimpleInfoDTO> cInfos = courseClient.getSimpleInfoList(cIds);
        Map<Long, CourseSimpleInfoDTO> cInfoMap = new HashMap<>(cInfos.size());
        if (CollUtils.isNotEmpty(cInfos)) {
            cInfoMap = cInfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        }

        // 3.4.根据id查询章节
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(cataIds);
        Map<Long, String> cataMap = new HashMap<>(catas.size());
        if (CollUtils.isNotEmpty(catas)) {
            cataMap = catas.stream()
                    .collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        }
        // 4.封装VO
        List<QuestionAdminVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion q : records) {
            QuestionAdminVO vo = BeanUtils.toBean(q, QuestionAdminVO.class);
            voList.add(vo);
            UserDTO user = userMap.get(q.getUserId());
            if(user != null){
                vo.setUserName(user.getName());
            }
            CourseSimpleInfoDTO cInfo = cInfoMap.get(q.getCourseId());
            if(cInfo!=null){
                vo.setCourseName(cInfo.getName());
                vo.setCategoryName(categoryCache.getCategoryNames(cInfo.getCategoryIds()));
            }
            //章节信息
            vo.setChapterName(cataMap.getOrDefault(q.getChapterId(), ""));
            vo.setSectionName(cataMap.getOrDefault(q.getSectionId(), ""));
        }
        return PageDTO.of(page ,voList);

    }

    @Override
    @Transactional
    //删除问题及下面回复
    public void deleteQuestionById(Long id) {
        Long userid = UserContext.getUser();
        // - 查询问题是否存在
        InteractionQuestion question = getById(id);
        if (question == null) {
            throw new RuntimeException("问题不存在！");
        }
        //   - 判断是否是当前用户提问的 如果不是则报错
        if(!Objects.equals(question.getUserId(), userid)){
            throw new RuntimeException("当前问题不是你发表的");
        }
        // - 如果是则删除问题
        removeById(id);
        //  - 然后删除问题下的回答及评论
        replyService.delete(
                new QueryWrapper<InteractionReply>().lambda().eq(InteractionReply::getQuestionId, id)
        );
    }

    @Override
    //根据id查询问题详情
    public QuestionVO queryQuestionById(Long id) {
        InteractionQuestion question = getById(id);
        if (question == null || question.getHidden()) {
            //非法的数据
            return null;
        }
        //获取用户的信息
        UserDTO user = null;
        if (!question.getAnonymity()) {
            user = userClient.queryUserById(question.getUserId());
        }
        QuestionVO vo = BeanUtils.toBean(question, QuestionVO.class);
        if (user != null) {
            vo.setUserName(user.getName());
            vo.setUserIcon(user.getIcon());
        }

        return vo;
    }

    @Override
    //修改互动问题
    public void updateQuestion(QuestionFormDTO questionFormDTO, Long id) {
        InteractionQuestion question = getById(id);
        if (question == null) {
            throw new RuntimeException("问题不存在！");
        }
        question.setTitle(questionFormDTO.getTitle());
        question.setDescription(question.getDescription());
        updateById(question);
    }

    @Override
    //分页查询互动问题
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        Long userid = UserContext.getUser();
        //校验数据，课程id和小节id不能都为空
        Long courseId = query.getCourseId();
        Long sectionId = query.getSectionId();
        if (courseId == null && sectionId == null) {
            throw new BadRequestException("课程id和小节id不能都为空");
        }
        // 2.分页查询
        Page<InteractionQuestion> page = lambdaQuery()
                .select(InteractionQuestion.class, info -> !info.getProperty().equals("description"))
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, UserContext.getUser())
                .eq(courseId != null, InteractionQuestion::getCourseId, courseId)
                .eq(sectionId != null, InteractionQuestion::getSectionId, sectionId)
                .eq(InteractionQuestion::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3.根据id查询提问者和最近一次回答的信息
        Set<Long> userIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        // 3.1.得到问题当中的提问者id和最近一次回答的id
        for (InteractionQuestion q : records) {
            if(!q.getAnonymity()) { // 只查询非匿名的问题
                userIds.add(q.getUserId());
            }
            answerIds.add(q.getLatestAnswerId());
        }
        // 3.2.根据id查询最近一次回答
        answerIds.remove(null);
        Map<Long, InteractionReply> replyMap = new HashMap<>(answerIds.size());
        if(CollUtils.isNotEmpty(answerIds)) {
            List<InteractionReply> replies = replyService.selectBatchIds(answerIds);
            for (InteractionReply reply : replies) {
                replyMap.put(reply.getId(), reply);
                if(!reply.getAnonymity()){ // 匿名用户不做查询
                    userIds.add(reply.getUserId());
                }
            }
        }

        // 3.3.根据id查询用户信息（提问者）
        userIds.remove(null);
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if(CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream()
                    .collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        // 4.封装VO
        List<QuestionVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion r : records) {
            // 4.1.将PO转为VO
            QuestionVO vo = BeanUtils.copyBean(r, QuestionVO.class);
            vo.setUserId(null);
            voList.add(vo);
            // 4.2.封装提问者信息
            if(!r.getAnonymity()){
                UserDTO userDTO = userMap.get(r.getUserId());
                if (userDTO != null) {
                    vo.setUserId(userDTO.getId());
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }

            // 4.3.封装最近一次回答的信息
            InteractionReply reply = replyMap.get(r.getLatestAnswerId());
            if (reply != null) {
                vo.setLatestReplyContent(reply.getContent());
                if(!reply.getAnonymity()){// 匿名用户直接忽略
                    UserDTO user = userMap.get(reply.getUserId());
                    vo.setLatestReplyUser(user.getName());
                }

            }
        }

        return PageDTO.of(page, voList);
    }

    @Override
    //新增互动问题
    public void saveQuestion(QuestionFormDTO questionFormDTO) {
        //获取当前登录用户
        Long userId = UserContext.getUser();
        //封装vo
        InteractionQuestion interactionQuestion = BeanUtils.copyBean(questionFormDTO, InteractionQuestion.class);
        interactionQuestion.setUserId(userId);
        save(interactionQuestion);
    }

}
