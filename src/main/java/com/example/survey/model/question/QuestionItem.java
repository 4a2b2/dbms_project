package com.example.survey.model.question;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 题库条目：一道可复用题目的"身份"，其内容以版本形式存放在 question_versions 集合。
 * 条目本身不保存题目内容，只保存归属、标签、共享标记与当前版本指针。
 */
@Data
@Document("question_items")
public class QuestionItem {

    @Id
    private String id;

    /** 创建者用户 id；共享题对其他用户只读 */
    @Indexed
    private String ownerId;

    /** 组织用标签（如「人口学」「课程反馈」），用于题库筛选 */
    private List<String> tags = new ArrayList<>();

    /** 是否共享：共享后其他登录用户可查看并引用到自己的问卷 */
    private boolean shared;

    /** 当前最新版本 id（指向 question_versions） */
    private String currentVersionId;

    /** 若本题是从别人的共享题"另存"而来，记录来源条目 id（否则为 null） */
    private String copiedFromItemId;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();
}
