package com.example.survey.dto;

import com.example.survey.model.survey.Option;

import java.util.List;
import java.util.Map;

/**
 * 题库题目创建/更新请求。
 * 更新时：题目内容有变化会创建新版本（旧版本不可变）；tags 为 null 表示不修改标签；
 * note 为本次修改说明（记入新版本）。
 */
public record ItemUpsertRequest(
        String type,
        String title,
        Boolean required,
        List<Option> options,
        Map<String, Object> constraints,
        List<String> tags,
        String note) {
}
