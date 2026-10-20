package com.example.survey.core.question;

import com.example.survey.core.Values;
import com.example.survey.exception.ApiException;
import com.example.survey.model.response.Answer;
import com.example.survey.model.survey.Option;
import com.example.survey.model.survey.Question;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多选题。
 * 约束参数：minSelect / maxSelect（0 <= min <= max <= 选项数；min == max == N 表示必须选 N 项）。
 */
@Component
public class MultipleChoiceHandler extends AbstractQuestionTypeHandler {

    @Override
    public String type() {
        return QuestionTypes.MULTIPLE;
    }

    @Override
    public void validateQuestion(Question question) {
        int optionCount = question.getOptions() == null ? 0 : question.getOptions().size();
        if (optionCount < 2) {
            throw ApiException.badRequest("多选题「" + question.getTitle() + "」至少需要 2 个选项");
        }
        Integer min = Values.getInt(question.getConstraints(), "minSelect");
        Integer max = Values.getInt(question.getConstraints(), "maxSelect");
        if (min != null && min < 0) {
            throw ApiException.badRequest("多选题「" + question.getTitle() + "」minSelect 不能小于 0");
        }
        if (max != null && max > optionCount) {
            throw ApiException.badRequest("多选题「" + question.getTitle() + "」maxSelect 不能超过选项数");
        }
        if (min != null && max != null && min > max) {
            throw ApiException.badRequest("多选题「" + question.getTitle() + "」minSelect 不能大于 maxSelect");
        }
    }

    @Override
    public void validateAnswer(Question question, Answer answer) {
        if (unanswered(question, answer)) {
            return;
        }
        List<String> ids = answer.getOptionIds();
        if (ids == null || ids.isEmpty()) {
            throw ApiException.badRequest("多选题「" + question.getTitle() + "」至少选择 1 项");
        }
        Set<String> distinct = new HashSet<>(ids);
        if (distinct.size() != ids.size()) {
            throw ApiException.badRequest("多选题「" + question.getTitle() + "」选项重复");
        }
        for (String oid : ids) {
            if (question.optionByOid(oid) == null) {
                throw ApiException.badRequest("多选题「" + question.getTitle() + "」包含不存在的选项");
            }
        }
        Integer min = Values.getInt(question.getConstraints(), "minSelect");
        Integer max = Values.getInt(question.getConstraints(), "maxSelect");
        if (min != null && ids.size() < min) {
            throw ApiException.badRequest("多选题「" + question.getTitle() + "」至少选择 " + min + " 项");
        }
        if (max != null && ids.size() > max) {
            throw ApiException.badRequest("多选题「" + question.getTitle() + "」最多选择 " + max + " 项");
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
            total++;
            for (String oid : a.getOptionIds()) {
                if (counts.containsKey(oid)) {
                    counts.merge(oid, 1, Integer::sum);
                }
            }
        }
        List<Map<String, Object>> options = new ArrayList<>();
        for (Option o : question.getOptions()) {
            options.add(Map.of("oid", o.getOid(), "text", o.getText(), "count", counts.get(o.getOid())));
        }
        return Map.of("total", total, "options", options);
    }
}
