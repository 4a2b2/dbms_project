package com.example.survey.model.survey;

import lombok.Data;

/** 条件命中后的动作：跳转到某题，或直接结束问卷 */
@Data
public class Action {

    /** 动作类型：jumpTo / end */
    private String action;

    /** jumpTo 时的目标题目 qid；end 时为 null */
    private String targetQid;
}
