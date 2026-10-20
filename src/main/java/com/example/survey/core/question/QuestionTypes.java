package com.example.survey.core.question;

/** 内置题型常量；新题型直接新增常量 + 实现 QuestionTypeHandler 即可 */
public final class QuestionTypes {

    public static final String SINGLE = "single";
    public static final String MULTIPLE = "multiple";
    public static final String TEXT = "text";
    public static final String NUMBER = "number";

    private QuestionTypes() {
    }
}
