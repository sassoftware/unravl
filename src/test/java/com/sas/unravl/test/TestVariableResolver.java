package com.sas.unravl.test;

import static org.junit.Assert.assertEquals;

import com.sas.unravl.util.VariableResolver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

public class TestVariableResolver {

    static Map<String, Object> environmentMap;
    static VariableResolver reusableResolver;

    @BeforeClass
    public static void initEnv() {
        environmentMap = new HashMap<String, Object>();
        environmentMap.put("var1", "val1");
        environmentMap.put("var_2", "val2");
        environmentMap.put("var.2", "val3");
        environmentMap.put("$var4", "val4");
        environmentMap = Collections.unmodifiableMap(environmentMap);
        reusableResolver = new VariableResolver(environmentMap);
    }

    private void assertResolve(String expected, String input) {
        String actual = reusableResolver.expand(input);
        assertEquals(expected, actual);
        VariableResolver newInstance = new VariableResolver(environmentMap);
        actual = newInstance.expand(input);
        assertEquals(expected, actual);

    }

    private void assertNoResolve(String input) {
        String actual = reusableResolver.expand(input);
        assertEquals(input, actual);
    }

    @Test
    public void test01() {
        String input = "one";
        assertResolve(input, input);
    }

    @Test
    public void test02() {
        assertResolve("val1", "{var1}");
    }

    @Test
    public void test03() {
        assertResolve("one val1", "one {var1}");
    }

    @Test
    public void test04() {
        assertResolve("one val1 two val2", "one {var1} two {var_2}");
    }

    @Test
    public void test05() {
        assertResolve("val1", "{var1|value1}");
    }

    @Test
    public void test06() {
        assertResolve("one val1 two val2", "one {var1} two {var_2|value2}");
    }

    @Test
    public void test07() {
        assertResolve("val1", "{var1|one {var_2|value2}}");
    }

    @Test
    public void test08() {
        assertResolve("val1 and val3",
                "{var1|one {var_2|value2}} and {var.2|value3}");
    }

    @Test
    public void test09() {
        assertResolve("one val2", "{undef1|one {var_2|value2}}");
    }

    @Test
    public void test10() {
        assertResolve("one val2 and three val4 ",
                "{undef1|one {var_2|value2}} and {undef3|three {$var4|value4}} ");
    }

    @Test
    public void test11() {
        assertResolve("one val2 and val3",
                "{undef1|one {var_2|value2}} and {var.2|three {$var4|value4}}");
    }

    @Test
    public void test12() {
        assertNoResolve("{some text var1|one}");
    }

    @Test
    public void test13() {
        assertNoResolve(
                "{\"name\":\"Ganesh\",\"task\":\"Handle | after no valid var name\"}");
    }

    @Test
    public void test15() {
        assertResolve("{\"a\":1, \"b\":2, \"c\":\"val1\"}",
                "{\"a\":1, \"b\":2, \"c\":\"{var1}\"}");
    }

    @Test
    public void test16() {
        assertResolve("one val2 and three val4 four",
                "{undef1|one {var_2|value2}} and {undef3|three {$var4} {undef4|four}}");
    }

    @Test
    public void test17() {
        assertResolve("one val2 and three val4 four",
                "{undef1|one {var_2|value2}} and {undef3|three {$var4} {undef4|four}}");
    }

    @Test
    public void test18() {
        assertResolve("one val2 and three val4 four.",
                "{undef1|one {var_2|value2}} and {undef3|three {$var4} {undef4|four}}.");
    }

    @Test
    public void test19() {
        assertResolve("{var_2|value2", "{var_2|value2");
    }

    @Test
    public void test20() {
        assertNoResolve("{var1|value1 {var_2|value2");
    }

    @Test
    public void test21() {
        assertResolve("{var1|value1 val2", "{var1|value1 {var_2|value2}");
    }

    @Test
    public void test22() {
        assertNoResolve("{var1|value1 {var_2|value2 {var3|value3");
    }

    @Test
    public void test23() {
        assertResolve("{var1|value1 val2 {var3|value3",
                "{var1|value1 {var_2|value2} {var3|value3");
    }

}
