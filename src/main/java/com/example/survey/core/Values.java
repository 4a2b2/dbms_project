package com.example.survey.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 约束/规则参数解析工具。
 * JSON 反序列化后数字可能是 Integer 也可能是 Double，这里统一处理。
 */
public final class Values {

    private Values() {
    }

    /** 转 Double，无法解析返回 null */
    public static Double asDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** 转 Integer，无法解析返回 null */
    public static Integer asInt(Object value) {
        Double d = asDouble(value);
        return d == null ? null : d.intValue();
    }

    /** 转 Boolean，无法解析返回 null */
    public static Boolean asBool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            if ("true".equalsIgnoreCase(s.trim())) {
                return true;
            }
            if ("false".equalsIgnoreCase(s.trim())) {
                return false;
            }
        }
        return null;
    }

    /** 转字符串列表：本身为列表则逐元素 toString，单个值则包装成单元素列表 */
    public static List<String> asStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
        } else if (value != null) {
            result.add(value.toString());
        }
        return result;
    }

    /** 从约束 Map 中取 Double，键不存在返回 null */
    public static Double getDouble(Map<String, Object> map, String key) {
        return map == null ? null : asDouble(map.get(key));
    }

    /** 从约束 Map 中取 Integer，键不存在返回 null */
    public static Integer getInt(Map<String, Object> map, String key) {
        return map == null ? null : asInt(map.get(key));
    }

    /** 从约束 Map 中取 Boolean，键不存在返回 null */
    public static Boolean getBool(Map<String, Object> map, String key) {
        return map == null ? null : asBool(map.get(key));
    }
}
