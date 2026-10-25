package com.example.survey.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** 页面路由：只负责返回模板，数据由前端 JS 调 API 获取 */
@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "redirect:/surveys";
    }

    /** 登录 / 注册页 */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /** 我的问卷列表 */
    @GetMapping("/surveys")
    public String surveys() {
        return "surveys";
    }

    /** 问卷编辑器 */
    @GetMapping("/surveys/{id}/edit")
    public String edit(@PathVariable String id, Model model) {
        model.addAttribute("surveyId", id);
        return "edit";
    }

    /** 问卷统计页 */
    @GetMapping("/surveys/{id}/stats")
    public String stats(@PathVariable String id, Model model) {
        model.addAttribute("surveyId", id);
        return "stats";
    }

    /** 题库页 */
    @GetMapping("/library")
    public String library() {
        return "library";
    }

    /** 填写页（分享链接） */
    @GetMapping("/s/{shareCode}")
    public String fill(@PathVariable String shareCode, Model model) {
        model.addAttribute("shareCode", shareCode);
        return "fill";
    }
}
