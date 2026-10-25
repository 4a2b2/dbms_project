package com.example.survey.repository;

import com.example.survey.model.question.QuestionItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface QuestionItemRepository extends MongoRepository<QuestionItem, String> {

    /** 我的题库，按更新时间倒序 */
    List<QuestionItem> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);

    /** 共享广场：所有共享题 */
    List<QuestionItem> findBySharedTrueOrderByUpdatedAtDesc();
}
