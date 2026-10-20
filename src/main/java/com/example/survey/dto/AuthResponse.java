package com.example.survey.dto;

/** 登录/注册成功响应 */
public record AuthResponse(String token, String userId, String username) {
}
