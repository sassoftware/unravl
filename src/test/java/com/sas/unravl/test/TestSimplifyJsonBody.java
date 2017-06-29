package com.sas.unravl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.UnRAVLRuntime;
import com.sas.unravl.generators.Binary;
import com.sas.unravl.util.Json;

public class TestSimplifyJsonBody extends TestBase {

    private boolean isTraceEnabled = false;

    private static final String INT_VALUE_KEY = "intValue";
    private static final String STRING_VALUE_KEY = "stringValue";
    private static final String DOUBLE_VALUE_KEY = "doubleValue";
    private static final String BOOLEAN_VALUE_KEY = "booleanValue";
    private static final String NULL_VALUE_KEY = "nullValue";
    private static final String ARRAY_VALUE_KEY = "arrayValue";
    private static final String JSON_OBJECT_VALUE_KEY = "jsonObject";
    private static final String ARRAY_OBJECT_VALUES_KEY = "arrayOfObjects";

    private static final int INT_VALUE = 100;
    private static final String STRING_VALUE = "\"test_string\"";
    private static final double DOUBLE_VALUE = 100.55;
    private static final boolean BOOLEAN_VALUE = true;
    private static final String NULL_VALUE = null;
    private static final String ARRAY_VALUE = "[1,2,3,4,5]";
    private static final String JSON_OBJECT_VALUE = "{\"id\": 1, \"name\": \"test1\"}";
    private static final String ARRAY_OBJECT_VALUES = "[{\"id\": 1, \"name\" : \"test1\"}, {\"id\": 2, \"name\": \"test2\"}]";

    // object
    @Test
    public void testJsonObject() throws Exception {
        assertRequestBodyJson("{'name':'value'}",
                "{'body':{'json':{'name':'value'}}}");

    }

    @Test
    public void testObjectWithoutJsonKey() throws Exception {
        assertRequestBodyJson("{'name':'value'}", "{'body':{'name':'value'}}");

    }

    @Test
    public void testObjectBooleanValue() throws Exception {
        assertRequestBodyJson("{'name':true}",
                "{'body':{'json':{'name':true}}}");

    }

    @Test
    public void testObjectBooleanValueWithoutJsonKey() throws Exception {
        assertRequestBodyJson("{'name':true}", "{'body':{'name':true}}");

    }

    @Test
    public void testObjectNumericValue() throws Exception {
        assertRequestBodyJson("{'name':1}", "{'body':{'json':{'name':1}}}");

    }

    @Test
    public void testObjectNumericValueWithoutJsonKey() throws Exception {
        assertRequestBodyJson("{'name':1}", "{'body':{'name':1}}");

    }

    @Test
    public void testObjectNullValue() throws Exception {
        assertRequestBodyJson("{'name':null}",
                "{'body':{'json':{'name':null}}}");

    }

    @Test
    public void testObjectNullValueWithoutJsonKey() throws Exception {
        assertRequestBodyJson("{'name':null}", "{'body':{'name':null}}");

    }

    // empty object

    @Test
    public void testObjectEmpty() throws Exception {

        assertRequestBodyJson("{}", "{'body':{'json':{}}}");
    }

    @Test
    public void testObjectEmptyWithoutJsonKey() throws Exception {
        assertRequestBodyJson("{}", "{'body':{}}");

    }

    // array

    @Test
    public void testArray() throws Exception {

        assertRequestBodyJson("[{'name':'value'}]",
                "{'body':{'json':[{'name':'value'}]}}");

    }

    @Test
    public void testArrayWithoutJsonKey() throws Exception {

        assertRequestBodyJson("[{'name':'value'}]",
                "{'body':[{'name':'value'}]}");

    }

    @Test
    public void testArrayMultipleElements() throws Exception {

        assertRequestBodyJson("[{'name':'value'},{'name2':'value2'}]",
                "{'body':{'json':[{'name':'value'},{'name2':'value2'}]}}");

    }

    @Test
    public void testArrayMultipleElementsWithoutJsonKey() throws Exception {

        assertRequestBodyJson("[{'name':'value'},{'name2':'value2'}]",
                "{'body':[{'name':'value'},{'name2':'value2'}]}");

    }

    @Test
    public void testEmptyArray() throws Exception {

        assertRequestBodyJson("[]", "{'body':{'json':[]}}");

    }

