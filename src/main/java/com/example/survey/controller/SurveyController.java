package com.example.survey.controller;

import com.example.survey.dto.CreateSurveyRequest;
import com.example.survey.dto.UpdateSurveyRequest;
import com.example.survey.model.survey.Survey;
import com.example.survey.security.Auth;
import com.example.survey.service.StatsService;
import com.example.survey.service.SurveyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 问卷管理（均需登录，且只能操作自己的问卷） */
@RestController
@RequestMapping("/api/surveys")
public class SurveyController {

    private final SurveyService surveyService;
    private final StatsService statsService;

    public SurveyController(SurveyService surveyService, StatsService statsService) {
        this.surveyService = surveyService;
        this.statsService = statsService;
    }

    @PostMapping
    public Survey create(HttpServletRequest request, @Valid @RequestBody CreateSurveyRequest req) {
        return surveyService.create(Auth.requireUid(request), req);
    }

    @GetMapping
    public List<Survey> listMine(HttpServletRequest request) {
        return surveyService.listMine(Auth.requireUid(request));
    }

    @GetMapping("/{id}")
    public Survey detail(HttpServletRequest request, @PathVariable String id) {
        return surveyService.getOwned(Auth.requireUid(request), id);
    }

    @PutMapping("/{id}")
    public Survey update(HttpServletRequest request, @PathVariable String id,
                         @RequestBody UpdateSurveyRequest req) {
        return surveyService.update(Auth.requireUid(request), id, req);
    }

    @PostMapping("/{id}/publish")
    public Survey publish(HttpServletRequest request, @PathVariable String id) {
        return surveyService.publish(Auth.requireUid(request), id);
    }

    @PostMapping("/{id}/close")
    public Survey close(HttpServletRequest request, @PathVariable String id) {
        return surveyService.close(Auth.requireUid(request), id);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(HttpServletRequest request, @PathVariable String id) {
        surveyService.delete(Auth.requireUid(request), id);
        return Map.of("result", "ok");
    }

    /** 统计（仅已提交答卷） */
    @GetMapping("/{id}/stats")
    public Map<String, Object> stats(HttpServletRequest request, @PathVariable String id) {
        Survey survey = surveyService.getOwned(Auth.requireUid(request), id);
        return statsService.stats(survey);
    }
}
