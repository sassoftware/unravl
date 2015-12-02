package com.sas.unravl.assertions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.annotations.UnRAVLAssertionPlugin;
import com.sas.unravl.generators.Text;
import com.sas.unravl.util.Json;

import java.io.IOException;

/**
 * Asserts that the HTTP response body is a JSON body that matches the given
 * JSON specification.
 * 
 * <pre>
 * { "json" : expected-json-body }
 * { "json" : "@file-or-url" }
 * </pre>
 * 
 * In the first form the JSON body is coded directly in the UnRAVL script. In
 * the second form, the JSON is in an external file or URL. It is assumed to be
 * in UTF-8 encoding.
 * <p>
 * This body assertion operation performs a somewhat strict definition of
 * equality: arrays and JSON objects must match in cardinality and order.
 * Numbers are matched exactly. Whitespace is ignored.
 * <h3>Examples</h3>
 * 
 * <pre>
 * { "json" : { "x" : 1, "y" : 2, "n" : "NC", "data" : [10, 99, 0.5] } }
 * </pre>
 * 
 * this will assert that the HTTP response matches the JSON object with fields
 * x, y, n, and data, with values 1, 2, "NC", and the array [10, 99, 0.5],
 * respectively.
 * 
 * @author David.Biesack@sas.com
 *
 */
@UnRAVLAssertionPlugin("json")
public class JsonBodyAssertion extends BaseUnRAVLAssertion implements
        UnRAVLAssertion {

    @Override
    public void check(UnRAVL current, ObjectNode assertion, Stage when,
            ApiCall call) throws UnRAVLAssertionException, UnRAVLException {
        super.check(current, assertion, when, call);

        JsonNode expected = Json.firstFieldValue(assertion);

        ObjectMapper mapper = new ObjectMapper();
        String content;
        try {
            content = current.expand(Text.utf8ToString(call.getResponseBody()));
            JsonNode actual = mapper.readTree(content);
            JsonNode mapped = Json.expand(actual, current);
            expected = realize(expected, mapper);
            boolean same = mapped.equals(expected);
            if (!same)
                throw new UnRAVLAssertionException(
                        "Response body does not match expected. Received:"
                                + actual);
        } catch (JsonProcessingException e) {
            throw new UnRAVLException("Could not parse response body as JSON: "
                    + e.getMessage(), e);
        } catch (IOException e) {
            throw new UnRAVLException("Could not parse response body as JSON: "
                    + e.getMessage(), e);
        }

        // Following JSONAssert fails - I get java.lang.NoClassDefFoundError:
        // org/skyscreamer/jsonassert/JSONAssert
        // even though it is right here...
        // Bummer, JSONAssert uses org.json.JSONObject, not Jackson JsonNode
        // So I'll convert to string then compare with JSONAssert
        // String expected = expectedj.toString();
        // String body = new String(current.responseBody().toByteArray());
        // try {
        // // TODO: add additional fields to control the strictness,
        // // maybe to ignore some values?
        // JSONAssert.assertEquals(expected, body, false);
        // } catch (JSONException e) {
        // throw new UnravlAssertionException(e.getMessage(), e);
        // }

    }

    // If node is "@file-or-URL , read text from that file and parse as JSON
    private JsonNode realize(JsonNode expected, ObjectMapper mapper)
            throws IOException, UnRAVLException {
        JsonNode json = expected;
        if (expected.isTextual()) {
            String path = getScript().expand(expected.textValue());
            String text = getScript()
                    .expand(new Text(getScript(), path).text());
            json = mapper.readTree(text);
        }
        return json;
    }

}
