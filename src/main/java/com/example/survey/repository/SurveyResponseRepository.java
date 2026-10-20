package com.example.survey.repository;

import com.example.survey.model.response.ResponseStatus;
import com.example.survey.model.response.SurveyResponse;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SurveyResponseRepository extends MongoRepository<SurveyResponse, String> {

    /** 某问卷下某状态的全部答卷（统计用） */
    List<SurveyResponse> findBySurveyIdAndStatus(String surveyId, ResponseStatus status);

    /** 同一用户在某问卷下的已提交答卷数（allowMultiple 检查用） */
    long countBySurveyIdAndRespondentIdAndStatus(String surveyId, String respondentId, ResponseStatus status);

    /** 按 id + surveyId 查答卷，防止跨问卷访问 */
    Optional<SurveyResponse> findByIdAndSurveyId(String id, String surveyId);

    /** 查用户在某问卷下的草稿（续填用） */
    List<SurveyResponse> findBySurveyIdAndRespondentIdAndStatus(String surveyId, String respondentId, ResponseStatus status);

    /** 删除问卷时级联清理答卷 */
    void deleteBySurveyId(String surveyId);
}
