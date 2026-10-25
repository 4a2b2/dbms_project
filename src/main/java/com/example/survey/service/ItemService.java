package com.example.survey.service;

import com.example.survey.core.question.QuestionTypeRegistry;
import com.example.survey.dto.ItemUpsertRequest;
import com.example.survey.exception.ApiException;
import com.example.survey.model.question.QuestionItem;
import com.example.survey.model.question.QuestionVersion;
import com.example.survey.model.response.Answer;
import com.example.survey.model.response.ResponseStatus;
import com.example.survey.model.response.SurveyResponse;
import com.example.survey.model.survey.Option;
import com.example.survey.model.survey.Question;
import com.example.survey.model.survey.Survey;
import com.example.survey.model.survey.SurveyStatus;
import com.example.survey.repository.QuestionItemRepository;
import com.example.survey.repository.QuestionVersionRepository;
import com.example.survey.repository.SurveyRepository;
import com.example.survey.repository.SurveyResponseRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 题库：题目条目 + 不可变版本。
 * 核心约定：题目内容只能通过"创建新版本"演进，旧版本永不修改，
 * 因此引用旧版本的问卷（以及问卷内的快照）永远保持原内容。
 */
@Service
public class ItemService {

    private final QuestionItemRepository itemRepository;
    private final QuestionVersionRepository versionRepository;
    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository responseRepository;
    private final QuestionTypeRegistry typeRegistry;

    public ItemService(QuestionItemRepository itemRepository,
                       QuestionVersionRepository versionRepository,
                       SurveyRepository surveyRepository,
                       SurveyResponseRepository responseRepository,
                       QuestionTypeRegistry typeRegistry) {
        this.itemRepository = itemRepository;
        this.versionRepository = versionRepository;
        this.surveyRepository = surveyRepository;
        this.responseRepository = responseRepository;
        this.typeRegistry = typeRegistry;
    }

    /* ========== 创建 ========== */

    /** 题库新建题目：条目 + v1 */
    public QuestionItem create(String uid, ItemUpsertRequest req) {
        QuestionItem item = newItem(uid, req.tags(), null);
        QuestionVersion v1 = newVersion(item.getId(), null, 1,
                req.type(), req.title(), req.required() != null && req.required(),
                req.options(), req.constraints(), req.note());
        validateContent(v1);
        versionRepository.save(v1);
        item.setCurrentVersionId(v1.getId());
        return itemRepository.save(item);
    }

    /** 供问卷保存流程调用：问卷里的新题自动入题库 */
    public QuestionItem createFromQuestion(String uid, Question q, String copiedFromItemId) {
        QuestionItem item = newItem(uid, null, copiedFromItemId);
        QuestionVersion v1 = newVersion(item.getId(), null, 1,
                q.getType(), q.getTitle(), q.isRequired(), q.getOptions(), q.getConstraints(), null);
        versionRepository.save(v1);
        item.setCurrentVersionId(v1.getId());
        return itemRepository.save(item);
    }

    private QuestionItem newItem(String uid, List<String> tags, String copiedFromItemId) {
        QuestionItem item = new QuestionItem();
        item.setOwnerId(uid);
        if (tags != null) {
            item.setTags(cleanTags(tags));
        }
        item.setCopiedFromItemId(copiedFromItemId);
        return itemRepository.save(item);
    }

    /* ========== 查询 ========== */

    /** 我的题库（附当前版本内容） */
    public List<Map<String, Object>> listMine(String uid) {
        return itemRepository.findByOwnerIdOrderByUpdatedAtDesc(uid).stream()
                .map(item -> view(item, currentVersion(item), true))
                .toList();
    }

    /** 共享广场：所有用户共享的题（附当前版本内容） */
    public List<Map<String, Object>> listShared(String uid) {
        return itemRepository.findBySharedTrueOrderByUpdatedAtDesc().stream()
                .map(item -> view(item, currentVersion(item), item.getOwnerId().equals(uid)))
                .toList();
    }

