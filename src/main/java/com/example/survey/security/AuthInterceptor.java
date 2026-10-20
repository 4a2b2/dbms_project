package com.example.survey.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 认证拦截器：解析 Authorization: Bearer <token>。
 * 有 token 才解析，解析成功把 uid/username 放进 request attribute；
 * token 无效直接 401 JSON；不带 token 的请求放行（由具体接口按需要求登录）。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public AuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIX)) {
            return true;
        }
        Claims claims = jwtService.parse(header.substring(PREFIX.length()));
        if (claims == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write("{\"error\":\"登录状态无效或已过期\"}".getBytes(StandardCharsets.UTF_8));
            return false;
        }
        request.setAttribute("uid", claims.getSubject());
        request.setAttribute("username", claims.get("username", String.class));
        return true;
    }
}
