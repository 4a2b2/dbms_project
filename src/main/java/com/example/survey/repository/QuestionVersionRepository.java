package com.example.survey.repository;

import com.example.survey.model.question.QuestionVersion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface QuestionVersionRepository extends MongoRepository<QuestionVersion, String> {

    /** 某条目的全部版本，按版本号升序（构成 v1→v2→v3 历史链） */
    List<QuestionVersion> findByItemIdOrderByVersionAsc(String itemId);
}
