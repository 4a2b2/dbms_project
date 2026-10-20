package com.example.survey.core.logic;

import com.example.survey.model.response.Answer;

/** 条件算子：判断一份答案是否满足某个比较值 */
public interface ConditionOperator {

    /** 算子名，如 eq / contains / between */
    String op();

    /** 答案满足条件返回 true；答案为空一律返回 false */
    boolean test(Answer answer, Object value);
}
