package com.example.survey.core.validation;

import com.example.survey.core.question.QuestionTypeRegistry;
import com.example.survey.exception.ApiException;
import com.example.survey.model.response.Answer;
import com.example.survey.model.survey.Question;
import com.example.survey.model.survey.Survey;
import org.springframework.stereotype.Component;

/** 答案校验入口：按题型分发给对应处理器 */
@Component
public class AnswerValidator {

    private final QuestionTypeRegistry registry;

    public AnswerValidator(QuestionTypeRegistry registry) {
        this.registry = registry;
    }

    /** 校验 qid 存在且答案合法，不合法抛 400 */
    public Question validate(Survey survey, String qid, Answer answer) {
        Question question = survey.questionByQid(qid);
        if (question == null) {
            throw ApiException.badRequest("题目不存在：" + qid);
        }
        registry.get(question.getType()).validateAnswer(question, answer);
        return question;
    }
}
