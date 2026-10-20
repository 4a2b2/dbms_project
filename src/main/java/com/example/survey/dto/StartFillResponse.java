package com.example.survey.dto;

/** 开始/继续填写响应 */
public record StartFillResponse(String responseId, String currentQid, boolean resumed) {
}
