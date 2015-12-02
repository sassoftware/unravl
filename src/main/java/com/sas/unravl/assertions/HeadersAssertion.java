package com.sas.unravl.assertions;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.log4j.Logger;
import org.springframework.http.HttpHeaders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.annotations.UnRAVLAssertionPlugin;
import com.sas.unravl.util.Json;

/**
 * Asserts that HTTP response headers exists and that it matches a regular
 * expression pattern.
 * 
 * <pre>
 * { "headers" : { header : pattern } }
 * </pre>
 * 
 * HTTP header case is ignored (Content-Type and content-type are the same
 * header), but by convention headers are coded in Initial-Caps-With-Hyphens
 * format. The pattern is a Java regular expression pattern as in
 * {@link Pattern}.
 * 
 * @author David.Biesack@sas.com
 */
@UnRAVLAssertionPlugin({ "headers", "header" })
public class HeadersAssertion extends BaseUnRAVLAssertion {

    static final Logger logger = Logger.getLogger(HeadersAssertion.class);

    @Override
    public void check(UnRAVL current, ObjectNode assertion, Stage when,
            ApiCall call) throws UnRAVLAssertionException, UnRAVLException {
        super.check(current, assertion, when, call);
        HttpHeaders headers = call.getResponseHeaders();
        JsonNode spec = assertion.get("headers");
        check(spec, headers, current);
        return;
    }

    private void check(JsonNode headerNode, HttpHeaders headers, UnRAVL current)
            throws UnRAVLException {
        for (Map.Entry<String, JsonNode> next : Json.fields(headerNode)) {
            String header = next.getKey();
            JsonNode valNode = next.getValue();
            if (!valNode.isTextual())
                throw new UnRAVLException("header value " + valNode
                        + " is not a string (regular expression expected)");
            String pattern = current.expand(valNode.textValue());
            List<String> h = findHeader(header, headers);
            try {
                boolean found = false;
                for (String hval : h) {
                    Matcher matcher = Pattern.compile(pattern)
                            .matcher(hval);
                    if (matcher.matches()) {
                        found = true;
                        logger.trace("header " + header
                                + " matches required pattern " + pattern);
                    }
                }
                if (!found) {
                    throw new UnRAVLAssertionException("header " + header
                            + " does not match required pattern " + pattern);

                }
            } catch (PatternSyntaxException e) {
                throw new UnRAVLException(
                        "Invalid header pattern regular expression, " + pattern);
            }

        }

    }

    private List<String> findHeader(String header, HttpHeaders headers)
            throws UnRAVLAssertionException {
        List<String> vals = headers.get(header);
        if (vals != null) {
            return vals;
        }
        throw new UnRAVLAssertionException("Required header " + header
                + " not found. Existing headers:" + Arrays.asList(headers));
    }
}
