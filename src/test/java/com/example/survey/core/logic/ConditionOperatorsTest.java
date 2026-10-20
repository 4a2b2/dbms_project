package com.example.survey.core.logic;

import com.example.survey.exception.ApiException;
import com.example.survey.model.response.Answer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 条件算子注册表单元测试 */
class ConditionOperatorsTest {

    private final ConditionOperators operators = new ConditionOperators();

    private Answer optionAnswer(String... oids) {
        Answer a = new Answer();
        a.setQid("q");
        a.setOptionIds(List.of(oids));
        return a;
    }

    private Answer numberAnswer(double number) {
        Answer a = new Answer();
        a.setQid("q");
        a.setNumber(number);
        return a;
    }

    @Test
    void eqMatchesSingleOption() {
        assertTrue(operators.test("eq", optionAnswer("a"), "a"));
        assertFalse(operators.test("eq", optionAnswer("b"), "a"));
    }

    @Test
    void eqMatchesNumber() {
        assertTrue(operators.test("eq", numberAnswer(5), 5));
        assertTrue(operators.test("eq", numberAnswer(5), "5"));
        assertFalse(operators.test("eq", numberAnswer(6), 5));
    }

    @Test
    void inMatchesCandidateList() {
        assertTrue(operators.test("in", optionAnswer("b"), List.of("a", "b")));
        assertFalse(operators.test("in", optionAnswer("c"), List.of("a", "b")));
        assertTrue(operators.test("in", numberAnswer(3), List.of(1, 3, 5)));
    }

    @Test
    void containsAndContainsAll() {
        assertTrue(operators.test("contains", optionAnswer("a", "b"), "a"));
        assertFalse(operators.test("contains", optionAnswer("a", "b"), "c"));
        assertTrue(operators.test("containsAll", optionAnswer("a", "b", "c"), List.of("a", "c")));
        assertFalse(operators.test("containsAll", optionAnswer("a", "b"), List.of("a", "c")));
    }

    @Test
    void numberComparisons() {
        assertTrue(operators.test("gt", numberAnswer(10), 5));
        assertFalse(operators.test("gt", numberAnswer(5), 5));
        assertTrue(operators.test("gte", numberAnswer(5), 5));
        assertTrue(operators.test("lt", numberAnswer(3), 5));
        assertTrue(operators.test("lte", numberAnswer(5), 5));
        assertFalse(operators.test("lt", numberAnswer(8), 5));
    }

    @Test
    void betweenSupportsListAndMap() {
        assertTrue(operators.test("between", numberAnswer(50), List.of(0, 100)));
        assertTrue(operators.test("between", numberAnswer(100), Map.of("min", 0, "max", 100)));
        assertFalse(operators.test("between", numberAnswer(101), List.of(0, 100)));
    }

    @Test
    void nullAnswerNeverMatches() {
        assertFalse(operators.test("eq", null, "a"));
        assertFalse(operators.test("contains", null, "a"));
    }

    @Test
    void unknownOperatorRejected() {
        assertThrows(ApiException.class, () -> operators.test("noop", numberAnswer(1), 1));
        assertFalse(operators.exists("noop"));
        assertTrue(operators.exists("between"));
    }
}
