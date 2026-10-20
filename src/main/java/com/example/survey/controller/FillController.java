package com.example.survey.controller;

import com.example.survey.dto.AnswerRequest;
import com.example.survey.dto.PublicSurveyView;
import com.example.survey.dto.ResponseStateView;
import com.example.survey.dto.SaveAnswerResponse;
import com.example.survey.dto.StartFillResponse;
import com.example.survey.model.response.SurveyResponse;
import com.example.survey.model.survey.Survey;
import com.example.survey.security.Auth;
import com.example.survey.service.ResponseService;
import com.example.survey.service.SurveyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 填写端接口（分享链接进入，匿名可填）。
 * 路径前缀 /api/s/{shareCode}，与管理端 /api/surveys 分开。
 */
@RestController
@RequestMapping("/api/s/{shareCode}")
public class FillController {

    private final SurveyService surveyService;
    private final ResponseService responseService;

    public FillController(SurveyService surveyService, ResponseService responseService) {
        this.surveyService = surveyService;
        this.responseService = responseService;
    }

    /** 问卷公开信息（题目、题型、约束，供前端渲染） */
    @GetMapping
    public PublicSurveyView view(@PathVariable String shareCode) {
        return PublicSurveyView.from(surveyService.getForFill(shareCode));
    }

    /** 开始/继续填写，返回答卷 id 与当前题 */
    @PostMapping("/responses")
    public StartFillResponse start(@PathVariable String shareCode, HttpServletRequest request) {
        Survey survey = surveyService.getForFill(shareCode);
        return responseService.start(survey, Auth.uid(request));
    }

    /** 答卷进度（凭 responseId 续填） */
    @GetMapping("/responses/{responseId}")
    public ResponseStateView state(@PathVariable String shareCode, @PathVariable String responseId) {
        Survey survey = surveyService.getForFill(shareCode);
        return ResponseStateView.from(responseService.getResponse(survey, responseId));
    }

    /** 保存单题答案（草稿落库），返回下一题 qid */
    @PostMapping("/responses/{responseId}/answers")
    public SaveAnswerResponse saveAnswer(@PathVariable String shareCode,
                                         @PathVariable String responseId,
                                         @Valid @RequestBody AnswerRequest req) {
        Survey survey = surveyService.getForFill(shareCode);
        return responseService.saveAnswer(survey, responseId, req);
    }

    /** 提交答卷 */
    @PostMapping("/responses/{responseId}/submit")
    public Map<String, String> submit(@PathVariable String shareCode, @PathVariable String responseId) {
        Survey survey = surveyService.getForFill(shareCode);
        SurveyResponse response = responseService.submit(survey, responseId);
        return Map.of("result", "ok", "responseId", response.getId());
    }
}