    @Test
    public void testEmptyArrayWithoutJsonKey() throws Exception {

        assertRequestBodyJson("[]", "{'body':[]}");

    }

    // textual file references

    @Test
    public void testTextualFileReference() throws Exception {

        assertRequestBodyJson("{'name':'SimplifyJsonBody'}",
                "{'body': {'json':'@src/test/SimplifyJsonBody.json'}}");

    }

    @Test
    public void testTextualFileReferenceWithoutJsonKey() throws Exception {

        assertRequestBodyJson("{'name':'SimplifyJsonBody'}",
                "{'body':'@src/test/SimplifyJsonBody.json'}");

    }

    // textual: var name containing json

    @Test
    public void testVarNameContainJsonValue() throws Exception {

        assertRequestBodyJson("{'name1':'val1'}",
                "{'env':{'var1':{'name1':'val1'}}, 'body':{'json':'var1'}}");

    }

    @Test
    public void testVarNameContainJsonValueWithoutJsonKey() throws Exception {

        assertRequestBodyJson("{'name1':'val1'}",
                "{'env':{'var1':{'name1':'val1'}}, 'body':'var1'}");

    }

    // textual
    @Test
    public void testTextualContent() throws Exception {

        assertRequestBody("textValue1", "{'body':{'text':'textValue1'}}");

    }

    @Test
    public void testTextualContentCarriageReturn() throws Exception {

        assertRequestBody("textValue1\r\n",
                "{'body':{'text':'textValue1\\r\\n'}}");

    }

    @Test
    public void testTextualContentMultiline() throws Exception {

        assertRequestBody("textValue1\r\ntextValue2",
                "{'body':{'text':'textValue1\\r\\ntextValue2'}}");

    }

    @Test
    public void testBlankTextualContent() throws Exception {

        assertRequestBody("", "{'body':''}");

    }

    @Test
    public void testNullTextualContent() throws Exception {

        assertRequestBody(null, "{'body':null}");

    }

    // binary
    @Test
    public void testBinary() throws Exception {

        String actuals = getActuals("{'body':{'binary':'@src/test/data/Un.png'}}");
        assertNotNull(actuals);

    }

    /** tests for variable substitution with different data types **/
    /** "{varName}" returns the string value for the specified variable **/
    /**
     * "{@varName@}" returns the actual value for the specified
     * variable
     **/

    // no variable found

    @Test
    public void testVarSubstitionWithNoEnvVarDefined() throws Exception {
        assertRequestBodyJson("{'name':'{@badValue@}'}",
                "{'body':{'json':{'name':'{@badValue@}'}}}");
    }

    @Test
    public void testVarSubstitionWithNoEnvVarDefinedWithoutJsonKey()
            throws Exception {
        assertRequestBodyJson("{'name':'{@badValue@}'}",
                "{'body':{'name':'{@badValue@}'}}");
    }

    //string containing @

    @Test
    public void testVarSubstitionWithAtValue() throws Exception {
        assertRequestBodyJson("{'name':'{@bad@Value@}'}",
                "{'env': {'bad@Value': 1}, 'body':{'json':{'name':'{@bad@Value@}'}}}");
    }

    @Test
    public void testVarSubstitionWithAtValueThatReturnsString() throws Exception {
        assertRequestBodyJson("{'name':'{bad@Value}'}",
                "{'env': {'bad@Value': 1}, 'body':{'json':{'name':'{bad@Value}'}}}");
    }

    @Test
    public void testVarSubstitionWithNoVarValue() throws Exception {
        assertRequestBodyJson("{'name':'{@@}'}",
                "{'body':{'json':{'name':'{@@}'}}}");
    }

    // integer

    @Test
    public void testIntVarSubstitutionThatReturnsString() throws Exception {
        assertRequestBodyJson(formatExpectedString(INT_VALUE_KEY, INT_VALUE),
                formatInputWithStringSubstitution(INT_VALUE_KEY, INT_VALUE));
    }

    @Test
    public void testIntVarSubstitution() throws Exception {
        assertRequestBodyJson(formatExpected(INT_VALUE_KEY, INT_VALUE),
                formatInput(INT_VALUE_KEY, INT_VALUE));
    }

