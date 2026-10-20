package com.example.survey.dto;

import com.example.survey.model.response.SurveyResponse;

/** 答卷进度（草稿续填用） */
public record ResponseStateView(String responseId, String currentQid, String status) {

    public static ResponseStateView from(SurveyResponse r) {
        return new ResponseStateView(r.getId(), r.getCurrentQid(), r.getStatus().name());
    }
}
