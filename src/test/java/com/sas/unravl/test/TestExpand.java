package com.sas.unravl.test;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;

import org.junit.Test;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.UnRAVLRuntime;
import com.sas.unravl.util.Json;

public class TestExpand extends TestBase {

    @Test
    public void expand() throws JsonProcessingException, IOException,
            UnRAVLException {
        String template = "{time} is the time for {which} {who} to come to the aid of their {where}";
        UnRAVL script = TestBase.scriptFixture();
        String actual = script.expand(template);
        String expected = "Mon, Aug 4, 2014 is the time for 16 hackers to come to the aid of their API";
        assertEquals(expected, actual);

    }

    @Test
    public void testMap() throws UnRAVLException {
        UnRAVL script = TestBase.scriptFixture();
        String in = "{ 's' : 'string', 'b' : true, 'i' : 123, 'd': 3.14159, "
                + " 'a' : [ '{time}', 'is the time for {which}', '{who} to come to the aid', 'of their {where}' ],"
                + " 'o' : { 'x' : '{which} {where}s' } }";
        JsonNode from = TestBase.mockJson(in);
        String out = script.expand(in);
        JsonNode expected = TestBase.mockJson(out);
        JsonNode to = Json.expand(from, script);
        assertEquals(expected, to);
    }

    // No conditional assignment for 'env' binding
    @Test
    public void testNoConditionalAssignmentForEnvBinding()
            throws UnRAVLException {
        UnRAVL script = TestBase.scriptFixture();
        String in = "http://maps.googleapis.com";

        String actual = script.expand(in);

        String expected = in;
        assertEquals(expected, actual);

    }

    // Conditional assignment in 'env' binding but no system environment
    // variable named 'host' is provided
    @Test
    public void testConditionalAssignmentInEnvBindingWithoutSystemEnvironmentVariable()
            throws UnRAVLException {
        UnRAVL script = TestBase.scriptFixture();
        String in = "{host|http://maps.googleapis.com}";

        String actual = script.expand(in);
        String expected = "http://maps.googleapis.com";

        assertEquals(expected, actual);

    }

    // Conditional assignment in 'env' binding and system environment variable
    // named 'host' is provided
    @Test
    public void testConditionalAssignmentInEnvBindingWithSystemEnvironmentVariable()
            throws UnRAVLException {

        // Add value for 'host' system environment variable
        HashMap<String, Object> env = new HashMap<String, Object>();
        env.put("host", "http://maps.appleapis.com");

        UnRAVLRuntime r = new UnRAVLRuntime(env);
        UnRAVL script = new UnRAVL(r, new RestTemplate());

        String in = "{host|http://maps.googleapis.com}";

        String actual = script.expand(in);
        String expected = "http://maps.appleapis.com";

        assertEquals(expected, actual);

    }

    // No conditional assignment for GET method
    @Test
    public void testNoConditionalAssignmentForGetUrl() throws UnRAVLException {
        UnRAVL script = TestBase.scriptFixture();
        String in = "http://maps.googleapis.com/maps/api/elevation/json?locations=18.5202,73.8567";

        String actual = script.expand(in);

        String expected = in;
        assertEquals(expected, actual);

    }

    // Conditional assignment in GET method without system environment variable
    @Test
    public void testConditionalAssignmentForGetUrlWithoutSystemEnvironmentVariable()
            throws UnRAVLException {

        UnRAVL script = TestBase.scriptFixture();

        String in = "{host|http://maps.googleapis.com}/maps/api/elevation/json?locations=18.5202,73.8567";

        String actual = script.expand(in);
        String expected = "http://maps.googleapis.com/maps/api/elevation/json?locations=18.5202,73.8567";

        assertEquals(expected, actual);

    }

    // Conditional assignment in GET method with system environment variable
    @Test
    public void testConditionalAssignmentForGetUrlWithSystemEnvironmentVariable()
            throws UnRAVLException {

        // Add value for 'host' system environment variable
        HashMap<String, Object> env = new HashMap<String, Object>();
        env.put("host", "http://maps.appleapis.com");

        UnRAVLRuntime r = new UnRAVLRuntime(env);
        UnRAVL script = new UnRAVL(r, new RestTemplate());

        String in = "{host|http://maps.googleapis.com}/maps/api/elevation/json?locations=18.5202,73.8567";

        String actual = script.expand(in);
        String expected = "http://maps.appleapis.com/maps/api/elevation/json?locations=18.5202,73.8567";

        assertEquals(expected, actual);

    }

    // Multiple Conditional assignments in GET method without system environment
    // variable
    @Test
    public void testMultipleConditionalAssignmentsForGetUrlWithoutSystemEnvironmentVariable()
            throws UnRAVLException {

        UnRAVL script = TestBase.scriptFixture();

        String in = "{host|http://maps.googleapis.com}/maps/api/{altitude|elevation}/json?locations=18.5202,73.8567";

        String actual = script.expand(in);
        String expected = "http://maps.googleapis.com/maps/api/elevation/json?locations=18.5202,73.8567";

        assertEquals(expected, actual);

    }

