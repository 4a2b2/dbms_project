package com.example.survey.repository;

import com.example.survey.model.survey.Survey;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SurveyRepository extends MongoRepository<Survey, String> {

    /** 我的问卷列表，按创建时间倒序 */
    List<Survey> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    /** 通过分享码找到问卷（填写页入口） */
    Optional<Survey> findByShareCode(String shareCode);

    /** 引用了任一给定题目版本的问卷（用于"题目被哪些问卷使用"与跨问卷统计） */
    @Query("{ 'questions.versionId': { $in: ?0 } }")
    List<Survey> findByQuestionVersionIdIn(Collection<String> versionIds);

    /** 引用了某题库条目（任一版本）的问卷 */
    @Query("{ 'questions.itemId': ?0 }")
    List<Survey> findByQuestionItemId(String itemId);

    /** 待迁移的旧结构问卷 */
    List<Survey> findBySchemaVersionLessThan(int schemaVersion);
}
