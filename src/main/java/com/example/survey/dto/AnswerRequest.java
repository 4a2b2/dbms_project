package com.example.survey.dto;

import com.example.survey.model.response.Answer;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** 保存单题答案请求；三个值字段按题型只会用一个 */
public record AnswerRequest(
        @NotBlank(message = "qid 不能为空")
        String qid,

        List<String> optionIds,
        String text,
        Double number) {

    /** 转为领域对象 */
    public Answer toAnswer() {
        Answer a = new Answer();
        a.setQid(qid);
        a.setOptionIds(optionIds);
        a.setText(text);
        a.setNumber(number);
        return a;
    }
}
