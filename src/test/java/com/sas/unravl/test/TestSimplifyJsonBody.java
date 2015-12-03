package com.sas.unravl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.UnRAVLRuntime;
import com.sas.unravl.util.Json;

import java.io.IOException;

import org.junit.Test;
import org.springframework.web.client.RestTemplate;

public class TestSimplifyJsonBody {

    // object
    @Test
    public void testJsonObject() throws Exception {
        assertRequestBody("{\"name\":\"value\"}",
                "{\"body\":{\"json\":{\"name\":\"value\"}}}");

    }

    @Test
    public void testObjectWithoutJsonKey() throws Exception {
        assertRequestBody("{\"name\":\"value\"}",
                "{\"body\":{\"name\":\"value\"}}");

    }

    @Test
    public void testObjectBooleanValue() throws Exception {
        assertRequestBody("{\"name\":true}",
                "{\"body\":{\"json\":{\"name\":true}}}");

    }

    @Test
    public void testObjectBooleanValueWithoutJsonKey() throws Exception {
        assertRequestBody("{\"name\":true}", "{\"body\":{\"name\":true}}");

    }

    @Test
    public void testObjectNumericValue() throws Exception {
        assertRequestBody("{\"name\":1}", "{\"body\":{\"json\":{\"name\":1}}}");

    }

    @Test
    public void testObjectNumericValueWithoutJsonKey() throws Exception {
        assertRequestBody("{\"name\":1}", "{\"body\":{\"name\":1}}");

    }

    @Test
    public void testObjectNullValue() throws Exception {
        assertRequestBody("{\"name\":null}",
                "{\"body\":{\"json\":{\"name\":null}}}");

    }

    @Test
    public void testObjectNullValueWithoutJsonKey() throws Exception {
        assertRequestBody("{\"name\":null}", "{\"body\":{\"name\":null}}");

    }

    // empty object

    @Test
    public void testObjectEmpty() throws Exception {

        assertRequestBody("{}", "{\"body\":{\"json\":{}}}");
    }

    @Test
    public void testObjectEmptyWithoutJsonKey() throws Exception {
        assertRequestBody("{}", "{\"body\":{}}");

    }

    // array

    @Test
    public void testArray() throws Exception {

        assertRequestBody("[{\"name\":\"value\"}]",
                "{\"body\":{\"json\":[{\"name\":\"value\"}]}}");

    }

    @Test
    public void testArrayWithoutJsonKey() throws Exception {

        assertRequestBody("[{\"name\":\"value\"}]",
                "{\"body\":[{\"name\":\"value\"}]}");

    }

    @Test
    public void testArrayMultipleElements() throws Exception {

        assertRequestBody("[{\"name\":\"value\"},{\"name2\":\"value2\"}]",
                "{\"body\":{\"json\":[{\"name\":\"value\"},{\"name2\":\"value2\"}]}}");

    }

    @Test
    public void testArrayMultipleElementsWithoutJsonKey() throws Exception {

        assertRequestBody("[{\"name\":\"value\"},{\"name2\":\"value2\"}]",
                "{\"body\":[{\"name\":\"value\"},{\"name2\":\"value2\"}]}");

    }

    @Test
    public void testEmptyArray() throws Exception {

        assertRequestBody("[]", "{\"body\":{\"json\":[]}}");

    }

    @Test
    public void testEmptyArrayWithoutJsonKey() throws Exception {

        assertRequestBody("[]", "{\"body\":[]}");

    }

    // textual file references

    @Test
    public void testTextualFileReference() throws Exception {

        assertRequestBody("{\"name\":\"SimplifyJsonBody\"}",
                "{\"body\": {\"json\":\"@src/test/SimplifyJsonBody.json\"}}");

    }

    @Test
    public void testTextualFileReferenceWithoutJsonKey() throws Exception {

        assertRequestBody("{\"name\":\"SimplifyJsonBody\"}",
                "{\"body\":\"@src/test/SimplifyJsonBody.json\"}");

    }

    // textual: var name containing json

    @Test
    public void testVarNameContainJsonValue() throws Exception {

        assertRequestBody("{\"name1\":\"val1\"}",
                "{\"env\":{\"var1\":{\"name1\":\"val1\"}}, \"body\":{\"json\":\"var1\"}}");

    }

    @Test
    public void testVarNameContainJsonValueWithoutJsonKey() throws Exception {

        assertRequestBody("{\"name1\":\"val1\"}",
                "{\"env\":{\"var1\":{\"name1\":\"val1\"}}, \"body\":\"var1\"}");

    }

    // textual
    @Test
    public void testTextualContent() throws Exception {

        assertRequestBody("textValue1", "{\"body\":{\"text\":\"textValue1\"}}");

    }

    @Test
    public void testTextualContentCarriageReturn() throws Exception {

        assertRequestBody("textValue1\r\n",
                "{\"body\":{\"text\":\"textValue1\\r\\n\"}}");

    }

    @Test
    public void testTextualContentMultiline() throws Exception {

        assertRequestBody("textValue1\r\ntextValue2",
                "{\"body\":{\"text\":\"textValue1\\r\\ntextValue2\"}}");

    }

    @Test
    public void testBlankTextualContent() throws Exception {

        assertRequestBody("", "{\"body\":\"\"}");

    }

    @Test
    public void testNullTextualContent() throws Exception {

        assertRequestBody(null, "{\"body\":null}");

    }

    // binary
    @Test
    public void testBinay() throws Exception {

        String actuals = getActuals(
                "{\"body\":{\"binary\":\"@src/test/data/Un.png\"}}");
        assertNotNull(actuals);

    }

    private void assertRequestBody(String expected, String input)
            throws Exception {

        String actual = getActuals(input);
        assertEquals(expected, actual);
    }

    private String getActuals(String input)
            throws UnRAVLException, IOException, JsonProcessingException {
        ApiCall apiCall = createApiCall(input);
        String actual = getRequestBodyContent(apiCall);
        return actual;
    }

    private String getRequestBodyContent(ApiCall apiCall)
            throws UnRAVLException {
        apiCall.run();
        byte[] bytes = apiCall.getRequestBody();
        if (bytes == null) {
            return null;
        }
        String requestBodyString = new String(bytes);
        return requestBodyString;
    }

    private ApiCall createApiCall(String input)
            throws UnRAVLException, IOException, JsonProcessingException {
        UnRAVLRuntime r = new UnRAVLRuntime();
        ObjectNode root = Json.object(Json.parse(input));
        RestTemplate restTemplate = new RestTemplate();
        UnRAVL script = new UnRAVL(r, root, restTemplate);
        ApiCall apiCall = new ApiCall(script, restTemplate);
        return apiCall;
    }
}
