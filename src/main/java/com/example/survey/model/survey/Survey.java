package com.example.survey.model.survey;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 问卷聚合根：题目、跳转逻辑全部内嵌，一次查询取出整卷。
 * 发布后 questions/logic 结构锁定，只能改标题、说明、设置。
 */
@Data
@Document("surveys")
public class Survey {

    /** 结构版本号，便于后续升级迁移。v2：题目改为题库版本引用 + 快照 */
    public static final int SCHEMA_VERSION = 2;

    @Id
    private String id;

    /** 创建者用户 id */
    @Indexed
    private String ownerId;

    private String title;

    private String description;

    private SurveyStatus status = SurveyStatus.DRAFT;

    /** 对外分享链接使用的短码，发布后生成，唯一 */
    @Indexed(unique = true)
    private String shareCode;

    private Settings settings = new Settings();

    /** 题目列表，数组顺序即默认答题顺序 */
    private List<Question> questions = new ArrayList<>();

    /** 跳转逻辑规则 */
    private List<LogicRule> logic = new ArrayList<>();

    private int schemaVersion = SCHEMA_VERSION;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    /** 按 qid 查找题目，找不到返回 null */
    public Question questionByQid(String qid) {
        if (qid == null || questions == null) {
            return null;
        }
        return questions.stream().filter(q -> qid.equals(q.getQid())).findFirst().orElse(null);
    }

    /** 题目在顺序中的下标，找不到返回 -1 */
    public int indexOfQuestion(String qid) {
        if (qid == null || questions == null) {
            return -1;
        }
        for (int i = 0; i < questions.size(); i++) {
            if (qid.equals(questions.get(i).getQid())) {
                return i;
            }
        }
        return -1;
    }
}
