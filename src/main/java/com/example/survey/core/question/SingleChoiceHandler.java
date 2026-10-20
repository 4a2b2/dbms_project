package com.example.survey.core.question;

import com.example.survey.exception.ApiException;
import com.example.survey.model.response.Answer;
import com.example.survey.model.survey.Option;
import com.example.survey.model.survey.Question;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 单选题 */
@Component
public class SingleChoiceHandler extends AbstractQuestionTypeHandler {

    @Override
    public String type() {
        return QuestionTypes.SINGLE;
    }

    @Override
    public void validateQuestion(Question question) {
        if (question.getOptions() == null || question.getOptions().size() < 2) {
            throw ApiException.badRequest("单选题「" + question.getTitle() + "」至少需要 2 个选项");
        }
    }

    @Override
    public void validateAnswer(Question question, Answer answer) {
        if (unanswered(question, answer)) {
            return;
        }
        List<String> ids = answer.getOptionIds();
        if (ids == null || ids.size() != 1) {
            throw ApiException.badRequest("单选题「" + question.getTitle() + "」必须且只能选 1 项");
        }
        if (question.optionByOid(ids.get(0)) == null) {
            throw ApiException.badRequest("单选题「" + question.getTitle() + "」包含不存在的选项");
        }
    }

    @Override
    public Map<String, Object> summarize(Question question, List<Answer> answers) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Option o : question.getOptions()) {
            counts.put(o.getOid(), 0);
        }
        int total = 0;
        for (Answer a : answers) {
            if (a.getOptionIds() == null || a.getOptionIds().isEmpty()) {
                continue;
            }
            String oid = a.getOptionIds().get(0);
            if (counts.containsKey(oid)) {
                counts.merge(oid, 1, Integer::sum);
                total++;
            }
        }
        List<Map<String, Object>> options = new ArrayList<>();
        for (Option o : question.getOptions()) {
            options.add(Map.of("oid", o.getOid(), "text", o.getText(), "count", counts.get(o.getOid())));
        }
        return Map.of("total", total, "options", options);
    }
}