    // Multiple Conditional assignments in GET method with system environment
    // variable
    @Test
    public void testMultipleConditionalAssignmentsForGetUrlWithSystemEnvironmentVariable()
            throws UnRAVLException {

        // Add value for 'host' system environment variable
        HashMap<String, Object> env = new HashMap<String, Object>();
        env.put("host", "http://maps.appleapis.com");
        env.put("altitude", "rise");

        UnRAVLRuntime r = new UnRAVLRuntime(env);
        UnRAVL script = new UnRAVL(r, new RestTemplate());

        String in = "{host|http://maps.googleapis.com}/maps/api/{altitude|elevation}/json?locations=18.5202,73.8567";

        String actual = script.expand(in);
        String expected = "http://maps.appleapis.com/maps/api/rise/json?locations=18.5202,73.8567";

        assertEquals(expected, actual);

    }

    // If the or operator '||' is used as a groovy code snippet, expand() method
    // should not break.
    // This test is required as '|' is used to define conditional assignment
    @Test
    public void testWithGroovyOrOperator() throws UnRAVLException {

        UnRAVL script = TestBase.scriptFixture();

        // Note: || is used as a groovy or operator
        String in = "status == 201 || status == 200";

        String actual = script.expand(in);
        String expected = in;

        assertEquals(expected, actual);

    }

    // nested conditional assignment without any system environment variables
    @Test
    public void testNestedConditonalAssignmentWithoutSystemEnvironmentVariable()
            throws UnRAVLException {

        UnRAVL script = TestBase.scriptFixture();

        String in = "api/models?{pagination|start={start|0}&limit={limit|25}}";

        String actual = script.expand(in);
        String expected = "api/models?start=0&limit=25";

        assertEquals(expected, actual);

    }

    // nested conditional assignment with system environment variable defined at
    // parent level
    @Test
    public void testNestedConditonalAssignmentWithSystemEnvironmentVariableForParent()
            throws UnRAVLException {

        // Add value for 'host' system environment variable
        HashMap<String, Object> env = new HashMap<String, Object>();
        env.put("pagination", "start=50&limit=100");// note empty spaces around
                                                    // URL fragment

        UnRAVLRuntime r = new UnRAVLRuntime(env);
        UnRAVL script = new UnRAVL(r, new RestTemplate());

        String in = "api/models?{pagination|start={start|0}&limit={limit|25}}";

        String actual = script.expand(in);
        String expected = "api/models?start=50&limit=100";

        assertEquals(expected, actual);

    }

    // nested conditional assignment without any system environment variable
    // defined at child level
    @Test
    public void testNestedConditonalAssignmentWithSystemEnvironmentVariableForOneChild()
            throws UnRAVLException {

        // Add value for 'host' system environment variable
        HashMap<String, Object> env = new HashMap<String, Object>();
        env.put("start", "10");// note empty spaces around URL fragment

        UnRAVLRuntime r = new UnRAVLRuntime(env);
        UnRAVL script = new UnRAVL(r, new RestTemplate());

        String in = "api/models?{pagination|start={start|0}&limit={limit|25}}";

        String actual = script.expand(in);
        String expected = "api/models?start=10&limit=25";

        assertEquals(expected, actual);

    }

    // nested conditional assignment without any system environment variable
    // defined at child level
    @Test
    public void testNestedConditonalAssignmentWithSystemEnvironmentVariableForSecondChild()
            throws UnRAVLException {

        // Add value for 'host' system environment variable
        HashMap<String, Object> env = new HashMap<String, Object>();
        env.put("limit", "50");

        UnRAVLRuntime r = new UnRAVLRuntime(env);
        UnRAVL script = new UnRAVL(r, new RestTemplate());

        String in = "api/models?{pagination|start={start|0}&limit={limit|25}}";

        String actual = script.expand(in);
        String expected = "api/models?start=0&limit=50";

        assertEquals(expected, actual);

    }

    // both non nested and nested conditional assignment without any system
    // environment variable defined at child level
    @Test
    public void testNonNestedAndNestedConditonalAssignmentWithSystemEnvironmentVariable()
            throws UnRAVLException {

        HashMap<String, Object> env = new HashMap<String, Object>();
        env.put("host", "http://localhost:8080");
        env.put("limit", "50");

        UnRAVLRuntime r = new UnRAVLRuntime(env);
        UnRAVL script = new UnRAVL(r, new RestTemplate());

        String in = "{host|http://localhost:9090}/api/models?{pagination|start={start|0}&limit={limit|25}}";

        String actual = script.expand(in);
        String expected = "http://localhost:8080/api/models?start=0&limit=50";

        assertEquals(expected, actual);

    }

    // if unbound variable is provided then it should not be modified
    @Test
    public void testUnboundVariable() throws UnRAVLException {

        UnRAVL script = TestBase.scriptFixture();

        String in = "http://localhost:9090/{api}/models";

        String actual = script.expand(in);
        String expected = "http://localhost:9090/{api}/models";

        assertEquals(expected, actual);

    }
}
