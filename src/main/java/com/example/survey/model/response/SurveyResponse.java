package com.example.survey.model.response;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 一份答卷（问卷的引用 + 逐题答案 + 填写进度） */
@Data
@Document("responses")
public class SurveyResponse {

    @Id
    private String id;

    /** 所属问卷 id */
    @Indexed
    private String surveyId;

    /** 填写人用户 id，匿名填写时为 null */
    private String respondentId;

    private ResponseStatus status = ResponseStatus.DRAFT;

    /** 逐题答案 */
    private List<Answer> answers = new ArrayList<>();

    /** 当前做到哪道题（草稿续填用），qid */
    private String currentQid;

    private Instant createdAt = Instant.now();

    /** 提交时间，草稿为 null */
    private Instant submittedAt;

    /** 按 qid 查找答案，找不到返回 null */
    public Answer answerByQid(String qid) {
        if (qid == null || answers == null) {
            return null;
        }
        return answers.stream().filter(a -> qid.equals(a.getQid())).findFirst().orElse(null);
    }
}
