package com.example.survey.model.survey;

import lombok.Data;

/** 跳转规则的触发条件：对某题的答案做运算 */
@Data
public class Condition {

    /** 目标题目 qid */
    private String qid;

    /** 算子，如 eq / in / contains / containsAll / gt / gte / lt / lte / between */
    private String op;

    /** 比较值（字符串、字符串列表或数字，由算子解释） */
    private Object value;
}
