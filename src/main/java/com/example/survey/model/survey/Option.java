package com.example.survey.model.survey;

import lombok.Data;

/** 选择题选项 */
@Data
public class Option {

    /** 选项在题目内的唯一标识 */
    private String oid;

    /** 选项文案 */
    private String text;
}
