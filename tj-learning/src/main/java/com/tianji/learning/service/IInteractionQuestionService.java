package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 服务类
 * </p>
 *
 * @author author
 * @since 2026-05-11
 */
public interface IInteractionQuestionService extends IService<InteractionQuestion> {

    void saveQuestion(@Valid QuestionFormDTO questionFormDTO);

    PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query);

    void updateQuestion(@Valid QuestionFormDTO questionFormDTO, Long id);

    QuestionVO queryQuestionById(Long id);

    void deleteQuestionById(Long id);

    PageDTO<QuestionAdminVO> queryQuestionAdminPage(QuestionAdminPageQuery query);

    void updateHidden(Long id, Boolean hidden);

    QuestionAdminVO selectById(Long id);
}
