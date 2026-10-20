package com.example.survey.core.question;

import com.example.survey.exception.ApiException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 题型注册表：收集所有 QuestionTypeHandler 实现（Spring 自动注入 List），
 * 新题型只要实现接口并声明为组件即可自动注册，无需改动这里。
 */
@Component
public class QuestionTypeRegistry {

    private final Map<String, QuestionTypeHandler> handlers = new HashMap<>();

    public QuestionTypeRegistry(List<QuestionTypeHandler> handlerList) {
        for (QuestionTypeHandler h : handlerList) {
            handlers.put(h.type(), h);
        }
    }

    /** 按题型取处理器，未知题型抛 400 */
    public QuestionTypeHandler get(String type) {
        QuestionTypeHandler h = handlers.get(type);
        if (h == null) {
            throw ApiException.badRequest("未知题型：" + type);
        }
        return h;
    }

    public boolean exists(String type) {
        return handlers.containsKey(type);
    }
}
