package com.example.survey.repository;

import com.example.survey.model.survey.Survey;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SurveyRepository extends MongoRepository<Survey, String> {

    /** 我的问卷列表，按创建时间倒序 */
    List<Survey> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    /** 通过分享码找到问卷（填写页入口） */
    Optional<Survey> findByShareCode(String shareCode);
}