    @Test
    public void testIntVarSubstitutionWithoutJsonKey() throws Exception {
        assertRequestBodyJson(formatExpected(INT_VALUE_KEY, INT_VALUE),
                formatInputWithoutJsonKey(INT_VALUE_KEY, INT_VALUE));
    }

    // String

    @Test
    public void testStringVarSubstitution() throws Exception {
        assertRequestBodyJson(formatExpected(STRING_VALUE_KEY, STRING_VALUE),
                formatInput(STRING_VALUE_KEY, STRING_VALUE));
    }

    @Test
    public void testStringVarSubstitutionWithoutJsonKey() throws Exception {
        assertRequestBodyJson(formatExpected(STRING_VALUE_KEY, STRING_VALUE),
                formatInputWithoutJsonKey(STRING_VALUE_KEY, STRING_VALUE));
    }

    // double

    @Test
    public void testDoubleVarSubstitutionThatReturnsString() throws Exception {
        assertRequestBodyJson(
                formatExpectedString(DOUBLE_VALUE_KEY, DOUBLE_VALUE),
                formatInputWithStringSubstitution(DOUBLE_VALUE_KEY,
                        DOUBLE_VALUE));
    }

    @Test
    public void testDoubleVarSubstitution() throws Exception {
        assertRequestBodyJson(formatExpected(DOUBLE_VALUE_KEY, DOUBLE_VALUE),
                formatInput(DOUBLE_VALUE_KEY, DOUBLE_VALUE));
    }

    @Test
    public void testDoubleVarSubstitutionWithoutJsonKey() throws Exception {
        assertRequestBodyJson(formatExpected(DOUBLE_VALUE_KEY, DOUBLE_VALUE),
                formatInputWithoutJsonKey(DOUBLE_VALUE_KEY, DOUBLE_VALUE));
    }

    // boolean

    @Test
    public void testBooleanVarSubstitutionThatReturnsString() throws Exception {
        assertRequestBodyJson(
                formatExpectedString(BOOLEAN_VALUE_KEY, BOOLEAN_VALUE),
                formatInputWithStringSubstitution(BOOLEAN_VALUE_KEY,
                        BOOLEAN_VALUE));
    }

    @Test
    public void testBooleanVarSubstitution() throws Exception {
        assertRequestBodyJson(formatExpected(BOOLEAN_VALUE_KEY, BOOLEAN_VALUE),
                formatInput(BOOLEAN_VALUE_KEY, BOOLEAN_VALUE));
    }

    @Test
    public void testBooleanVarSubstitutionWithoutJsonKey() throws Exception {
        assertRequestBodyJson(formatExpected(BOOLEAN_VALUE_KEY, BOOLEAN_VALUE),
                formatInputWithoutJsonKey(BOOLEAN_VALUE_KEY, BOOLEAN_VALUE));
    }

    // null

    @Test
    public void testNullVarSubstitution() throws Exception {
        assertRequestBodyJson(formatExpected(NULL_VALUE_KEY, NULL_VALUE),
                formatInput(NULL_VALUE_KEY, NULL_VALUE));
    }

    @Test
    public void testNullVarSubstitutionWithoutJsonKey() throws Exception {
        assertRequestBodyJson(formatExpected(NULL_VALUE_KEY, NULL_VALUE),
                formatInputWithoutJsonKey(NULL_VALUE_KEY, NULL_VALUE));
    }

    // array

    @Test
    public void testArrayVarSubstitutionThatReturnsString() throws Exception {
        assertRequestBodyJson(
                formatExpectedString(ARRAY_VALUE_KEY, ARRAY_VALUE),
                formatInputWithStringSubstitution(ARRAY_VALUE_KEY, ARRAY_VALUE));
    }

    @Test
    public void testArrayVarSubstitution() throws Exception {
        assertRequestBodyJson(formatExpected(ARRAY_VALUE_KEY, ARRAY_VALUE),
                formatInput(ARRAY_VALUE_KEY, ARRAY_VALUE));
    }

    @Test
    public void testArrayVarSubstitutionWithoutJsonKey() throws Exception {
        assertRequestBodyJson(formatExpected(ARRAY_VALUE_KEY, ARRAY_VALUE),
                formatInputWithoutJsonKey(ARRAY_VALUE_KEY, ARRAY_VALUE));
    }

