package com.example.survey.dto;

/** 恢复旧版本请求：以指定版本的内容创建一个最新版本（历史链不回滚） */
public record RestoreRequest(String versionId) {
}
