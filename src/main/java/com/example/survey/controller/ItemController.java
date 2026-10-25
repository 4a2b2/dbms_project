package com.example.survey.controller;

import com.example.survey.dto.ItemUpsertRequest;
import com.example.survey.dto.RestoreRequest;
import com.example.survey.dto.ShareRequest;
import com.example.survey.model.question.QuestionItem;
import com.example.survey.model.question.QuestionVersion;
import com.example.survey.security.Auth;
import com.example.survey.service.ItemService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 题库接口：题目条目、版本、共享、使用情况与跨问卷统计。均需登录 */
@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /** 我的题库（附每题当前版本内容） */
    @GetMapping
    public List<Map<String, Object>> mine(HttpServletRequest request) {
        return itemService.listMine(Auth.requireUid(request));
    }

    /** 共享广场：所有用户共享的题目 */
    @GetMapping("/shared")
    public List<Map<String, Object>> shared(HttpServletRequest request) {
        return itemService.listShared(Auth.requireUid(request));
    }

    /** 新建题目（自动成为 v1） */
    @PostMapping
    public QuestionItem create(HttpServletRequest request, @RequestBody ItemUpsertRequest req) {
        return itemService.create(Auth.requireUid(request), req);
    }

    /** 题目详情（当前版本内容） */
    @GetMapping("/{id}")
    public Map<String, Object> detail(HttpServletRequest request, @PathVariable String id) {
        String uid = Auth.requireUid(request);
        QuestionItem item = itemService.getReadable(uid, id);
        QuestionVersion current = itemService.currentVersion(item);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("ownerId", item.getOwnerId());
        map.put("mine", item.getOwnerId().equals(uid));
        map.put("shared", item.isShared());
        map.put("tags", item.getTags());
        map.put("copiedFromItemId", item.getCopiedFromItemId());
        map.put("currentVersionId", current.getId());
        map.put("currentVersion", current.getVersion());
        map.put("type", current.getType());
        map.put("title", current.getTitle());
        map.put("required", current.isRequired());
        map.put("options", current.getOptions());
        map.put("constraints", current.getConstraints());
        return map;
    }

    /** 修改题目：内容变化会创建新版本，旧版本保持不变 */
    @PutMapping("/{id}")
    public QuestionItem update(HttpServletRequest request, @PathVariable String id,
                               @RequestBody ItemUpsertRequest req) {
        return itemService.update(Auth.requireUid(request), id, req);
    }

    /** 共享开关 */
    @PostMapping("/{id}/share")
    public QuestionItem share(HttpServletRequest request, @PathVariable String id,
                              @RequestBody ShareRequest req) {
        return itemService.setShared(Auth.requireUid(request), id, req.shared());
    }

    /** 删除：被问卷引用时拒绝 */
    @DeleteMapping("/{id}")
    public Map<String, String> delete(HttpServletRequest request, @PathVariable String id) {
        itemService.delete(Auth.requireUid(request), id);
        return Map.of("result", "ok");
    }

    /** 版本历史链（v1 → v2 → v3，含 parentVersionId） */
    @GetMapping("/{id}/versions")
    public List<QuestionVersion> versions(HttpServletRequest request, @PathVariable String id) {
        return itemService.versions(Auth.requireUid(request), id);
    }

    /** 恢复旧版本：以该版本内容创建最新版本 */
    @PostMapping("/{id}/restore")
    public QuestionItem restore(HttpServletRequest request, @PathVariable String id,
                                @RequestBody RestoreRequest req) {
        return itemService.restore(Auth.requireUid(request), id, req.versionId());
    }

    /** 题目被哪些问卷使用 */
    @GetMapping("/{id}/usage")
    public List<Map<String, Object>> usage(HttpServletRequest request, @PathVariable String id) {
        return itemService.usage(Auth.requireUid(request), id);
    }

    /** 跨问卷统计：该题在所有引用问卷中的汇总回答 */
    @GetMapping("/{id}/stats")
    public Map<String, Object> stats(HttpServletRequest request, @PathVariable String id) {
        return itemService.stats(Auth.requireUid(request), id);
    }
}
