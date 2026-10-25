package com.example.survey.service;

import com.example.survey.core.logic.LogicEngine;
import com.example.survey.core.question.QuestionTypeRegistry;
import com.example.survey.dto.CreateSurveyRequest;
import com.example.survey.dto.UpdateSurveyRequest;
import com.example.survey.exception.ApiException;
import com.example.survey.model.question.QuestionItem;
import com.example.survey.model.question.QuestionVersion;
import com.example.survey.model.survey.LogicRule;
import com.example.survey.model.survey.Option;
import com.example.survey.model.survey.Question;
import com.example.survey.model.survey.Survey;
import com.example.survey.model.survey.SurveyStatus;
import com.example.survey.repository.QuestionItemRepository;
import com.example.survey.repository.QuestionVersionRepository;
import com.example.survey.repository.SurveyRepository;
import com.example.survey.repository.SurveyResponseRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 问卷的创建、编辑、发布、关闭、删除 */
@Service
public class SurveyService {

    private static final String SHARE_CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int SHARE_CODE_LENGTH = 8;

    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository responseRepository;
    private final QuestionItemRepository itemRepository;
    private final QuestionVersionRepository versionRepository;
    private final ItemService itemService;
    private final QuestionTypeRegistry typeRegistry;
    private final LogicEngine logicEngine;
    private final SecureRandom random = new SecureRandom();

    public SurveyService(SurveyRepository surveyRepository,
                         SurveyResponseRepository responseRepository,
                         QuestionItemRepository itemRepository,
                         QuestionVersionRepository versionRepository,
                         ItemService itemService,
                         QuestionTypeRegistry typeRegistry,
                         LogicEngine logicEngine) {
        this.surveyRepository = surveyRepository;
        this.responseRepository = responseRepository;
        this.itemRepository = itemRepository;
        this.versionRepository = versionRepository;
        this.itemService = itemService;
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
            survey.setQuestions(resolveQuestions(uid, req.questions()));
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

    /**
     * 保存问卷前解析每道题与题库的关系：
     * - 无 itemId：新题，自动创建题库条目 + v1；
     * - 仅带 itemId/versionId 且无内容：纯引用（从题库选题/切换版本），以版本内容写快照；
     * - 内容与所引版本一致：沿用版本；
     * - 内容有改动且是自己的题：在该条目下创建新版本并引用；
     * - 内容有改动且是别人的共享题：另存为自己的新条目（fork），不污染他人题库。
     * 无论哪条路径，最终都把版本内容写入问卷内嵌快照——发布后快照固化，题库再改不影响本问卷。
     */
    private List<Question> resolveQuestions(String uid, List<Question> questions) {
        for (Question q : questions) {
            if (q.getItemId() == null) {
                QuestionItem item = itemService.createFromQuestion(uid, q, null);
                applyRef(q, item, itemService.currentVersion(item));
                continue;
            }
            QuestionItem item = itemRepository.findById(q.getItemId())
                    .orElseThrow(() -> ApiException.badRequest("引用的题目不存在"));
            boolean mine = item.getOwnerId().equals(uid);
            if (!mine && !item.isShared()) {
                throw ApiException.forbidden("无权引用该题目");
            }
            QuestionVersion ref;
            if (q.getVersionId() != null) {
                ref = versionRepository.findById(q.getVersionId())
                        .filter(v -> v.getItemId().equals(item.getId()))
                        .orElseThrow(() -> ApiException.badRequest("题目版本不存在"));
            } else {
                ref = itemService.currentVersion(item);
            }
            boolean hasContent = q.getTitle() != null && !q.getTitle().isBlank();
            if (!hasContent || itemService.contentEquals(ref, q)) {
                copyContent(ref, q);
                applyRef(q, item, ref);
            } else if (mine) {
                QuestionVersion next = itemService.createNextVersion(item, ref,
                        q.getType(), q.getTitle(), q.isRequired(),
                        q.getOptions(), q.getConstraints(), "在问卷中修改");
                copyContent(next, q);
                applyRef(q, item, next);
            } else {
                QuestionItem fork = itemService.createFromQuestion(uid, q, item.getId());
                applyRef(q, fork, itemService.currentVersion(fork));
            }
        }
        return questions;
    }

    /** 以版本内容覆盖问卷内嵌快照字段（不覆盖 qid 与引用信息） */
    private void copyContent(QuestionVersion v, Question q) {
        q.setType(v.getType());
        q.setTitle(v.getTitle());
        q.setRequired(v.isRequired());
        q.setOptions(v.getOptions() == null ? List.of() : v.getOptions());
        q.setConstraints(v.getConstraints() == null ? Map.of() : v.getConstraints());
    }

    private void applyRef(Question q, QuestionItem item, QuestionVersion v) {
        q.setItemId(item.getId());
        q.setVersionId(v.getId());
        q.setVersion(v.getVersion());
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
