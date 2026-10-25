package com.example.survey.config;

import com.example.survey.model.question.QuestionItem;
import com.example.survey.model.question.QuestionVersion;
import com.example.survey.model.survey.Question;
import com.example.survey.model.survey.Survey;
import com.example.survey.repository.QuestionItemRepository;
import com.example.survey.repository.QuestionVersionRepository;
import com.example.survey.repository.SurveyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 结构迁移：v1（题目整段内嵌）→ v2（题库版本引用 + 快照）。
 * 启动时扫描 schemaVersion < 2 的问卷，为每道旧题在题库中创建条目与 v1 版本，
 * 并在问卷内嵌题目上回填 itemId/versionId/version。迁移幂等：已带 itemId 的题跳过。
 */
@Component
public class SchemaMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationRunner.class);

    private final SurveyRepository surveyRepository;
    private final QuestionItemRepository itemRepository;
    private final QuestionVersionRepository versionRepository;

    public SchemaMigrationRunner(SurveyRepository surveyRepository,
                                 QuestionItemRepository itemRepository,
                                 QuestionVersionRepository versionRepository) {
        this.surveyRepository = surveyRepository;
        this.itemRepository = itemRepository;
        this.versionRepository = versionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Survey> oldSurveys = surveyRepository.findBySchemaVersionLessThan(Survey.SCHEMA_VERSION);
        if (oldSurveys.isEmpty()) {
            return;
        }
        int migratedQuestions = 0;
        for (Survey survey : oldSurveys) {
            for (Question q : survey.getQuestions()) {
                if (q.getItemId() != null) {
                    continue;
                }
                QuestionItem item = new QuestionItem();
                item.setOwnerId(survey.getOwnerId());
                itemRepository.save(item);

                QuestionVersion v1 = new QuestionVersion();
                v1.setItemId(item.getId());
                v1.setVersion(1);
                v1.setType(q.getType());
                v1.setTitle(q.getTitle());
                v1.setRequired(q.isRequired());
                v1.setOptions(q.getOptions());
                v1.setConstraints(q.getConstraints());
                v1.setNote("由旧版问卷迁移");
                versionRepository.save(v1);

                item.setCurrentVersionId(v1.getId());
                itemRepository.save(item);

                q.setItemId(item.getId());
                q.setVersionId(v1.getId());
                q.setVersion(1);
                migratedQuestions++;
            }
            survey.setSchemaVersion(Survey.SCHEMA_VERSION);
            surveyRepository.save(survey);
        }
        log.info("结构迁移完成：{} 份问卷、{} 道题目已迁移到题库版本模型（schemaVersion={}）",
                oldSurveys.size(), migratedQuestions, Survey.SCHEMA_VERSION);
    }
}
