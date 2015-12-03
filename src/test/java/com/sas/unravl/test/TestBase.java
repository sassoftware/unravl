package com.sas.unravl.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.UnRAVLRuntime;
import com.sas.unravl.util.Json;

import java.util.HashMap;

import org.junit.Test;
import org.springframework.web.client.RestTemplate;

public class TestBase {

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
        env.put("time", "Mon, Aug 4, 2014");
        env.put("where", "API");
        env.put("which", Integer.valueOf(16));
        env.put("who", "hackers");
        return env;
    }

    @Test
    public void baseTest() {

    }
}