    /** 读取校验：自己的题，或别人共享的题 */
    public QuestionItem getReadable(String uid, String id) {
        QuestionItem item = itemRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("题目不存在"));
        if (!item.getOwnerId().equals(uid) && !item.isShared()) {
            throw ApiException.forbidden("无权访问该题目");
        }
        return item;
    }

    /** 归属校验：仅创建者可改 */
    public QuestionItem getOwned(String uid, String id) {
        QuestionItem item = itemRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("题目不存在"));
        if (!item.getOwnerId().equals(uid)) {
            throw ApiException.forbidden("只有创建者可以修改该题目");
        }
        return item;
    }

    public QuestionVersion currentVersion(QuestionItem item) {
        return versionRepository.findById(item.getCurrentVersionId())
                .orElseThrow(() -> ApiException.notFound("题目版本不存在"));
    }

    /** 版本历史链：v1 → v2 → v3，含每个版本的 parentVersionId */
    public List<QuestionVersion> versions(String uid, String id) {
        getReadable(uid, id);
        return versionRepository.findByItemIdOrderByVersionAsc(id);
    }

    /* ========== 修改（产生新版本） ========== */

    /**
     * 修改题目：内容与当前版本一致时只更新标签；
     * 内容有变化时创建新版本（parent = 当前版本），旧版本保持不变。
     */
    public QuestionItem update(String uid, String id, ItemUpsertRequest req) {
        QuestionItem item = getOwned(uid, id);
        QuestionVersion current = currentVersion(item);

        boolean contentTouched = req.type() != null || req.title() != null
                || req.required() != null || req.options() != null || req.constraints() != null;
        if (contentTouched) {
            String type = req.type() != null ? req.type() : current.getType();
            String title = req.title() != null ? req.title() : current.getTitle();
            boolean required = req.required() != null ? req.required() : current.isRequired();
            List<Option> options = req.options() != null ? req.options() : current.getOptions();
            Map<String, Object> constraints = req.constraints() != null ? req.constraints() : current.getConstraints();
            // 与当前版本真正不同时才创建新版本（避免只改标签也刷出版本）
            if (!sameContent(current, type, title, required, options, constraints)) {
                createNextVersion(item, current, type, title, required, options, constraints, req.note());
            }
        }
        if (req.tags() != null) {
            item.setTags(cleanTags(req.tags()));
        }
        item.setUpdatedAt(Instant.now());
        return itemRepository.save(item);
    }

    /** 供问卷保存流程调用：基于 parent 版本创建新版本并推进当前指针 */
    public QuestionVersion createNextVersion(QuestionItem item, QuestionVersion parent,
                                             String type, String title, boolean required,
                                             List<Option> options, Map<String, Object> constraints,
                                             String note) {
        QuestionVersion next = newVersion(item.getId(), parent == null ? null : parent.getId(),
                parent == null ? 1 : parent.getVersion() + 1,
                type, title, required, options, constraints, note);
        validateContent(next);
        versionRepository.save(next);
        item.setCurrentVersionId(next.getId());
        item.setUpdatedAt(Instant.now());
        itemRepository.save(item);
        return next;
    }

    /** 恢复旧版本：以指定版本的内容创建最新版本（历史链保持完整，不做回滚） */
    public QuestionItem restore(String uid, String id, String versionId) {
        QuestionItem item = getOwned(uid, id);
        QuestionVersion current = currentVersion(item);
        QuestionVersion target = versionRepository.findById(versionId)
                .filter(v -> v.getItemId().equals(id))
                .orElseThrow(() -> ApiException.notFound("该版本不存在"));
        createNextVersion(item, current, target.getType(), target.getTitle(), target.isRequired(),
                target.getOptions(), target.getConstraints(),
                "恢复自 v" + target.getVersion());
        return item;
    }

    /* ========== 共享 / 删除 ========== */

    public QuestionItem setShared(String uid, String id, boolean shared) {
        QuestionItem item = getOwned(uid, id);
        item.setShared(shared);
        item.setUpdatedAt(Instant.now());
        return itemRepository.save(item);
    }

    /** 删除：仅允许删除未被任何问卷引用的题目，避免已建问卷失引用 */
    public void delete(String uid, String id) {
        QuestionItem item = getOwned(uid, id);
        if (!surveyRepository.findByQuestionItemId(id).isEmpty()) {
            throw ApiException.conflict("该题目已被问卷使用，不能删除（可取消共享或停用）");
        }
        versionRepository.findByItemIdOrderByVersionAsc(id)
                .forEach(versionRepository::delete);
        itemRepository.delete(item);
    }

    /* ========== 使用情况（需求六） ========== */

    /** 题目被哪些问卷使用：返回每份问卷引用的版本与位置 */
    public List<Map<String, Object>> usage(String uid, String id) {
        getOwned(uid, id);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Survey s : surveyRepository.findByQuestionItemId(id)) {
            for (Question q : s.getQuestions()) {
                if (!id.equals(q.getItemId())) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("surveyId", s.getId());
                row.put("surveyTitle", s.getTitle());
                row.put("surveyStatus", s.getStatus());
                row.put("qid", q.getQid());
                row.put("version", q.getVersion());
                row.put("versionId", q.getVersionId());
                result.add(row);
            }
        }
        return result;
    }

    /* ========== 跨问卷统计（需求八） ========== */

    /**
     * 某道题在所有引用它的问卷中的汇总回答情况。
     * 按版本分组聚合（不同版本内容可能不同），文本/数字题额外给跨版本总览。
     */
    public Map<String, Object> stats(String uid, String id) {
        QuestionItem item = getOwned(uid, id);
        List<QuestionVersion> versions = versionRepository.findByItemIdOrderByVersionAsc(id);
        Map<String, QuestionVersion> byId = new LinkedHashMap<>();
        for (QuestionVersion v : versions) {
            byId.put(v.getId(), v);
        }

        // 按版本收集答案：遍历引用过这些版本的非草稿问卷
        Map<String, List<Answer>> answersByVersion = new LinkedHashMap<>();
        Map<String, Set<String>> surveysByVersion = new LinkedHashMap<>();
        for (Survey s : surveyRepository.findByQuestionVersionIdIn(byId.keySet())) {
            if (s.getStatus() == SurveyStatus.DRAFT) {
                continue;
            }
            List<SurveyResponse> submitted = null;
            for (Question q : s.getQuestions()) {
                if (q.getVersionId() == null || !byId.containsKey(q.getVersionId())) {
                    continue;
                }
                if (submitted == null) {
                    submitted = responseRepository.findBySurveyIdAndStatus(s.getId(), ResponseStatus.SUBMITTED);
                }
                List<Answer> answers = submitted.stream()
                        .map(r -> r.answerByQid(q.getQid()))
                        .filter(Objects::nonNull)
                        .toList();
                answersByVersion.computeIfAbsent(q.getVersionId(), k -> new ArrayList<>()).addAll(answers);
                surveysByVersion.computeIfAbsent(q.getVersionId(), k -> new HashSet<>()).add(s.getId());
            }
        }

        List<Map<String, Object>> byVersion = new ArrayList<>();
        List<Answer> allAnswers = new ArrayList<>();
        int totalResponses = 0;
        for (QuestionVersion v : versions) {
            List<Answer> answers = answersByVersion.getOrDefault(v.getId(), List.of());
            allAnswers.addAll(answers);
            totalResponses += answers.size();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("version", v.getVersion());
            row.put("versionId", v.getId());
            row.put("title", v.getTitle());
            row.put("surveyCount", surveysByVersion.getOrDefault(v.getId(), Set.of()).size());
            row.put("responseCount", answers.size());
            row.put("summary", typeRegistry.get(v.getType()).summarize(toQuestion(v), answers));
            byVersion.add(row);
        }

        // 跨版本总览：仅文本/数字题可安全合并（选择题选项 oid 可能随版本变化，只看分组）
        Object overall = null;
        String currentType = currentVersion(item).getType();
        boolean sameType = versions.stream().allMatch(v -> v.getType().equals(currentType));
        if (sameType && ("text".equals(currentType) || "number".equals(currentType))) {
            overall = typeRegistry.get(currentType)
                    .summarize(toQuestion(currentVersion(item)), allAnswers);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itemId", item.getId());
        result.put("title", currentVersion(item).getTitle());
        result.put("type", currentType);
        result.put("versionCount", versions.size());
        result.put("totalResponses", totalResponses);
        result.put("byVersion", byVersion);
        result.put("overall", overall);
        return result;
    }

    /* ========== 内部工具 ========== */

    /** 判断问卷中的题目内容与指定版本是否一致（一致则沿用版本，不一致则需新版本） */
    public boolean contentEquals(QuestionVersion v, Question q) {
        return sameContent(v, q.getType(), q.getTitle(), q.isRequired(), q.getOptions(), q.getConstraints());
    }

    private boolean sameContent(QuestionVersion v, String type, String title, boolean required,
                                List<Option> options, Map<String, Object> constraints) {
        return Objects.equals(v.getType(), type)
                && Objects.equals(v.getTitle(), title)
                && v.isRequired() == required
                && optionsEquals(v.getOptions(), options)
                && constraintsEquals(v.getConstraints(), constraints);
    }

    private boolean optionsEquals(List<Option> a, List<Option> b) {
        List<Option> x = a == null ? List.of() : a;
        List<Option> y = b == null ? List.of() : b;
        if (x.size() != y.size()) {
            return false;
        }
        for (int i = 0; i < x.size(); i++) {
            if (!Objects.equals(x.get(i).getOid(), y.get(i).getOid())
                    || !Objects.equals(x.get(i).getText(), y.get(i).getText())) {
                return false;
            }
        }
        return true;
    }

    private boolean constraintsEquals(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> x = a == null ? Map.of() : a;
        Map<String, Object> y = b == null ? Map.of() : b;
        if (!x.keySet().equals(y.keySet())) {
            return false;
        }
        // JSON 反序列化后数字可能是 Integer 或 Double，统一按字符串比较
        for (String key : x.keySet()) {
            if (!Objects.equals(String.valueOf(x.get(key)), String.valueOf(y.get(key)))) {
                return false;
            }
        }
        return true;
    }

    /** 用版本内容构造临时 Question（供题型处理器校验/统计） */
    public Question toQuestion(QuestionVersion v) {
        Question q = new Question();
        q.setQid("v");
        q.setType(v.getType());
        q.setTitle(v.getTitle());
        q.setRequired(v.isRequired());
        q.setOptions(v.getOptions());
        q.setConstraints(v.getConstraints());
        q.setItemId(v.getItemId());
        q.setVersionId(v.getId());
        q.setVersion(v.getVersion());
        return q;
    }

    /** 校验题目内容（结构与约束合法性），复用题型处理器 */
    private void validateContent(QuestionVersion v) {
        if (v.getTitle() == null || v.getTitle().isBlank()) {
            throw ApiException.badRequest("题目标题不能为空");
        }
        if (v.getOptions() != null) {
            Set<String> oids = new HashSet<>();
            for (Option o : v.getOptions()) {
                if (o.getOid() == null || o.getOid().isBlank()) {
                    throw ApiException.badRequest("选项 oid 不能为空");
                }
                if (!oids.add(o.getOid())) {
                    throw ApiException.badRequest("选项 oid 重复：" + o.getOid());
                }
            }
        }
        typeRegistry.get(v.getType()).validateQuestion(toQuestion(v));
    }

    private QuestionVersion newVersion(String itemId, String parentVersionId, int version,
                                       String type, String title, boolean required,
                                       List<Option> options, Map<String, Object> constraints,
                                       String note) {
        QuestionVersion v = new QuestionVersion();
        v.setItemId(itemId);
        v.setParentVersionId(parentVersionId);
        v.setVersion(version);
        v.setType(type);
        v.setTitle(title);
        v.setRequired(required);
        v.setOptions(options == null ? new ArrayList<>() : new ArrayList<>(options));
        v.setConstraints(constraints == null ? new HashMap<>() : new HashMap<>(constraints));
        v.setNote(note);
        return v;
    }

    private List<String> cleanTags(List<String> tags) {
        return tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private Map<String, Object> view(QuestionItem item, QuestionVersion current, boolean mine) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("ownerId", item.getOwnerId());
        map.put("mine", mine);
        map.put("shared", item.isShared());
        map.put("tags", item.getTags());
        map.put("currentVersionId", item.getCurrentVersionId());
        map.put("currentVersion", current.getVersion());
        map.put("copiedFromItemId", item.getCopiedFromItemId());
        map.put("createdAt", item.getCreatedAt());
        map.put("updatedAt", item.getUpdatedAt());
        map.put("type", current.getType());
        map.put("title", current.getTitle());
        map.put("required", current.isRequired());
        map.put("options", current.getOptions());
        map.put("constraints", current.getConstraints());
        return map;
    }
}
