package com.example.survey.core.logic;

import com.example.survey.exception.ApiException;
import com.example.survey.model.response.Answer;
import com.example.survey.model.response.SurveyResponse;
import com.example.survey.model.survey.LogicRule;
import com.example.survey.model.survey.Survey;
import org.springframework.stereotype.Component;

/**
 * 跳转逻辑引擎：根据规则数据决定下一题，规则全部来自问卷文档，不写死。
 * 阶段一约定：只能向后跳（目标题位置必须大于条件题位置），因此天然无环。
 */
@Component
public class LogicEngine {

    public static final String ACTION_JUMP_TO = "jumpTo";
    public static final String ACTION_END = "end";

    private final ConditionOperators operators;

    public LogicEngine(ConditionOperators operators) {
        this.operators = operators;
    }

    /** 第一题的 qid，无题返回 null */
    public String firstQuestion(Survey survey) {
        if (survey.getQuestions() == null || survey.getQuestions().isEmpty()) {
            return null;
        }
        return survey.getQuestions().get(0).getQid();
    }

    /**
     * 答完 justAnsweredQid 后，计算下一题 qid。
     * 按规则顺序匹配第一条 when.qid == justAnsweredQid 且条件成立的规则：
     * jumpTo 返回目标题，end 返回 null（问卷结束）。
     * 无命中规则则按题目数组顺序取下一题，已是最后一题返回 null。
     */
    public String nextQuestion(Survey survey, SurveyResponse response, String justAnsweredQid) {
        if (survey.getLogic() != null) {
            Answer answer = response.answerByQid(justAnsweredQid);
            for (LogicRule rule : survey.getLogic()) {
                if (rule.getWhen() == null || !justAnsweredQid.equals(rule.getWhen().getQid())) {
                    continue;
                }
                if (operators.test(rule.getWhen().getOp(), answer, rule.getWhen().getValue())) {
                    if (ACTION_END.equals(rule.getThen().getAction())) {
                        return null;
                    }
                    return rule.getThen().getTargetQid();
                }
            }
        }
        int index = survey.indexOfQuestion(justAnsweredQid);
        if (index < 0 || index + 1 >= survey.getQuestions().size()) {
            return null;
        }
        return survey.getQuestions().get(index + 1).getQid();
    }

    /** 保存问卷时校验全部规则的合法性 */
    public void validateRules(Survey survey) {
        if (survey.getLogic() == null) {
            return;
        }
        for (LogicRule rule : survey.getLogic()) {
            if (rule.getId() == null || rule.getId().isBlank()) {
                throw ApiException.badRequest("跳转规则缺少 id");
            }
            if (rule.getWhen() == null || rule.getThen() == null) {
                throw ApiException.badRequest("跳转规则 " + rule.getId() + " 缺少 when/then");
            }
            String whenQid = rule.getWhen().getQid();
            if (survey.questionByQid(whenQid) == null) {
                throw ApiException.badRequest("跳转规则 " + rule.getId() + " 的条件题目不存在：" + whenQid);
            }
            if (!operators.exists(rule.getWhen().getOp())) {
                throw ApiException.badRequest("跳转规则 " + rule.getId() + " 使用了未知算子：" + rule.getWhen().getOp());
            }
            String action = rule.getThen().getAction();
            if (ACTION_END.equals(action)) {
                continue;
            }
            if (!ACTION_JUMP_TO.equals(action)) {
                throw ApiException.badRequest("跳转规则 " + rule.getId() + " 使用了未知动作：" + action);
            }
            String targetQid = rule.getThen().getTargetQid();
            if (survey.questionByQid(targetQid) == null) {
                throw ApiException.badRequest("跳转规则 " + rule.getId() + " 的目标题目不存在：" + targetQid);
            }
            if (survey.indexOfQuestion(targetQid) <= survey.indexOfQuestion(whenQid)) {
                throw ApiException.badRequest("跳转规则 " + rule.getId() + " 只能向后跳转");
            }
        }
    }
}
