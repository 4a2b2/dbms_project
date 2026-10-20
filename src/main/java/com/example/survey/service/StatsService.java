package com.example.survey.service;

import com.example.survey.core.question.QuestionTypeRegistry;
import com.example.survey.model.response.Answer;
import com.example.survey.model.response.ResponseStatus;
import com.example.survey.model.response.SurveyResponse;
import com.example.survey.model.survey.Question;
import com.example.survey.model.survey.Survey;
import com.example.survey.repository.SurveyResponseRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 问卷统计：只对已提交答卷做聚合 */
@Service
public class StatsService {

    private final SurveyResponseRepository responseRepository;
    private final QuestionTypeRegistry typeRegistry;

    public StatsService(SurveyResponseRepository responseRepository, QuestionTypeRegistry typeRegistry) {
        this.responseRepository = responseRepository;
        this.typeRegistry = typeRegistry;
    }

    public Map<String, Object> stats(Survey survey) {
        List<SurveyResponse> submitted = responseRepository.findBySurveyIdAndStatus(
                survey.getId(), ResponseStatus.SUBMITTED);

        List<Map<String, Object>> questionStats = new ArrayList<>();
        for (Question q : survey.getQuestions()) {
            List<Answer> answers = submitted.stream()
                    .map(r -> r.answerByQid(q.getQid()))
                    .filter(Objects::nonNull)
                    .toList();
            Map<String, Object> summary = typeRegistry.get(q.getType()).summarize(q, answers);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("qid", q.getQid());
            item.put("title", q.getTitle());
            item.put("type", q.getType());
            item.put("summary", summary);
            questionStats.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("surveyId", survey.getId());
        result.put("title", survey.getTitle());
        result.put("responseCount", submitted.size());
        result.put("questions", questionStats);
        return result;
    }
}
