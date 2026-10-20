package com.example.survey.core.question;

import com.example.survey.core.Values;
import com.example.survey.exception.ApiException;
import com.example.survey.model.response.Answer;
import com.example.survey.model.survey.Question;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文本题。
 * 约束参数：minLength / maxLength。
 */
@Component
public class TextQuestionHandler extends AbstractQuestionTypeHandler {

    @Override
    public String type() {
        return QuestionTypes.TEXT;
    }

    @Override
    public void validateQuestion(Question question) {
        Integer min = Values.getInt(question.getConstraints(), "minLength");
        Integer max = Values.getInt(question.getConstraints(), "maxLength");
        if (min != null && min < 0) {
            throw ApiException.badRequest("文本题「" + question.getTitle() + "」minLength 不能小于 0");
        }
        if (max != null && max < 1) {
            throw ApiException.badRequest("文本题「" + question.getTitle() + "」maxLength 不能小于 1");
        }
        if (min != null && max != null && min > max) {
            throw ApiException.badRequest("文本题「" + question.getTitle() + "」minLength 不能大于 maxLength");
        }
    }

    @Override
    public void validateAnswer(Question question, Answer answer) {
        if (unanswered(question, answer)) {
            return;
        }
        String text = answer.getText();
        if (text == null || text.isBlank()) {
            throw ApiException.badRequest("文本题「" + question.getTitle() + "」内容不能为空");
        }
        int len = text.trim().length();
        Integer min = Values.getInt(question.getConstraints(), "minLength");
        Integer max = Values.getInt(question.getConstraints(), "maxLength");
        if (min != null && len < min) {
            throw ApiException.badRequest("文本题「" + question.getTitle() + "」至少输入 " + min + " 个字符");
        }
        if (max != null && len > max) {
            throw ApiException.badRequest("文本题「" + question.getTitle() + "」最多输入 " + max + " 个字符");
        }
    }

    @Override
    public Map<String, Object> summarize(Question question, List<Answer> answers) {
        List<String> texts = new ArrayList<>();
        for (Answer a : answers) {
            if (a.getText() != null && !a.getText().isBlank()) {
                texts.add(a.getText());
            }
        }
        return Map.of("total", texts.size(), "texts", texts);
    }
}
