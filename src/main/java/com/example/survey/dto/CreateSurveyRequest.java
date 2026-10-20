package com.example.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建问卷请求 */
public record CreateSurveyRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题最长 200 字")
        String title,

        @Size(max = 2000, message = "说明最长 2000 字")
        String description) {
}
