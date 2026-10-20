package com.example.survey.core.question;

import com.example.survey.exception.ApiException;
import com.example.survey.model.response.Answer;
import com.example.survey.model.survey.Question;

/** 公共辅助：判断空答案、required 检查 */
public abstract class AbstractQuestionTypeHandler implements QuestionTypeHandler {

    /**
     * 三个值字段都为空视为未作答。
     * 返回 true 表示未作答（required 时直接抛错）；返回 false 表示已作答，实现类继续校验内容。
     */
    protected boolean unanswered(Question question, Answer answer) {
        boolean empty = answer == null
                || ((answer.getOptionIds() == null || answer.getOptionIds().isEmpty())
                && (answer.getText() == null || answer.getText().isBlank())
                && answer.getNumber() == null);
        if (empty && question.isRequired()) {
            throw ApiException.badRequest("题目「" + question.getTitle() + "」为必答题");
        }
        return empty;
    }
}
