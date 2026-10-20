package com.example.survey.model.survey;

import lombok.Data;

import java.time.Instant;

/** 问卷设置 */
@Data
public class Settings {

    /** 是否允许匿名填写 */
    private boolean allowAnonymous = true;

    /** 是否允许同一用户多次提交 */
    private boolean allowMultiple = true;

    /** 截止时间，null 表示不限 */
    private Instant deadline;
}
