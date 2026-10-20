package com.example.survey.core.logic;

import com.example.survey.exception.ApiException;
import com.example.survey.model.response.Answer;
import com.example.survey.model.response.SurveyResponse;
import com.example.survey.model.survey.Action;
import com.example.survey.model.survey.Condition;
import com.example.survey.model.survey.LogicRule;
import com.example.survey.model.survey.Question;
import com.example.survey.model.survey.Survey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 跳转逻辑引擎单元测试（不依赖 Spring / MongoDB） */
class LogicEngineTest {

    private final LogicEngine engine = new LogicEngine(new ConditionOperators());

    private Survey surveyWith(String... qids) {
        Survey survey = new Survey();
        List<Question> questions = new ArrayList<>();
        for (String qid : qids) {
            Question q = new Question();
            q.setQid(qid);
            q.setTitle("题 " + qid);
            q.setType("text");
            questions.add(q);
        }
        survey.setQuestions(questions);
        return survey;
    }

    private SurveyResponse responseWithOption(String qid, String oid) {
        SurveyResponse response = new SurveyResponse();
        Answer answer = new Answer();
        answer.setQid(qid);
        answer.setOptionIds(List.of(oid));
        response.getAnswers().add(answer);
        return response;
    }

    private LogicRule rule(String id, String whenQid, String op, Object value, String action, String targetQid) {
        Condition when = new Condition();
        when.setQid(whenQid);
        when.setOp(op);
        when.setValue(value);
        Action then = new Action();
        then.setAction(action);
        then.setTargetQid(targetQid);
        LogicRule rule = new LogicRule();
        rule.setId(id);
        rule.setWhen(when);
        rule.setThen(then);
        return rule;
    }

    @Test
    void firstQuestionReturnsFirstQid() {
        assertEquals("q1", engine.firstQuestion(surveyWith("q1", "q2")));
    }

    @Test
    void firstQuestionReturnsNullWhenEmpty() {
        assertNull(engine.firstQuestion(surveyWith()));
    }

    @Test
    void nextQuestionGoesSequentialWithoutRules() {
        Survey survey = surveyWith("q1", "q2", "q3");
        SurveyResponse response = new SurveyResponse();
        assertEquals("q2", engine.nextQuestion(survey, response, "q1"));
        assertEquals("q3", engine.nextQuestion(survey, response, "q2"));
        assertNull(engine.nextQuestion(survey, response, "q3"));
    }

    @Test
    void matchingJumpRuleSkipsToTarget() {
        Survey survey = surveyWith("q1", "q2", "q3");
        survey.setLogic(List.of(rule("r1", "q1", "eq", "a", LogicEngine.ACTION_JUMP_TO, "q3")));
        SurveyResponse response = responseWithOption("q1", "a");
        assertEquals("q3", engine.nextQuestion(survey, response, "q1"));
    }

    @Test
    void unmatchedRuleFallsBackToSequential() {
        Survey survey = surveyWith("q1", "q2", "q3");
        survey.setLogic(List.of(rule("r1", "q1", "eq", "a", LogicEngine.ACTION_JUMP_TO, "q3")));
        SurveyResponse response = responseWithOption("q1", "b");
        assertEquals("q2", engine.nextQuestion(survey, response, "q1"));
    }

    @Test
    void endActionTerminatesSurvey() {
        Survey survey = surveyWith("q1", "q2");
        survey.setLogic(List.of(rule("r1", "q1", "eq", "a", LogicEngine.ACTION_END, null)));
        SurveyResponse response = responseWithOption("q1", "a");
        assertNull(engine.nextQuestion(survey, response, "q1"));
    }

    @Test
    void firstMatchingRuleWins() {
        Survey survey = surveyWith("q1", "q2", "q3", "q4");
        survey.setLogic(List.of(
                rule("r1", "q1", "eq", "a", LogicEngine.ACTION_JUMP_TO, "q3"),
                rule("r2", "q1", "eq", "a", LogicEngine.ACTION_JUMP_TO, "q4")));
        SurveyResponse response = responseWithOption("q1", "a");
        assertEquals("q3", engine.nextQuestion(survey, response, "q1"));
    }

    @Test
    void validateRulesAcceptsBackwardJump() {
        Survey survey = surveyWith("q1", "q2", "q3");
        survey.setLogic(List.of(rule("r1", "q1", "eq", "a", LogicEngine.ACTION_JUMP_TO, "q3")));
        engine.validateRules(survey); // 不抛异常即通过
    }

    @Test
    void validateRulesRejectsForwardJump() {
        Survey survey = surveyWith("q1", "q2", "q3");
        survey.setLogic(List.of(rule("r1", "q3", "eq", "a", LogicEngine.ACTION_JUMP_TO, "q1")));
        ApiException e = assertThrows(ApiException.class, () -> engine.validateRules(survey));
        assertTrue(e.getMessage().contains("只能向后跳转"));
    }

    @Test
    void validateRulesRejectsUnknownOperator() {
        Survey survey = surveyWith("q1", "q2");
        survey.setLogic(List.of(rule("r1", "q1", "noop", "a", LogicEngine.ACTION_JUMP_TO, "q2")));
        assertThrows(ApiException.class, () -> engine.validateRules(survey));
    }

    @Test
    void validateRulesRejectsMissingTarget() {
        Survey survey = surveyWith("q1", "q2");
        survey.setLogic(List.of(rule("r1", "q1", "eq", "a", LogicEngine.ACTION_JUMP_TO, "qx")));
        assertThrows(ApiException.class, () -> engine.validateRules(survey));
    }
}
