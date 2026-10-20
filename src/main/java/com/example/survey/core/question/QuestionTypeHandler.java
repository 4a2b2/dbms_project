package com.example.survey.core.question;

import com.example.survey.model.response.Answer;
import com.example.survey.model.survey.Question;

import java.util.List;
import java.util.Map;

/**
 * 题型处理器：定义该题型的结构校验、答案校验与统计汇总。
 * 新题型实现此接口并声明为 Spring 组件即可接入系统。
 */
public interface QuestionTypeHandler {

    /** 题型字符串标识 */
    String type();

    /**
     * 校验题目结构（选项数量、约束参数是否自洽），保存问卷时调用。
     * 不合法时抛出 ApiException.badRequest。
     */
    void validateQuestion(Question question);

    /**
     * 校验一份回答是否合法，提交时调用。
     * answer 为 null 或三个值字段都为空表示未作答（required 时由实现类报错）。
     */
    void validateAnswer(Question question, Answer answer);

    /** 对已提交的答案列表做统计汇总 */
    Map<String, Object> summarize(Question question, List<Answer> answers);
}
