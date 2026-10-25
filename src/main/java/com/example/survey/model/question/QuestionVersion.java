package com.example.survey.model.question;

import com.example.survey.model.survey.Option;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 题目版本：不可变。任何"修改题目"都产生一个新版本，旧版本永远保持原内容，
 * 从而已发布问卷（引用具体版本并持有快照）不受后续修改影响。
 */
@Data
@Document("question_versions")
@CompoundIndex(name = "item_version", def = "{'itemId': 1, 'version': 1}", unique = true)
public class QuestionVersion {

    @Id
    private String id;

    /** 所属题库条目 */
    @Indexed
    private String itemId;

    /** 版本号：1, 2, 3...（条目内递增） */
    private int version;

    /** 从哪个版本修改而来；v1 为 null。构成版本链，可回溯历史 */
    private String parentVersionId;

    /* ===== 以下为题目内容（与问卷内嵌 Question 的字段一致） ===== */

    private String type;

    private String title;

    private boolean required;

    private List<Option> options = new ArrayList<>();

    private Map<String, Object> constraints = new HashMap<>();

    /** 本次修改说明（可选） */
    private String note;

    private Instant createdAt = Instant.now();
}
