package com.sas.unravl.assertions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.annotations.UnRAVLAssertionPlugin;
import com.sas.unravl.generators.Text;

import java.io.IOException;

/**
 * Asserts that the HTTP response body matches some text. There are several
 * forms for specifying the expected text response, as defined by {@link Text}.
 * 
 * @author David.Biesack@sas.com
 *
 */
@UnRAVLAssertionPlugin("text")
public class TextBodyAssertion extends BaseUnRAVLAssertion
        implements UnRAVLAssertion {

    @Override
    public void check(UnRAVL current, ObjectNode assertion, Stage when,
            ApiCall call) throws UnRAVLAssertionException, UnRAVLException {
        super.check(current, assertion, when, call);
        JsonNode value = assertion.get("text");
        try {
            Text text = new Text(current, value);
            String expected = text.text();
            String actual = Text.utf8ToString(call.getResponseBody());
            try {
                assertEquals(expected, actual);
            } catch (AssertionError a) {
                throw new UnRAVLAssertionException(a.getMessage(), a);
            }
        } catch (IOException e1) {
            throw new UnRAVLException(e1.getMessage(), e1);
        }

    }

    private void assertEquals(String expected, String actual)
            throws UnRAVLAssertionException {
        if (expected.length() != actual.length())
            throw new UnRAVLAssertionException(String.format(
                    "text contents not equal: length %d not equal to expected length %d",
                    actual.length(), expected.length()));
        for (int len = actual.length(), i = 0; i < len; i++) {
            if (actual.charAt(i) != expected.charAt(i))
                throw new UnRAVLAssertionException(String.format(
                        "binary array contents not equal at byte %d: found %d, expected %d",
                        i, actual.charAt(i), expected.charAt(i)));
        }
    }

}
