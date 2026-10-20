package com.example.survey.model.response;

import lombok.Data;

import java.util.List;

/**
 * 一道题的回答（内嵌文档）。
 * 三个值字段按题型只会有一个非空：选择题用 optionIds，文本题用 text，数字题用 number。
 * 三个字段都为空表示该题未作答。
 */
@Data
public class Answer {

    /** 对应题目 qid */
    private String qid;

    /** 选择题：选中的选项 oid 列表（单选只有一个元素） */
    private List<String> optionIds;

    /** 文本题：回答内容 */
    private String text;

    /** 数字题：回答数值 */
    private Double number;
}
