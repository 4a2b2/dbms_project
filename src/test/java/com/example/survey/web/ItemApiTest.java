package com.example.survey.web;

import com.example.survey.BaseIntegrationTest;
import com.example.survey.config.SchemaMigrationRunner;
import com.example.survey.model.survey.Survey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段二题库端到端测试，覆盖需求：
 * ①题目独立保存复用 ②共享 ③修改不影响已建问卷（快照隔离）
 * ④版本历史与恢复 ⑤多版本并存 ⑥使用情况 ⑦题库管理 ⑧跨问卷统计
 */
class ItemApiTest extends BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    SchemaMigrationRunner migrationRunner;

    /* ========== 辅助 ========== */

    private String register(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Test1234!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json).get("token").asText();
    }

    /** 题库建题，返回 itemId */
    private String createItem(String token, String type, String title, List<Map<String, Object>> options) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("title", title);
        body.put("required", true);
        if (options != null) {
            body.put("options", options);
        }
        String json = mockMvc.perform(post("/api/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json).get("id").asText();
    }

    private JsonNode itemDetail(String token, String itemId) throws Exception {
        String json = mockMvc.perform(get("/api/items/" + itemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
    }

    private static List<Map<String, Object>> opts(String... texts) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            list.add(Map.of("oid", "o" + (i + 1), "text", texts[i]));
        }
        return list;
    }

    /** 按题目当前版本构造问卷引用题（内容与版本一致，保存时沿用该版本） */
    private Map<String, Object> refQuestion(String qid, JsonNode detail) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("qid", qid);
        q.put("itemId", detail.get("id").asText());
        q.put("versionId", detail.get("currentVersionId").asText());
        q.put("type", detail.get("type").asText());
        q.put("title", detail.get("title").asText());
        q.put("required", detail.get("required").asBoolean());
        if (detail.has("options")) {
            q.put("options", objectMapper.convertValue(detail.get("options"), List.class));
        }
        if (detail.has("constraints")) {
            q.put("constraints", objectMapper.convertValue(detail.get("constraints"), Map.class));
        }
        return q;
    }

    /** 建卷并写入题目，返回 surveyId */
    private String createSurvey(String token, List<?> questions) throws Exception {
        String created = mockMvc.perform(post("/api/surveys")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"测试问卷\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String surveyId = objectMapper.readTree(created).get("id").asText();
        mockMvc.perform(put("/api/surveys/" + surveyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("questions", questions))))
                .andExpect(status().isOk());
        return surveyId;
    }

    private JsonNode getSurvey(String token, String surveyId) throws Exception {
        String json = mockMvc.perform(get("/api/surveys/" + surveyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
    }

    private String publish(String token, String surveyId) throws Exception {
        String json = mockMvc.perform(post("/api/surveys/" + surveyId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json).get("shareCode").asText();
    }

    /** 匿名答数字题并提交 */
    private void submitNumber(String shareCode, String qid, double number) throws Exception {
        String started = mockMvc.perform(post("/api/s/" + shareCode + "/responses"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String responseId = objectMapper.readTree(started).get("responseId").asText();
        mockMvc.perform(post("/api/s/" + shareCode + "/responses/" + responseId + "/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qid\":\"" + qid + "\",\"number\":" + number + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/s/" + shareCode + "/responses/" + responseId + "/submit"))
                .andExpect(status().isOk());
    }

    /* ========== 用例 ========== */

    @Test
    void itemsRequireLogin() throws Exception {
        mockMvc.perform(get("/api/items")).andExpect(status().isUnauthorized());
    }

    /** 需求一/七：题库建题并出现在我的题库 */
    @Test
    void createAndListItem() throws Exception {
        String token = register("lib_create");
        String itemId = createItem(token, "single", "你的性别", opts("男", "女"));

        String list = mockMvc.perform(get("/api/items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("你的性别"))
                .andExpect(jsonPath("$[0].currentVersion").value(1))
                .andExpect(jsonPath("$[0].mine").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(itemId, objectMapper.readTree(list).get(0).get("id").asText());
    }

    /** 需求四：修改产生新版本，旧版本内容不变（版本不可变） */
    @Test
    void updateCreatesNewVersionAndKeepsOld() throws Exception {
        String token = register("lib_ver");
        String itemId = createItem(token, "single", "原始标题", opts("A", "B"));
        String v1Id = itemDetail(token, itemId).get("currentVersionId").asText();

        mockMvc.perform(put("/api/items/" + itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"修改后标题\",\"note\":\"改了标题\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        JsonNode detail = itemDetail(token, itemId);
        assertEquals(2, detail.get("currentVersion").asInt());
        assertEquals("修改后标题", detail.get("title").asText());

        String versions = mockMvc.perform(get("/api/items/" + itemId + "/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode v1 = objectMapper.readTree(versions).get(0);
        JsonNode v2 = objectMapper.readTree(versions).get(1);
        // 旧版本保持不变，版本链指向正确
        assertEquals(v1Id, v1.get("id").asText());
        assertEquals("原始标题", v1.get("title").asText());
        assertEquals(v1Id, v2.get("parentVersionId").asText());
        assertEquals("改了标题", v2.get("note").asText());
    }

    /** 只改标签不产生新版本 */
    @Test
    void tagsOnlyUpdateDoesNotCreateVersion() throws Exception {
        String token = register("lib_tags");
        String itemId = createItem(token, "text", "建议", null);

        mockMvc.perform(put("/api/items/" + itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[\"课程\",\"反馈\"]}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        assertEquals(1, itemDetail(token, itemId).get("currentVersion").asInt());
        mockMvc.perform(get("/api/items/" + itemId + "/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1));
    }

    /** 需求四：恢复旧版本 = 以旧版内容创建最新版本，历史链不回滚 */
    @Test
    void restoreCreatesNewVersionFromOld() throws Exception {
        String token = register("lib_restore");
        String itemId = createItem(token, "single", "第一版", opts("A", "B"));
        String v1Id = itemDetail(token, itemId).get("currentVersionId").asText();
        mockMvc.perform(put("/api/items/" + itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"第二版\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/items/" + itemId + "/restore")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":\"" + v1Id + "\"}"))
                .andExpect(status().isOk());

        JsonNode detail = itemDetail(token, itemId);
        assertEquals(3, detail.get("currentVersion").asInt());
        assertEquals("第一版", detail.get("title").asText());
        mockMvc.perform(get("/api/items/" + itemId + "/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(3));
    }

    /** 需求二：共享后其他用户可见可引用，未共享则 403 */
    @Test
    void shareVisibility() throws Exception {
        String owner = register("lib_owner");
        String other = register("lib_other");
        String itemId = createItem(owner, "single", "共享题", opts("X", "Y"));

        // 未共享：他人不可见
        mockMvc.perform(get("/api/items/" + itemId)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());

        // 共享
        mockMvc.perform(post("/api/items/" + itemId + "/share")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shared\":true}"))
                .andExpect(status().isOk());

        // 共享广场可见，且 mine=false
        mockMvc.perform(get("/api/items/shared")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mine").value(false));
        mockMvc.perform(get("/api/items/" + itemId)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isOk());

        // 他人不能修改/取消共享
        mockMvc.perform(put("/api/items/" + itemId)
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"篡改\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isForbidden());
    }

    /** 需求一：问卷里的新题保存时自动入题库 */
    @Test
    void surveySaveAutoCreatesItem() throws Exception {
        String token = register("lib_auto");
        String surveyId = createSurvey(token, List.of(single("q1", "自动入题库", true, "好", "差")));

        JsonNode q = getSurvey(token, surveyId).get("questions").get(0);
        assertNotNull(q.get("itemId").asText());
        assertEquals(1, q.get("version").asInt());
        // 题库里能看到这条题
        mockMvc.perform(get("/api/items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("自动入题库"));
    }

    /** 需求三/五：问卷引用 v1 发布后，题库改出 v2，问卷内容保持 v1 不变 */
    @Test
    void snapshotIsolationAfterItemUpdated() throws Exception {
        String token = register("lib_snap");
        String itemId = createItem(token, "single", "原始标题", opts("A", "B"));
        String surveyId = createSurvey(token, List.of(refQuestion("q1", itemDetail(token, itemId))));
        publish(token, surveyId);

        // 题库更新到 v2
        mockMvc.perform(put("/api/items/" + itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"title\":\"全新标题\",\"options\":[" +
                                "{\"oid\":\"o1\",\"text\":\"甲\"},{\"oid\":\"o2\",\"text\":\"乙\"},{\"oid\":\"o3\",\"text\":\"丙\"}]}").getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
        assertEquals(2, itemDetail(token, itemId).get("currentVersion").asInt());

        // 已发布问卷仍是 v1 内容（快照 + 引用版本不变）
        JsonNode q = getSurvey(token, surveyId).get("questions").get(0);
        assertEquals("原始标题", q.get("title").asText());
        assertEquals(1, q.get("version").asInt());
        assertEquals(2, q.get("options").size());
    }

    /** 需求五：草稿问卷中修改自己的题，保存后自动产生新版本并引用新版本 */
    @Test
    void editInSurveyCreatesNextVersion() throws Exception {
        String token = register("lib_edit");
        String itemId = createItem(token, "single", "旧标题", opts("A", "B"));
        Map<String, Object> q = refQuestion("q1", itemDetail(token, itemId));
        String surveyId = createSurvey(token, List.of(q));

        // 在问卷里改标题后保存
        q.put("title", "在问卷中改的新标题");
        mockMvc.perform(put("/api/surveys/" + surveyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("questions", List.of(q)))))
                .andExpect(status().isOk());

        // 题库当前版本推进到 v2，问卷引用 v2
        assertEquals(2, itemDetail(token, itemId).get("currentVersion").asInt());
        JsonNode saved = getSurvey(token, surveyId).get("questions").get(0);
        assertEquals(2, saved.get("version").asInt());
        assertEquals("在问卷中改的新标题", saved.get("title").asText());
    }

    /** 需求二/三：引用别人的共享题并修改，自动 fork 为自己的新条目，原题不变 */
    @Test
    void editSharedItemForks() throws Exception {
        String owner = register("lib_fork_o");
        String other = register("lib_fork_u");
        String itemId = createItem(owner, "single", "原题", opts("A", "B"));
        mockMvc.perform(post("/api/items/" + itemId + "/share")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shared\":true}"))
                .andExpect(status().isOk());

        Map<String, Object> q = refQuestion("q1", itemDetail(other, itemId));
        q.put("title", "我改造后的题");
        String surveyId = createSurvey(other, List.of(q));

        JsonNode saved = getSurvey(other, surveyId).get("questions").get(0);
        String forkItemId = saved.get("itemId").asText();
        // 不是原条目，原题仍是 v1，fork 条目记录来源
        assertTrue(!itemId.equals(forkItemId));
        assertEquals(1, itemDetail(owner, itemId).get("currentVersion").asInt());
        assertEquals("原题", itemDetail(owner, itemId).get("title").asText());
        JsonNode fork = itemDetail(other, forkItemId);
        assertEquals(itemId, fork.get("copiedFromItemId").asText());
        assertEquals("我改造后的题", fork.get("title").asText());
    }

    /** 需求七：被问卷引用的题目不能删除，未引用可删 */
    @Test
    void deleteReferencedItemRejected() throws Exception {
        String token = register("lib_del");
        String usedId = createItem(token, "single", "被引用", opts("A", "B"));
        createSurvey(token, List.of(refQuestion("q1", itemDetail(token, usedId))));
        mockMvc.perform(delete("/api/items/" + usedId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        String freeId = createItem(token, "text", "没人用", null);
        mockMvc.perform(delete("/api/items/" + freeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/items/" + freeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    /** 需求六：查看题目被哪些问卷使用 */
    @Test
    void usageListsSurveys() throws Exception {
        String token = register("lib_usage");
        String itemId = createItem(token, "single", "使用情况", opts("A", "B"));
        createSurvey(token, List.of(refQuestion("q1", itemDetail(token, itemId))));
        createSurvey(token, List.of(refQuestion("q9", itemDetail(token, itemId))));

        mockMvc.perform(get("/api/items/" + itemId + "/usage")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].surveyTitle").isNotEmpty());
    }

    /** 需求八：同一题目不同版本被两份问卷使用，跨问卷汇总统计 */
    @Test
    void crossSurveyStatsByVersion() throws Exception {
        String token = register("lib_stats");
        String itemId = createItem(token, "number", "你的年龄", null);

        // 卷1 引用 v1，收到回答 10
        String s1 = createSurvey(token, List.of(refQuestion("q1", itemDetail(token, itemId))));
        submitNumber(publish(token, s1), "q1", 10);

        // 题目更新到 v2（改标题），卷2 引用 v2，收到回答 20
        mockMvc.perform(put("/api/items/" + itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"你的年龄（新版）\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
        String s2 = createSurvey(token, List.of(refQuestion("q1", itemDetail(token, itemId))));
        submitNumber(publish(token, s2), "q1", 20);

        MvcResult res = mockMvc.perform(get("/api/items/" + itemId + "/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionCount").value(2))
                .andExpect(jsonPath("$.totalResponses").value(2))
                .andReturn();
        JsonNode stats = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode byVersion = stats.get("byVersion");
        assertEquals(1, byVersion.get(0).get("responseCount").asInt());
        assertEquals(1, byVersion.get(1).get("responseCount").asInt());
        assertEquals(1, byVersion.get(0).get("surveyCount").asInt());
        // 数字题跨版本总览：平均 15
        assertEquals(15.0, stats.get("overall").get("average").asDouble(), 0.001);
        assertEquals(2, stats.get("overall").get("total").asInt());
    }

    /** 迁移：schemaVersion=1 的旧问卷在迁移后回填题库引用 */
    @Test
    void migrationBackfillsReferences() throws Exception {
        Survey old = new Survey();
        old.setOwnerId("legacy-user");
        old.setTitle("旧版问卷");
        old.setSchemaVersion(1);
        old.setQuestions(List.of(single("q1", "旧题", true, "甲", "乙")));
        mongoTemplate.save(old);

        migrationRunner.run(null);

        Survey migrated = mongoTemplate.findById(old.getId(), Survey.class);
        assertEquals(Survey.SCHEMA_VERSION, migrated.getSchemaVersion());
        var q = migrated.getQuestions().get(0);
        assertNotNull(q.getItemId());
        assertNotNull(q.getVersionId());
        assertEquals(1, q.getVersion());
        // 题库中确有对应条目与 v1 版本，内容一致
        var item = mongoTemplate.findById(q.getItemId(), com.example.survey.model.question.QuestionItem.class);
        assertNotNull(item);
        assertEquals(q.getVersionId(), item.getCurrentVersionId());
        var version = mongoTemplate.findById(q.getVersionId(), com.example.survey.model.question.QuestionVersion.class);
        assertEquals("旧题", version.getTitle());
        // 幂等：再跑一次不会产生新版本
        migrationRunner.run(null);
        assertNull(mongoTemplate.findById(q.getVersionId(), com.example.survey.model.question.QuestionVersion.class).getParentVersionId());
    }
}


