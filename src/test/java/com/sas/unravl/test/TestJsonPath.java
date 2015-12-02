// Copyright (c) 2015, SAS Institute Inc., Cary, NC, USA, All Rights Reserved
package com.sas.unravl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.UnRAVLRuntime;
import com.sas.unravl.util.Json;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class TestJsonPath {

    @Test
    public void testJsonPath() throws UnRAVLException {
        UnRAVLRuntime r = new UnRAVLRuntime(); // drives configuration
        assertNotNull(r);
        String document = "{ \"s\": \"string\", \"b\": true, \"i\": 100, \"n\": 0.5, \"o\": { \"x\": 0, \"y\" : 0 }, \"a\": [ 0,1,2,3,4,5] }";
        JsonNode node = Json.parse(document);
        ObjectMapper m = new ObjectMapper();
        Object jo;
        if (node instanceof ObjectNode)
            jo = m.convertValue(node, Map.class);
        else // (node instanceof ArrayNode)
            jo = m.convertValue(node, List.class);
        // JsonPath parses strings into java.util.Map and java.util.List
        // objects.
        // If we have a Jackson JsonNode (an ObjectNode or an ArrayNode), we
        // must convert the Jackson types to Maps or Lists to use JsonPath.
        JsonProvider jp = Configuration.defaultConfiguration().jsonProvider();
        assertNotNull(jo);
        String s = JsonPath.read(jo, "$.s");
        Object o = JsonPath.read(jo, "$.o");
        Object a = JsonPath.read(jo, "$.a");
        assertTrue(s.equals("string"));
        assertNotNull(o);
        assertNotNull(a);
        assertTrue(jp.isMap(o));
        assertTrue(jp.isArray(a));
        ObjectNode on = m.valueToTree(o);
        ArrayNode an = m.valueToTree(a);
        assertNotNull(on);
        assertNotNull(an);
        assertEquals(2, on.size());
        assertEquals(6, an.size());
    }
}
