package com.example.survey.core.question;

import com.example.survey.core.Values;
import com.example.survey.exception.ApiException;
import com.example.survey.model.response.Answer;
import com.example.survey.model.survey.Question;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 数字题。
 * 约束参数：min / max / integerOnly（仅允许整数）。
 */
@Component
public class NumberQuestionHandler extends AbstractQuestionTypeHandler {

    @Override
    public String type() {
        return QuestionTypes.NUMBER;
    }

    @Override
    public void validateQuestion(Question question) {
        Double min = Values.getDouble(question.getConstraints(), "min");
        Double max = Values.getDouble(question.getConstraints(), "max");
        if (min != null && max != null && min > max) {
            throw ApiException.badRequest("数字题「" + question.getTitle() + "」min 不能大于 max");
        }
    }

    @Override
    public void validateAnswer(Question question, Answer answer) {
        if (unanswered(question, answer)) {
            return;
        }
        Double number = answer.getNumber();
        if (number == null || !Double.isFinite(number)) {
            throw ApiException.badRequest("数字题「" + question.getTitle() + "」必须是有效数字");
        }
        Boolean integerOnly = Values.getBool(question.getConstraints(), "integerOnly");
        if (Boolean.TRUE.equals(integerOnly) && number % 1 != 0) {
            throw ApiException.badRequest("数字题「" + question.getTitle() + "」只接受整数");
        }
        Double min = Values.getDouble(question.getConstraints(), "min");
        Double max = Values.getDouble(question.getConstraints(), "max");
        if (min != null && number < min) {
            throw ApiException.badRequest("数字题「" + question.getTitle() + "」不能小于 " + min);
        }
        if (max != null && number > max) {
            throw ApiException.badRequest("数字题「" + question.getTitle() + "」不能大于 " + max);
        }
    }

    @Override
    public Map<String, Object> summarize(Question question, java.util.List<Answer> answers) {
        int total = 0;
        double sum = 0;
        Double min = null;
        Double max = null;
        for (Answer a : answers) {
            if (a.getNumber() == null) {
                continue;
            }
            double v = a.getNumber();
            total++;
            sum += v;
            min = min == null ? v : Math.min(min, v);
            max = max == null ? v : Math.max(max, v);
        }
        return Map.of(
                "total", total,
                "average", total == 0 ? 0 : sum / total,
                "min", min == null ? 0 : min,
                "max", max == null ? 0 : max);
    }
}
