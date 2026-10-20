package com.example.survey.dto;

import com.example.survey.model.survey.Question;
import com.example.survey.model.survey.Survey;

import java.util.List;

/** 填写者看到的问卷公开信息 */
public record PublicSurveyView(
        String shareCode,
        String title,
        String description,
        boolean allowAnonymous,
        List<Question> questions) {

    public static PublicSurveyView from(Survey s) {
        return new PublicSurveyView(
                s.getShareCode(),
                s.getTitle(),
                s.getDescription(),
                s.getSettings().isAllowAnonymous(),
                s.getQuestions());
    }
}
