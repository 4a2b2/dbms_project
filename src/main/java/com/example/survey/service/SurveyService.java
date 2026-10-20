package com.example.survey.service;

import com.example.survey.core.logic.LogicEngine;
import com.example.survey.core.question.QuestionTypeRegistry;
import com.example.survey.dto.CreateSurveyRequest;
import com.example.survey.dto.UpdateSurveyRequest;
import com.example.survey.exception.ApiException;
import com.example.survey.model.survey.LogicRule;
import com.example.survey.model.survey.Option;
import com.example.survey.model.survey.Question;
import com.example.survey.model.survey.Survey;
import com.example.survey.model.survey.SurveyStatus;
import com.example.survey.repository.SurveyRepository;
import com.example.survey.repository.SurveyResponseRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 问卷的创建、编辑、发布、关闭、删除 */
@Service
public class SurveyService {

    private static final String SHARE_CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int SHARE_CODE_LENGTH = 8;

    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository responseRepository;
    private final QuestionTypeRegistry typeRegistry;
    private final LogicEngine logicEngine;
    private final SecureRandom random = new SecureRandom();

    public SurveyService(SurveyRepository surveyRepository,
                         SurveyResponseRepository responseRepository,
                         QuestionTypeRegistry typeRegistry,
                         LogicEngine logicEngine) {
        this.surveyRepository = surveyRepository;
        this.responseRepository = responseRepository;
        this.typeRegistry = typeRegistry;
        this.logicEngine = logicEngine;
    }

    public Survey create(String uid, CreateSurveyRequest req) {
        Survey survey = new Survey();
        survey.setOwnerId(uid);
        survey.setTitle(req.title());
        survey.setDescription(req.description());
        return surveyRepository.save(survey);
    }

    public List<Survey> listMine(String uid) {
        return surveyRepository.findByOwnerIdOrderByCreatedAtDesc(uid);
    }

    /** 取问卷并校验归属：不存在 404，非本人 403 */
    public Survey getOwned(String uid, String id) {
        Survey survey = surveyRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("问卷不存在"));
        if (!survey.getOwnerId().equals(uid)) {
            throw ApiException.forbidden("无权访问该问卷");
        }
        return survey;
    }

    public Survey update(String uid, String id, UpdateSurveyRequest req) {
        Survey survey = getOwned(uid, id);
        if (survey.getStatus() != SurveyStatus.DRAFT
                && (req.questions() != null || req.logic() != null)) {
            throw ApiException.badRequest("发布后题目与逻辑结构不可修改");
        }
        if (req.title() != null) {
            survey.setTitle(req.title());
        }
        if (req.description() != null) {
            survey.setDescription(req.description());
        }
        if (req.settings() != null) {
            survey.setSettings(req.settings());
        }
        if (req.questions() != null) {
            survey.setQuestions(req.questions());
        }
        if (req.logic() != null) {
            survey.setLogic(req.logic());
        }
        validateStructure(survey);
        survey.setUpdatedAt(Instant.now());
        return surveyRepository.save(survey);
    }

    /** 发布：校验结构、生成分享码 */
    public Survey publish(String uid, String id) {
        Survey survey = getOwned(uid, id);
        if (survey.getStatus() != SurveyStatus.DRAFT) {
            throw ApiException.badRequest("仅草稿状态的问卷可以发布");
        }
        if (survey.getQuestions() == null || survey.getQuestions().isEmpty()) {
            throw ApiException.badRequest("问卷至少需要一道题才能发布");
        }
        validateStructure(survey);
        if (survey.getShareCode() == null) {
            survey.setShareCode(generateShareCode());
        }
        survey.setStatus(SurveyStatus.PUBLISHED);
        survey.setUpdatedAt(Instant.now());
        return surveyRepository.save(survey);
    }

    public Survey close(String uid, String id) {
        Survey survey = getOwned(uid, id);
        if (survey.getStatus() != SurveyStatus.PUBLISHED) {
            throw ApiException.badRequest("仅已发布的问卷可以关闭");
        }
        survey.setStatus(SurveyStatus.CLOSED);
        survey.setUpdatedAt(Instant.now());
        return surveyRepository.save(survey);
    }

    public void delete(String uid, String id) {
        Survey survey = getOwned(uid, id);
        responseRepository.deleteBySurveyId(survey.getId());
        surveyRepository.delete(survey);
    }

    /** 填写入口：按分享码取问卷，并检查可填写状态 */
    public Survey getForFill(String shareCode) {
        Survey survey = surveyRepository.findByShareCode(shareCode)
                .orElseThrow(() -> ApiException.notFound("问卷不存在"));
        if (survey.getStatus() == SurveyStatus.CLOSED) {
            throw ApiException.badRequest("问卷已关闭");
        }
        if (survey.getStatus() != SurveyStatus.PUBLISHED) {
            throw ApiException.notFound("问卷不存在");
        }
        Instant deadline = survey.getSettings().getDeadline();
        if (deadline != null && Instant.now().isAfter(deadline)) {
            throw ApiException.badRequest("问卷已截止");
        }
        return survey;
    }

    /** 校验题目与逻辑结构（保存草稿与发布前都会调用） */
    private void validateStructure(Survey survey) {
        List<Question> questions = survey.getQuestions();
        if (questions != null) {
            Set<String> qids = new HashSet<>();
            for (Question q : questions) {
                if (q.getQid() == null || q.getQid().isBlank()) {
                    q.setQid(randomId());
                }
                if (!qids.add(q.getQid())) {
                    throw ApiException.badRequest("题目 qid 重复：" + q.getQid());
                }
                if (q.getTitle() == null || q.getTitle().isBlank()) {
                    throw ApiException.badRequest("存在标题为空的题目");
                }
                if (q.getOptions() != null) {
                    Set<String> oids = new HashSet<>();
                    for (Option o : q.getOptions()) {
                        if (o.getOid() == null || o.getOid().isBlank()) {
                            o.setOid(randomId());
                        }
                        if (!oids.add(o.getOid())) {
                            throw ApiException.badRequest("题目「" + q.getTitle() + "」选项 oid 重复：" + o.getOid());
                        }
                    }
                }
                typeRegistry.get(q.getType()).validateQuestion(q);
            }
        }
        if (survey.getLogic() != null) {
            for (LogicRule rule : survey.getLogic()) {
                if (rule.getId() == null || rule.getId().isBlank()) {
                    rule.setId(randomId());
                }
            }
        }
        logicEngine.validateRules(survey);
    }

    private String generateShareCode() {
        String code;
        do {
            code = randomId();
        } while (surveyRepository.findByShareCode(code).isPresent());
        return code;
    }

    private String randomId() {
        StringBuilder sb = new StringBuilder(SHARE_CODE_LENGTH);
        for (int i = 0; i < SHARE_CODE_LENGTH; i++) {
            sb.append(SHARE_CODE_ALPHABET.charAt(random.nextInt(SHARE_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
