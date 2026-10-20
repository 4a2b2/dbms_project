package com.example.survey.dto;

/** 保存单题答案响应：nextQid 为 null 表示已到问卷末尾 */
public record SaveAnswerResponse(String nextQid, boolean done) {
}
