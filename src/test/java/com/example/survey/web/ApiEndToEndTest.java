package com.example.survey.web;

import com.example.survey.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 层端到端测试（MockMvc 走完整 Controller + 拦截器 + 内嵌 MongoDB），
 * 覆盖：未登录 401、注册登录、建卷、发布、分享链接填写、提交、统计。
 */
class ApiEndToEndTest extends BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    private String register(String username) throws Exception {
        String body = """
                {"username":"%s","password":"Test1234!"}""".formatted(username);
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }

    @Test
    void surveyListRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/surveys"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("请先登录"));
    }

    @Test
    void invalidTokenRejected() throws Exception {
        mockMvc.perform(get("/api/surveys").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerThenDuplicateIsConflict() throws Exception {
        register("api_user");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"api_user","password":"Test1234!"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        register("api_login");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"api_login","password":"Test1234!"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void registerWithShortPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"api_short","password":"123"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    /** 完整链路：注册 → 建卷 → 编辑 → 发布 → 分享填写（含跳转）→ 提交 → 统计 */
    @Test
    void fullHttpFlowFromRegisterToStats() throws Exception {
        String token = register("api_full");

        // 1. 创建问卷
        String created = mockMvc.perform(post("/api/surveys")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"接口流程问卷","description":"E2E"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String surveyId = objectMapper.readTree(created).get("id").asText();

        // 2. 设置题目与跳转规则：q1 选「男」跳到 q3
        Map<String, Object> rule = Map.of(
                "id", "r1",
                "when", Map.of("qid", "q1", "op", "eq", "value", "q1-o1"),
                "then", Map.of("action", "jumpTo", "targetQid", "q3"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questions", List.of(
                single("q1", "你的性别", true, "男", "女"),
                multiple("q2", "喜欢的水果", false, Map.of("maxSelect", 2), "苹果", "香蕉", "橙子"),
                text("q3", "你的建议", false, null),
                number("q4", "你的年龄", false, Map.of("integerOnly", true))));
        payload.put("logic", List.of(rule));
        mockMvc.perform(put("/api/surveys/" + surveyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // 3. 发布，拿到分享码
        String published = mockMvc.perform(post("/api/surveys/" + surveyId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andReturn().getResponse().getContentAsString();
        String shareCode = objectMapper.readTree(published).get("shareCode").asText();

        // 4. 分享链接公开信息（匿名可访问）
        mockMvc.perform(get("/api/s/" + shareCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("接口流程问卷"))
                .andExpect(jsonPath("$.questions.length()").value(4));

        // 5. 匿名开始填写
        String started = mockMvc.perform(post("/api/s/" + shareCode + "/responses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentQid").value("q1"))
                .andReturn().getResponse().getContentAsString();
        String responseId = objectMapper.readTree(started).get("responseId").asText();

        // 6. 答 q1（男）→ 应跳到 q3
        String step1 = mockMvc.perform(post("/api/s/" + shareCode + "/responses/" + responseId + "/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qid":"q1","optionIds":["q1-o1"]}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode step1Node = objectMapper.readTree(step1);
        assertEquals("q3", step1Node.get("nextQid").asText());
        assertFalse(step1Node.get("done").asBoolean());

        // 7. 答 q3（文本）
        mockMvc.perform(post("/api/s/" + shareCode + "/responses/" + responseId + "/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qid":"q3","text":"多些实践课"}"""))
                .andExpect(status().isOk());

        // 8. 答 q4（整数 22）→ done
        mockMvc.perform(post("/api/s/" + shareCode + "/responses/" + responseId + "/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qid":"q4","number":22}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true));

        // 9. 提交
        mockMvc.perform(post("/api/s/" + shareCode + "/responses/" + responseId + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ok"));

        // 10. 重复提交被拒
        mockMvc.perform(post("/api/s/" + shareCode + "/responses/" + responseId + "/submit"))
                .andExpect(status().isBadRequest());

        // 11. 统计：1 份答卷，q1「男」1 票
        String stats = mockMvc.perform(get("/api/surveys/" + surveyId + "/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCount").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode q1 = findQuestion(objectMapper.readTree(stats), "q1");
        assertEquals(1, q1.get("summary").get("total").asInt());
        assertTrue(q1.get("summary").get("options").toString().contains("\"count\":1"));

        // 12. 他人无法查看该问卷统计
        String otherToken = register("api_other");
        mockMvc.perform(get("/api/surveys/" + surveyId + "/stats")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void constraintViolationReturnsBadRequest() throws Exception {
        String token = register("api_constraint");
        String created = mockMvc.perform(post("/api/surveys")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"约束问卷"}"""))
                .andReturn().getResponse().getContentAsString();
        String surveyId = objectMapper.readTree(created).get("id").asText();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questions", List.of(multiple("q1", "水果", true, Map.of("maxSelect", 2), "A", "B", "C")));
        mockMvc.perform(put("/api/surveys/" + surveyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
        String published = mockMvc.perform(post("/api/surveys/" + surveyId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        String shareCode = objectMapper.readTree(published).get("shareCode").asText();

        String started = mockMvc.perform(post("/api/s/" + shareCode + "/responses"))
                .andReturn().getResponse().getContentAsString();
        String responseId = objectMapper.readTree(started).get("responseId").asText();

        mockMvc.perform(post("/api/s/" + shareCode + "/responses/" + responseId + "/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qid":"q1","optionIds":["q1-o1","q1-o2","q1-o3"]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("最多选择")));
    }

    @Test
    void closedSurveyCannotBeFilled() throws Exception {
        String token = register("api_closed");
        String created = mockMvc.perform(post("/api/surveys")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"要关闭的问卷"}"""))
                .andReturn().getResponse().getContentAsString();
        String surveyId = objectMapper.readTree(created).get("id").asText();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questions", List.of(text("q1", "随便说说", false, null)));
        mockMvc.perform(put("/api/surveys/" + surveyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
        String published = mockMvc.perform(post("/api/surveys/" + surveyId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        String shareCode = objectMapper.readTree(published).get("shareCode").asText();

        mockMvc.perform(post("/api/surveys/" + surveyId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        // 关闭后：填写入口 400，开始填写同样失败
        mockMvc.perform(get("/api/s/" + shareCode))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/s/" + shareCode + "/responses"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode findQuestion(JsonNode stats, String qid) {
        for (JsonNode q : stats.get("questions")) {
            if (qid.equals(q.get("qid").asText())) {
                return q;
            }
        }
        throw new IllegalStateException("统计中找不到题目 " + qid);
    }
}
