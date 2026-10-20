package com.example.survey.dto;

import com.example.survey.model.survey.LogicRule;
import com.example.survey.model.survey.Question;
import com.example.survey.model.survey.Settings;

import java.util.List;

/**
 * 更新问卷请求。字段为 null 表示不修改。
 * 草稿状态可更新全部字段；已发布仅可更新标题、说明、设置。
 */
public record UpdateSurveyRequest(
        String title,
        String description,
        Settings settings,
        List<Question> questions,
        List<LogicRule> logic) {
}
