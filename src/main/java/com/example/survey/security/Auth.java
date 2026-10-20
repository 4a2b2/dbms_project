package com.example.survey.security;

import com.example.survey.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;

/** 在 Controller/Service 中读取当前登录用户的静态工具 */
public final class Auth {

    private Auth() {
    }

    /** 当前用户 id，未登录返回 null */
    public static String uid(HttpServletRequest request) {
        Object uid = request.getAttribute("uid");
        return uid == null ? null : uid.toString();
    }

    /** 要求必须登录，未登录抛 401 */
    public static String requireUid(HttpServletRequest request) {
        String uid = uid(request);
        if (uid == null) {
            throw ApiException.unauthorized("请先登录");
        }
        return uid;
    }
}
