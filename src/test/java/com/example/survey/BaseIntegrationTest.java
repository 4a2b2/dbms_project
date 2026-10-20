package com.example.survey;

import com.example.survey.model.survey.Option;
import com.example.survey.model.survey.Question;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 集成测试基类：启动完整 Spring 上下文 + 内嵌 MongoDB（de.flapdoodle）。
 * 每个用例前清空数据库，保证用例之间互不影响。
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    @Autowired
    protected MongoTemplate mongoTemplate;

    @BeforeEach
    void cleanDatabase() {
        mongoTemplate.getDb().drop();
    }

    /* ========== 题目构造辅助 ========== */

    /** 单选题：选项 oid 形如 qid-o1 / qid-o2 ... */
    protected Question single(String qid, String title, boolean required, String... optionTexts) {
        Question q = base(qid, "single", title, required);
        q.setOptions(optionTexts(qid, optionTexts));
        return q;
    }

    /** 多选题：可附加 minSelect / maxSelect 约束 */
    protected Question multiple(String qid, String title, boolean required,
                                Map<String, Object> constraints, String... optionTexts) {
        Question q = base(qid, "multiple", title, required);
        q.setOptions(optionTexts(qid, optionTexts));
        if (constraints != null) {
            q.setConstraints(constraints);
        }
        return q;
    }

    /** 文本题 */
    protected Question text(String qid, String title, boolean required, Map<String, Object> constraints) {
        Question q = base(qid, "text", title, required);
        if (constraints != null) {
            q.setConstraints(constraints);
        }
        return q;
    }

    /** 数字题 */
    protected Question number(String qid, String title, boolean required, Map<String, Object> constraints) {
        Question q = base(qid, "number", title, required);
        if (constraints != null) {
            q.setConstraints(constraints);
        }
        return q;
    }

    /** 选项 oid：qid-o1、qid-o2 ... 与 optionTexts 顺序对应 */
    protected String oid(String qid, int index) {
        return qid + "-o" + index;
    }

    private Question base(String qid, String type, String title, boolean required) {
        Question q = new Question();
        q.setQid(qid);
        q.setType(type);
        q.setTitle(title);
        q.setRequired(required);
        return q;
    }

    private List<Option> optionTexts(String qid, String... texts) {
        List<Option> options = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            Option o = new Option();
            o.setOid(oid(qid, i + 1));
            o.setText(texts[i]);
            options.add(o);
        }
        return options;
    }
}
