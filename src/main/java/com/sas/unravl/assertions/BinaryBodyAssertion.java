package com.sas.unravl.assertions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.annotations.UnRAVLAssertionPlugin;
import com.sas.unravl.generators.Binary;

import java.io.IOException;

/**
 * Asserts that the HTTP response body matches some text. There are several
 * forms for specifying the expected text response, as defined by {@link Binary}
 * .
 * 
 * @author David.Biesack@sas.com
 *
 */
@UnRAVLAssertionPlugin("binary")
public class BinaryBodyAssertion extends BaseUnRAVLAssertion
        implements UnRAVLAssertion {

    @Override
    public void check(UnRAVL current, ObjectNode assertion, Stage when,
            ApiCall call) throws UnRAVLAssertionException, UnRAVLException {
        super.check(current, assertion, when, call);
        JsonNode value = assertion.get("binary");
        try {
            Binary binary = new Binary(current, value);
            byte[] expected = binary.bytes();
            byte[] actual = call.getResponseBody();
            assertArrayEquals(expected, actual);
        } catch (IOException e1) {
            throw new UnRAVLException(e1.getMessage(), e1);
        }

    }

    private void assertArrayEquals(byte[] expected, byte[] actual)
            throws UnRAVLAssertionException {
        if (expected.length != actual.length)
            throw new UnRAVLAssertionException(String.format(
                    "binary array contents not equal: length %d not equal to expected length %d",
                    actual.length, expected.length));
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] != expected[i])
                throw new UnRAVLAssertionException(String.format(
                        "binary array contents not equal at byte %d: found %d, expected %d",
                        i, actual[i], expected[i]));
        }
    }

}
