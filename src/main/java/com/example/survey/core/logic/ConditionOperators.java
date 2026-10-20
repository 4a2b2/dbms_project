package com.example.survey.core.logic;

import com.example.survey.core.Values;
import com.example.survey.exception.ApiException;
import com.example.survey.model.response.Answer;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * 条件算子注册表。
 * 新算子在构造器中一行注册即可，规则数据（surveys.logic）无需改动。
 */
@Component
public class ConditionOperators {

    private final Map<String, BiPredicate<Answer, Object>> operators = new HashMap<>();

    public ConditionOperators() {
        // eq：单选选中了某选项，或数字等于某值
        register("eq", (a, v) -> {
            if (a.getNumber() != null) {
                Double target = Values.asDouble(v);
                return target != null && a.getNumber().doubleValue() == target.doubleValue();
            }
            return singleOid(a) != null && singleOid(a).equals(String.valueOf(v));
        });
        // in：单选选中项在值列表中，或数字在值列表中
        register("in", (a, v) -> {
            List<String> candidates = Values.asStringList(v);
            if (a.getNumber() != null) {
                return candidates.stream()
                        .map(Values::asDouble)
                        .anyMatch(d -> d != null && d.doubleValue() == a.getNumber().doubleValue());
            }
            String oid = singleOid(a);
            return oid != null && candidates.contains(oid);
        });
        // contains：多选选中项包含指定选项
        register("contains", (a, v) ->
                a.getOptionIds() != null && a.getOptionIds().contains(String.valueOf(v)));
        // containsAll：多选选中项包含值列表中的全部选项
        register("containsAll", (a, v) ->
                a.getOptionIds() != null && a.getOptionIds().containsAll(Values.asStringList(v)));
        // 数字比较
        register("gt", (a, v) -> a.getNumber() != null && compare(a.getNumber(), v) > 0);
        register("gte", (a, v) -> a.getNumber() != null && compare(a.getNumber(), v) >= 0);
        register("lt", (a, v) -> a.getNumber() != null && compare(a.getNumber(), v) < 0);
        register("lte", (a, v) -> a.getNumber() != null && compare(a.getNumber(), v) <= 0);
        // between：value 为 [min, max] 或 {min: x, max: y}，闭区间
        register("between", (a, v) -> {
            if (a.getNumber() == null) {
                return false;
            }
            Double min = null;
            Double max = null;
            if (v instanceof List<?> list && list.size() == 2) {
                min = Values.asDouble(list.get(0));
                max = Values.asDouble(list.get(1));
            } else if (v instanceof Map<?, ?> map) {
                min = Values.asDouble(map.get("min"));
                max = Values.asDouble(map.get("max"));
            }
            return min != null && max != null && a.getNumber() >= min && a.getNumber() <= max;
        });
    }

    private void register(String op, BiPredicate<Answer, Object> fn) {
        operators.put(op, fn);
    }

    public boolean exists(String op) {
        return operators.containsKey(op);
    }

    /** 执行算子；未知算子抛 400；空答案一律 false */
    public boolean test(String op, Answer answer, Object value) {
        BiPredicate<Answer, Object> fn = operators.get(op);
        if (fn == null) {
            throw ApiException.badRequest("未知条件算子：" + op);
        }
        if (answer == null) {
            return false;
        }
        return fn.test(answer, value);
    }

    /** 单选答案的选中项，非单选返回 null */
    private static String singleOid(Answer a) {
        return a.getOptionIds() != null && a.getOptionIds().size() == 1 ? a.getOptionIds().get(0) : null;
    }

    private static int compare(double number, Object value) {
        Double target = Values.asDouble(value);
        if (target == null) {
            return -1;
        }
        return Double.compare(number, target);
    }
}
