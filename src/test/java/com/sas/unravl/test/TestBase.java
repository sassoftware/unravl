package com.sas.unravl.test;

import java.util.HashMap;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.UnRAVLRuntime;
import com.sas.unravl.util.Json;

public class TestBase {

    protected static final String WHO_VAL = "hackers";
    protected static final Integer WHICH_VAL = Integer.valueOf(16);
    protected static final String WHERE_VAL = "API";
    protected static final String TIME_VAL = "Mon, Aug 4, 2014";
    protected static final String WHO_KEY = "who";
    protected static final String WHICH_KEY = "which";
    protected static final String WHERE_KEY = "where";
    protected static final String TIME_KEY = "time";

    public static UnRAVL scriptFixture() {
        UnRAVLRuntime r = new UnRAVLRuntime(envFixture());
        UnRAVL script = new UnRAVL(r);
        return script;
    }

    /**
     * Convenience test method that constructs a JsonNode from a Json string
     *
     * @param string
     *            A string containing JSON, but which uses ' instead of " to
     *            quote strings. This allows easier encoding of JSON strings in
     *            tests without having to escape all the " characters with \"
     * @return a JsonNode parsed from string after replacing all ' characters
     *         with "
     */
    public static JsonNode mockJson(String string) throws UnRAVLException {
        return Json.parse(string.replace("'", "\""));
    }

    public static HashMap<String, Object> envFixture() {
        HashMap<String, Object> env = new HashMap<String, Object>();
        env.put(TIME_KEY, TIME_VAL);
        env.put(WHERE_KEY, WHERE_VAL);
        env.put(WHICH_KEY, WHICH_VAL);
        env.put(WHO_KEY, WHO_VAL);
        return env;
    }

    @Test
    public void baseTest() {

    }
}