    // json object

    @Test
    public void testJsonObjectVarSubstitution() throws Exception {
        assertRequestBodyJson(
                formatExpected(JSON_OBJECT_VALUE_KEY, JSON_OBJECT_VALUE),
                formatInput(JSON_OBJECT_VALUE_KEY, JSON_OBJECT_VALUE));
    }

    @Test
    public void testJsonObjectVarSubstitutionWithoutJsonKey() throws Exception {
        assertRequestBodyJson(
                formatExpected(JSON_OBJECT_VALUE_KEY, JSON_OBJECT_VALUE),
                formatInputWithoutJsonKey(JSON_OBJECT_VALUE_KEY,
                        JSON_OBJECT_VALUE));
    }

    // array of json objects

    @Test
    public void testArrayOfObjectsVarSubstitution() throws Exception {
        assertRequestBodyJson(
                formatExpected(ARRAY_OBJECT_VALUES_KEY, ARRAY_OBJECT_VALUES),
                formatInput(ARRAY_OBJECT_VALUES_KEY, ARRAY_OBJECT_VALUES));
    }

    @Test
    public void testArrayOfObjectsVarSubstitutionWithoutJsonKey()
            throws Exception {
        assertRequestBodyJson(
                formatExpected(ARRAY_OBJECT_VALUES_KEY, ARRAY_OBJECT_VALUES),
                formatInputWithoutJsonKey(ARRAY_OBJECT_VALUES_KEY,
                        ARRAY_OBJECT_VALUES));
    }

    private String formatExpectedString(String key, Object value) {
        return String.format("{'%s':'%s'}", key, value);
    }

    private String formatExpected(String key, Object value) {
        return String.format("{'%s':%s}", key, value);
    }

    private String formatInputWithStringSubstitution(String key, Object value) {
        return String.format(
                "{'env': {'%s': %s}, 'body':{'json':{'%s':'{%s}'}}}", key,
                value, key, key);
    }

    private String formatInput(String key, Object value) {
        return String.format(
                "{'env': {'%s': %s}, 'body':{'json':{'%s':'{@%s@}'}}}", key,
                value, key, key);
    }

    private String formatInputWithoutJsonKey(String key, Object value) {
        return String.format("{'env': {'%s': %s}, 'body':{'%s':'{@%s@}'}}}",
                key, value, key, key);
    }

    private void assertRequestBodyJson(String expected, String input)
            throws Exception {

        String actual = getActuals(input);
        if (isTraceEnabled) {
            System.out.println("Expected: " + mockJson(expected) + "\nInput: "
                    + mockJson(input) + "\nActual: " + actual);
        }
        assertEquals(mockJson(expected).toString(), actual);
    }

    private void assertRequestBody(String expected, String input)
            throws Exception {
        String actual = getActuals(input);
        if (isTraceEnabled) {
            System.out.println("Expected: " + expected + "\nInput: "
                    + mockJson(input) + "\nActual: " + actual);
        }
        assertEquals(expected, actual);
    }

    private String getActuals(String input) throws UnRAVLException,
            IOException, JsonProcessingException {
        ApiCall apiCall = createApiCall(input);
        String actual = getRequestBodyContent(apiCall);
        return actual;
    }

    private String getRequestBodyContent(ApiCall apiCall)
            throws UnRAVLException {
        apiCall.run(); // These calls may not have a method (PUT or POST) that
                       // will consume the requestStream
        if (apiCall.getRequestStream() == null) {
            return null;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Binary.copy(apiCall.getRequestStream(), baos);
            String requestBodyString = null;
            requestBodyString = baos.toString("UTF-8");
            return requestBodyString;
        } catch (UnsupportedEncodingException e) {
            throw new UnRAVLException(e.getMessage());// should not happen;
                                                      // UTF-8 should exist
        } catch (IOException e) {
            throw new UnRAVLException(e.getMessage());
        }
    }

    private ApiCall createApiCall(String input) throws UnRAVLException,
            IOException, JsonProcessingException {
        UnRAVLRuntime r = new UnRAVLRuntime();
        ObjectNode root = Json.object(mockJson(input));
        UnRAVL script = new UnRAVL(r, root);
        ApiCall apiCall = new ApiCall(script);
        return apiCall;
    }
}
