package com.example.survey.service;

import com.example.survey.core.logic.LogicEngine;
import com.example.survey.core.validation.AnswerValidator;
import com.example.survey.dto.AnswerRequest;
import com.example.survey.dto.SaveAnswerResponse;
import com.example.survey.dto.StartFillResponse;
import com.example.survey.exception.ApiException;
import com.example.survey.model.response.Answer;
import com.example.survey.model.response.ResponseStatus;
import com.example.survey.model.response.SurveyResponse;
import com.example.survey.model.survey.Survey;
import com.example.survey.repository.SurveyResponseRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** 填写流程：开始/续填、逐题保存答案（草稿落库）、提交 */
@Service
public class ResponseService {

    private final SurveyResponseRepository responseRepository;
    private final AnswerValidator answerValidator;
    private final LogicEngine logicEngine;

    public ResponseService(SurveyResponseRepository responseRepository,
                           AnswerValidator answerValidator,
                           LogicEngine logicEngine) {
        this.responseRepository = responseRepository;
        this.answerValidator = answerValidator;
        this.logicEngine = logicEngine;
    }

    /**
     * 开始或继续填写。
     * 登录用户自动续上自己的草稿；匿名用户每次新建草稿（前端可靠 responseId 恢复进度）。
     */
    public StartFillResponse start(Survey survey, String uid) {
        if (!survey.getSettings().isAllowAnonymous() && uid == null) {
            throw ApiException.unauthorized("该问卷需要登录后填写");
        }
        if (uid != null
                && !survey.getSettings().isAllowMultiple()
                && responseRepository.countBySurveyIdAndRespondentIdAndStatus(
                survey.getId(), uid, ResponseStatus.SUBMITTED) > 0) {
            throw ApiException.conflict("您已提交过该问卷");
        }
        if (uid != null) {
            List<SurveyResponse> drafts = responseRepository.findBySurveyIdAndRespondentIdAndStatus(
                    survey.getId(), uid, ResponseStatus.DRAFT);
            if (!drafts.isEmpty()) {
                SurveyResponse draft = drafts.get(0);
                return new StartFillResponse(draft.getId(), draft.getCurrentQid(), true);
            }
        }
        SurveyResponse response = new SurveyResponse();
        response.setSurveyId(survey.getId());
        response.setRespondentId(uid);
        response.setCurrentQid(logicEngine.firstQuestion(survey));
        responseRepository.save(response);
        return new StartFillResponse(response.getId(), response.getCurrentQid(), false);
    }

    /** 取答卷并校验属于该问卷 */
    public SurveyResponse getResponse(Survey survey, String responseId) {
        return responseRepository.findByIdAndSurveyId(responseId, survey.getId())
                .orElseThrow(() -> ApiException.notFound("答卷不存在"));
    }

    /**
     * 保存单题答案（草稿落库），返回下一题。
     * 重新回答某题时，其后的答案全部作废，防止旧路径残留脏数据。
     */
    public SaveAnswerResponse saveAnswer(Survey survey, String responseId, AnswerRequest req) {
        SurveyResponse response = getResponse(survey, responseId);
        if (response.getStatus() != ResponseStatus.DRAFT) {
            throw ApiException.badRequest("答卷已提交，不能再修改");
        }
        Answer answer = req.toAnswer();
        answerValidator.validate(survey, req.qid(), answer);

        int answeredIndex = survey.indexOfQuestion(req.qid());
        response.getAnswers().removeIf(a -> survey.indexOfQuestion(a.getQid()) >= answeredIndex);
        response.getAnswers().add(answer);

        String nextQid = logicEngine.nextQuestion(survey, response, req.qid());
        response.setCurrentQid(nextQid);
        responseRepository.save(response);
        return new SaveAnswerResponse(nextQid, nextQid == null);
    }

    /**
     * 提交答卷：沿跳转路径从头走一遍，路径上每道题的答案都要通过校验（必答题未答会报错）。
     */
    public SurveyResponse submit(Survey survey, String responseId) {
        SurveyResponse response = getResponse(survey, responseId);
        if (response.getStatus() != ResponseStatus.DRAFT) {
            throw ApiException.badRequest("答卷已提交，请勿重复操作");
        }
        String qid = logicEngine.firstQuestion(survey);
        while (qid != null) {
            Answer answer = response.answerByQid(qid);
            answerValidator.validate(survey, qid, answer);
            qid = logicEngine.nextQuestion(survey, response, qid);
        }
        response.setStatus(ResponseStatus.SUBMITTED);
        response.setSubmittedAt(Instant.now());
        response.setCurrentQid(null);
        return responseRepository.save(response);
    }
}
