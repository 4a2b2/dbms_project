package com.example.survey.model.survey;

import lombok.Data;

/** 一条跳转逻辑规则：when 条件命中时执行 then 动作 */
@Data
public class LogicRule {

    /** 规则在问卷内的唯一标识 */
    private String id;

    private Condition when;

    private Action then;
}
