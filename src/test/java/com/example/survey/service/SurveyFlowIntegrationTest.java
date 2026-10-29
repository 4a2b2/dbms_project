package com.example.survey.service;

import com.example.survey.BaseIntegrationTest;
import com.example.survey.dto.AnswerRequest;
import com.example.survey.dto.CreateSurveyRequest;
import com.example.survey.dto.LoginRequest;
import com.example.survey.dto.RegisterRequest;
import com.example.survey.dto.SaveAnswerResponse;
import com.example.survey.dto.StartFillResponse;
import com.example.survey.dto.UpdateSurveyRequest;
import com.example.survey.exception.ApiException;
import com.example.survey.model.response.ResponseStatus;
import com.example.survey.model.response.SurveyResponse;
import com.example.survey.model.survey.Action;
import com.example.survey.model.survey.Condition;
import com.example.survey.model.survey.LogicRule;
import com.example.survey.model.survey.Question;
import com.example.survey.model.survey.Settings;
import com.example.survey.model.survey.Survey;
import com.example.survey.model.survey.SurveyStatus;
import com.example.survey.repository.SurveyRepository;
import com.example.survey.repository.SurveyResponseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段一核心业务流程集成测试（真实 Spring 上下文 + 内嵌 MongoDB）：
 * 注册登录、问卷 CRUD/发布/关闭/删除、四种题型结构校验、跳转逻辑、逐题作答约束、
 * 提交流程与统计聚合。
 */
class SurveyFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    AuthService authService;
    @Autowired
    SurveyService surveyService;
    @Autowired
    ResponseService responseService;
    @Autowired
    StatsService statsService;
    @Autowired
    SurveyRepository surveyRepository;
    @Autowired
    SurveyResponseRepository responseRepository;

    /* ========== 认证 ========== */

    @Test
    void registerThenLoginSuccessfully() {
        String uid = authService.register(new RegisterRequest("alice", "Test1234!")).userId();
        assertNotNull(uid);

        var auth = authService.login(new LoginRequest("alice", "Test1234!"));
        assertEquals(uid, auth.userId());
        assertTrue(auth.token().length() > 20);
    }

    @Test
    void registerDuplicateUsernameRejected() {
        authService.register(new RegisterRequest("bob", "Test1234!"));
        ApiException e = assertThrows(ApiException.class,
                () -> authService.register(new RegisterRequest("bob", "Test1234!")));
        assertTrue(e.getMessage().contains("已被注册"));
    }

    @Test
    void loginWithWrongPasswordRejected() {
        authService.register(new RegisterRequest("carol", "Test1234!"));
        assertThrows(ApiException.class, () -> authService.login(new LoginRequest("carol", "wrong-pass")));
    }

    /* ========== 问卷结构 ========== */

    /** 建一份含四种题型 + 一条跳转规则（q1 选「男」跳到 q3）的完整问卷 */
    private Survey buildFullSurvey(String ownerId) {
        Survey survey = surveyService.create(ownerId, new CreateSurveyRequest("功能测试问卷", "描述"));
        List<Question> questions = List.of(
                single("q1", "你的性别", true, "男", "女"),
                multiple("q2", "喜欢的水果", false, Map.of("maxSelect", 2), "苹果", "香蕉", "橙子"),
                text("q3", "你的建议", false, null),
                number("q4", "你的年龄", false, Map.of("integerOnly", true)));
        LogicRule rule = new LogicRule();
        Condition when = new Condition();
        when.setQid("q1");
        when.setOp("eq");
        when.setValue(oid("q1", 1)); // 男
        Action then = new Action();
        then.setAction(LogicEngineAction.JUMP_TO);
        then.setTargetQid("q3");
        rule.setId("r1");
        rule.setWhen(when);
        rule.setThen(then);
        return surveyService.update(ownerId, survey.getId(),
                new UpdateSurveyRequest(null, null, null, questions, List.of(rule)));
    }

    /** 常量引用（避免测试中拼错字符串） */
    private static final class LogicEngineAction {
        static final String JUMP_TO = "jumpTo";
    }

    @Test
    void createdSurveyIsDraftAndOwned() {
        String uid = authService.register(new RegisterRequest("dave", "Test1234!")).userId();
        Survey survey = surveyService.create(uid, new CreateSurveyRequest("新问卷", null));
        assertEquals(SurveyStatus.DRAFT, survey.getStatus());
        assertEquals(uid, survey.getOwnerId());
        assertNull(survey.getShareCode());
    }

    @Test
    void foreignSurveyAccessForbidden() {
        String owner = authService.register(new RegisterRequest("owner1", "Test1234!")).userId();
        String other = authService.register(new RegisterRequest("other1", "Test1234!")).userId();
        Survey survey = surveyService.create(owner, new CreateSurveyRequest("私有问卷", null));
        ApiException e = assertThrows(ApiException.class, () -> surveyService.getOwned(other, survey.getId()));
        assertTrue(e.getMessage().contains("无权访问"));
    }

    @Test
    void publishingWithoutQuestionsRejected() {
        String uid = authService.register(new RegisterRequest("erin", "Test1234!")).userId();
        Survey survey = surveyService.create(uid, new CreateSurveyRequest("空问卷", null));
        ApiException e = assertThrows(ApiException.class, () -> surveyService.publish(uid, survey.getId()));
        assertTrue(e.getMessage().contains("至少需要一道题"));
    }

    @Test
    void invalidMultipleConstraintRejected() {
        String uid = authService.register(new RegisterRequest("frank", "Test1234!")).userId();
        Survey survey = surveyService.create(uid, new CreateSurveyRequest("约束不合法", null));
        // maxSelect=5 超过选项数 2
        List<Question> questions = List.of(
                multiple("q1", "多选", true, Map.of("maxSelect", 5), "A", "B"));
        ApiException e = assertThrows(ApiException.class, () -> surveyService.update(uid, survey.getId(),
                new UpdateSurveyRequest(null, null, null, questions, null)));
        assertTrue(e.getMessage().contains("maxSelect"));
    }

    @Test
    void duplicateQidRejected() {
        String uid = authService.register(new RegisterRequest("grace", "Test1234!")).userId();
        Survey survey = surveyService.create(uid, new CreateSurveyRequest("重复 qid", null));
        List<Question> questions = List.of(text("q1", "题一", false, null), text("q1", "题二", false, null));
        assertThrows(ApiException.class, () -> surveyService.update(uid, survey.getId(),
                new UpdateSurveyRequest(null, null, null, questions, null)));
    }

    @Test
    void publishGeneratesShareCodeAndLocksStructure() {
        String uid = authService.register(new RegisterRequest("henry", "Test1234!")).userId();
        Survey survey = buildFullSurvey(uid);

        Survey published = surveyService.publish(uid, survey.getId());
        assertEquals(SurveyStatus.PUBLISHED, published.getStatus());
        assertEquals(8, published.getShareCode().length());

        // 已发布后不能再改题目结构
        List<Question> newQuestions = List.of(text("q9", "新题", false, null));
        ApiException e = assertThrows(ApiException.class, () -> surveyService.update(uid, survey.getId(),
                new UpdateSurveyRequest(null, null, null, newQuestions, null)));
        assertTrue(e.getMessage().contains("不可修改"));

        // 重复发布被拒
        assertThrows(ApiException.class, () -> surveyService.publish(uid, survey.getId()));
    }

    @Test
    void closeRequiresPublishedState() {
        String uid = authService.register(new RegisterRequest("ivan", "Test1234!")).userId();
        Survey survey = buildFullSurvey(uid);
        assertThrows(ApiException.class, () -> surveyService.close(uid, survey.getId()));

        surveyService.publish(uid, survey.getId());
        Survey closed = surveyService.close(uid, survey.getId());
        assertEquals(SurveyStatus.CLOSED, closed.getStatus());
    }

    @Test
    void fillEntryRejectsClosedSurvey() {
        String uid = authService.register(new RegisterRequest("judy", "Test1234!")).userId();
        Survey survey = surveyService.publish(uid, buildFullSurvey(uid).getId());
        surveyService.close(uid, survey.getId());
        ApiException e = assertThrows(ApiException.class, () -> surveyService.getForFill(survey.getShareCode()));
        assertTrue(e.getMessage().contains("已关闭"));
    }

    @Test
    void deleteCascadesResponses() {
        String uid = authService.register(new RegisterRequest("kate", "Test1234!")).userId();
        Survey survey = surveyService.publish(uid, buildFullSurvey(uid).getId());
        SurveyResponse response = new SurveyResponse();
        response.setSurveyId(survey.getId());
        response.setStatus(ResponseStatus.SUBMITTED);
        responseRepository.save(response);

        surveyService.delete(uid, survey.getId());
        assertTrue(surveyRepository.findById(survey.getId()).isEmpty());
        assertTrue(responseRepository.findBySurveyIdAndStatus(survey.getId(), ResponseStatus.SUBMITTED).isEmpty());
    }

    /* ========== 填写流程 ========== */

    private Survey publishedFullSurvey(String uid) {
        return surveyService.publish(uid, buildFullSurvey(uid).getId());
    }

    @Test
    void anonymousFillFollowsJumpRuleAndSubmits() {
        String uid = authService.register(new RegisterRequest("luke", "Test1234!")).userId();
        Survey survey = publishedFullSurvey(uid);

        StartFillResponse start = responseService.start(survey, null);
        assertEquals("q1", start.currentQid());

        // q1 选「男」→ 规则命中 → 跳到 q3
        SaveAnswerResponse step1 = responseService.saveAnswer(survey, start.responseId(),
                new AnswerRequest("q1", List.of(oid("q1", 1)), null, null));
        assertEquals("q3", step1.nextQid());

        // q3 文本 → 顺序下一题 q4
        SaveAnswerResponse step2 = responseService.saveAnswer(survey, start.responseId(),
                new AnswerRequest("q3", null, "希望多些实践课", null));
        assertEquals("q4", step2.nextQid());

        // q4 数字 → 最后一道，done
        SaveAnswerResponse step3 = responseService.saveAnswer(survey, start.responseId(),
                new AnswerRequest("q4", null, null, 22.0));
        assertNull(step3.nextQid());
        assertTrue(step3.done());

        SurveyResponse submitted = responseService.submit(survey, start.responseId());
        assertEquals(ResponseStatus.SUBMITTED, submitted.getStatus());
        assertNotNull(submitted.getSubmittedAt());
        // 跳转跳过了 q2，其答案不应存在
        assertNull(submitted.answerByQid("q2"));
    }

    @Test
    void answeringEarlierQuestionDiscardsLaterAnswers() {
        String uid = authService.register(new RegisterRequest("mona", "Test1234!")).userId();
        Survey survey = publishedFullSurvey(uid);
        StartFillResponse start = responseService.start(survey, null);

        responseService.saveAnswer(survey, start.responseId(),
                new AnswerRequest("q1", List.of(oid("q1", 1)), null, null));
        responseService.saveAnswer(survey, start.responseId(),
                new AnswerRequest("q3", null, "旧建议", null));

        // 回头改选「女」→ 规则不命中 → 下一题变回顺序 q2，且 q3/q4 旧答案被清除
        SaveAnswerResponse again = responseService.saveAnswer(survey, start.responseId(),
                new AnswerRequest("q1", List.of(oid("q1", 2)), null, null));
        assertEquals("q2", again.nextQid());
        SurveyResponse current = responseService.getResponse(survey, start.responseId());
        assertNull(current.answerByQid("q3"));
        assertNull(current.answerByQid("q4"));
    }

    @Test
    void maxSelectConstraintEnforced() {
        String uid = authService.register(new RegisterRequest("nina", "Test1234!")).userId();
        Survey survey = publishedFullSurvey(uid);
        StartFillResponse start = responseService.start(survey, null);

        // 先走 q1（选女）到 q2
        responseService.saveAnswer(survey, start.responseId(),
                new AnswerRequest("q1", List.of(oid("q1", 2)), null, null));

        // q2 最多选 2 项，这里选 3 项
        ApiException e = assertThrows(ApiException.class, () -> responseService.saveAnswer(survey, start.responseId(),
                new AnswerRequest("q2", List.of(oid("q2", 1), oid("q2", 2), oid("q2", 3)), null, null)));
        assertTrue(e.getMessage().contains("最多选择"));
    }

    @Test
    void integerOnlyConstraintEnforced() {
        String uid = authService.register(new RegisterRequest("oscar", "Test1234!")).userId();
        Survey survey = publishedFullSurvey(uid);
        StartFillResponse start = responseService.start(survey, null);
        responseService.saveAnswer(survey, start.responseId(),
                new AnswerRequest("q1", List.of(oid("q1", 2)), null, null));
        responseService.saveAnswer(survey, start.responseId(),
                new AnswerRequest("q2", List.of(oid("q2", 1)), null, null));
        responseService.saveAnswer(survey, start.responseId(),
                new AnswerRequest("q3", null, "建议", null));

        ApiException e = assertThrows(ApiException.class, () -> responseService.saveAnswer(survey, start.responseId(),
                new AnswerRequest("q4", null, null, 20.5)));
        assertTrue(e.getMessage().contains("只接受整数"));
    }

    @Test
    void submitRejectsMissingRequiredAnswer() {
        String uid = authService.register(new RegisterRequest("peter", "Test1234!")).userId();
        Survey survey = publishedFullSurvey(uid);
        StartFillResponse start = responseService.start(survey, null);
        // 必答题 q1 未作答
        ApiException e = assertThrows(ApiException.class, () -> responseService.submit(survey, start.responseId()));
        assertTrue(e.getMessage().contains("必答题"));
    }

    @Test
    void submitTwiceRejected() {
        String uid = authService.register(new RegisterRequest("quinn", "Test1234!")).userId();
        Survey survey = publishedFullSurvey(uid);
        StartFillResponse start = responseService.start(survey, null);
        responseService.saveAnswer(survey, start.responseId(),
                new AnswerRequest("q1", List.of(oid("q1", 2)), null, null));
        responseService.submit(survey, start.responseId());
        assertThrows(ApiException.class, () -> responseService.submit(survey, start.responseId()));
    }

    /** 新规则：整卷没有必答题时保存草稿正常，但发布被拒绝 */
    @Test
    void publishRejectedWhenNoRequiredQuestion() {
        String uid = authService.register(new RegisterRequest("noreq", "Test1234!")).userId();
        Survey survey = surveyService.create(uid, new CreateSurveyRequest("全选答问卷", null));
        // 保存草稿（全部为选答题）正常
        surveyService.update(uid, survey.getId(), new UpdateSurveyRequest(null, null, null,
                List.of(text("q1", "选答题一", false, null),
                        text("q2", "选答题二", false, null)), null));
        // 发布被拒绝
        ApiException e = assertThrows(ApiException.class, () -> surveyService.publish(uid, survey.getId()));
        assertTrue(e.getMessage().contains("没有必答题"));
        // 把其中一题改为必答后即可发布
        surveyService.update(uid, survey.getId(), new UpdateSurveyRequest(null, null, null,
                List.of(text("q1", "选答题一", true, null)), null));
        assertEquals(SurveyStatus.PUBLISHED, surveyService.publish(uid, survey.getId()).getStatus());
    }

    @Test
    void anonymousNotAllowedRequiresLogin() {
        String uid = authService.register(new RegisterRequest("rita", "Test1234!")).userId();
        Survey survey = surveyService.create(uid, new CreateSurveyRequest("需登录", null));
        Settings settings = new Settings();
        settings.setAllowAnonymous(false);
        surveyService.update(uid, survey.getId(), new UpdateSurveyRequest(null, null, settings,
                List.of(text("q1", "题目", true, null)), null));
        Survey published = surveyService.publish(uid, survey.getId());

        ApiException e = assertThrows(ApiException.class, () -> responseService.start(published, null));
        assertTrue(e.getMessage().contains("需要登录"));
    }

    @Test
    void allowMultipleFalseBlocksSecondSubmission() {
        String uid = authService.register(new RegisterRequest("sam", "Test1234!")).userId();
        Survey survey = surveyService.create(uid, new CreateSurveyRequest("仅一次", null));
        Settings settings = new Settings();
        settings.setAllowAnonymous(true);
        settings.setAllowMultiple(false);
        surveyService.update(uid, survey.getId(), new UpdateSurveyRequest(null, null, settings,
                List.of(text("q1", "题目", true, null)), null));
        Survey published = surveyService.publish(uid, survey.getId());

        StartFillResponse first = responseService.start(published, uid);
        responseService.saveAnswer(published, first.responseId(),
                new AnswerRequest("q1", null, "第一次", null));
        responseService.submit(published, first.responseId());

        ApiException e = assertThrows(ApiException.class, () -> responseService.start(published, uid));
        assertTrue(e.getMessage().contains("已提交过"));
    }

    /* ========== 统计 ========== */

    @Test
    void statsAggregatesAllQuestionTypes() {
        String uid = authService.register(new RegisterRequest("tina", "Test1234!")).userId();
        Survey survey = publishedFullSurvey(uid);

        // 第一份：男 / 单选水果2项 / 建议 / 年龄 20
        StartFillResponse a = responseService.start(survey, null);
        responseService.saveAnswer(survey, a.responseId(), new AnswerRequest("q1", List.of(oid("q1", 1)), null, null));
        responseService.saveAnswer(survey, a.responseId(), new AnswerRequest("q3", null, "建议A", null));
        responseService.saveAnswer(survey, a.responseId(), new AnswerRequest("q4", null, null, 20.0));
        responseService.submit(survey, a.responseId());

        // 第二份：女 / 走顺序含多选 / 建议 / 年龄 30
        StartFillResponse b = responseService.start(survey, null);
        responseService.saveAnswer(survey, b.responseId(), new AnswerRequest("q1", List.of(oid("q1", 2)), null, null));
        responseService.saveAnswer(survey, b.responseId(),
                new AnswerRequest("q2", List.of(oid("q2", 1), oid("q2", 3)), null, null));
        responseService.saveAnswer(survey, b.responseId(), new AnswerRequest("q3", null, "建议B", null));
        responseService.saveAnswer(survey, b.responseId(), new AnswerRequest("q4", null, null, 30.0));
        responseService.submit(survey, b.responseId());

        Map<String, Object> stats = statsService.stats(survey);
        assertEquals(2, stats.get("responseCount"));

        // 单选题 q1：男 1 票、女 1 票
        Map<String, Object> single = summaryOf(stats, "q1");
        assertEquals(2, single.get("total"));
        assertEquals(1, countOfOption(single, oid("q1", 1)));
        assertEquals(1, countOfOption(single, oid("q1", 2)));

        // 多选题 q2：只有第二份作答了 2 项
        Map<String, Object> multiple = summaryOf(stats, "q2");
        assertEquals(1, multiple.get("total"));
        assertEquals(1, countOfOption(multiple, oid("q2", 1)));
        assertEquals(0, countOfOption(multiple, oid("q2", 2)));
        assertEquals(1, countOfOption(multiple, oid("q2", 3)));

        // 文本题 q3：两条文本
        Map<String, Object> text = summaryOf(stats, "q3");
        assertEquals(2, text.get("total"));
        assertEquals(List.of("建议A", "建议B"), text.get("texts"));

        // 数字题 q4：平均 25.0，最小 20，最大 30
        Map<String, Object> number = summaryOf(stats, "q4");
        assertEquals(2, number.get("total"));
        assertEquals(25.0, ((Number) number.get("average")).doubleValue(), 0.001);
        assertEquals(20.0, ((Number) number.get("min")).doubleValue(), 0.001);
        assertEquals(30.0, ((Number) number.get("max")).doubleValue(), 0.001);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> summaryOf(Map<String, Object> stats, String qid) {
        List<Map<String, Object>> questions = (List<Map<String, Object>>) stats.get("questions");
        return questions.stream()
                .filter(q -> qid.equals(q.get("qid")))
                .map(q -> (Map<String, Object>) q.get("summary"))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private int countOfOption(Map<String, Object> summary, String optionOid) {
        List<Map<String, Object>> options = (List<Map<String, Object>>) summary.get("options");
        return options.stream()
                .filter(o -> optionOid.equals(o.get("oid")))
                .map(o -> ((Number) o.get("count")).intValue())
                .findFirst()
                .orElseThrow();
    }
}
