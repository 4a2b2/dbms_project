package com.example.survey.model.survey;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 问卷中的一道题（内嵌文档）。
 *  阶段二起，题目内容来自题库版本：itemId/versionId/version 记录引用来源；
 *  type/title/required/options/constraints 是引用时刻（或保存时刻）写入的快照，
 *  问卷发布后快照固化，题库后续修改不影响本问卷。 */
@Data
public class Question {

    /** 题目在问卷内的唯一标识 */
    private String qid;

    /** 题库条目 id（阶段二新增；旧数据迁移后补齐） */
    private String itemId;

    /** 引用的题目版本 id */
    private String versionId;

    /** 引用的版本号（冗余展示用） */
    private Integer version;

    /** 保存请求中的一次性意图：true 表示把这道新题加入题库（仅对无 itemId 的题生效）。
     *  不落库——入题库后归属由 itemId 表达；未勾选的题目只属于本问卷。 */
    @org.springframework.data.annotation.Transient
    private Boolean addToLibrary;

    /** 题型字符串：single / multiple / text / number（不用枚举，便于扩展新题型） */
    private String type;

    /** 题目标题 */
    private String title;

    /** 是否必答 */
    private boolean required;

    /** 选择题的选项列表，其他题型为空 */
    private List<Option> options = new ArrayList<>();

    /**
     * 题型约束参数，键值由题型决定，例如：
     * multiple -> minSelect/maxSelect；text -> minLength/maxLength；number -> min/max/integerOnly
     */
    private Map<String, Object> constraints = new HashMap<>();

    /** 按 oid 查找选项，找不到返回 null */
    public Option optionByOid(String oid) {
        if (oid == null || options == null) {
            return null;
        }
        return options.stream().filter(o -> oid.equals(o.getOid())).findFirst().orElse(null);
    }
}
