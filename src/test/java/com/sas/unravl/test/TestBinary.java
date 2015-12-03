package com.sas.unravl.test;

import static org.junit.Assert.assertArrayEquals;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.UnRAVLRuntime;
import com.sas.unravl.generators.Binary;
import com.sas.unravl.util.Json;

import java.io.IOException;

import org.junit.Test;
import org.springframework.web.client.RestTemplate;

public class TestBinary extends TestBase {

    @Test
    public void simpleBinary() throws IOException, UnRAVLException {
        testBinary("{ 'binary' : [0, 1, 2, 3, 4 ] }",
                new byte[] { 0, 1, 2, 3, 4 });
        testBinary("{ 'binary' : [0, 1, [2, 3], 4 ] }",
                new byte[] { 0, 1, 2, 3, 4 });
        testBinary("{ 'binary' : [0, 1, '@src/test/java/B.txt', 4 ] }",
                new byte[] { 0, 1, 'B', '\n', 4 });
    }

    private void testBinary(String json, byte expected[])
            throws IOException, UnRAVLException {
        ObjectNode node = Json.object(mockJson(json));
        UnRAVL script = scriptFixture();
        Binary binary = new Binary(script, node, "binary");
        byte actual[] = binary.bytes();
        assertArrayEquals(expected, actual);
    }

    @Test(expected = IOException.class)
    public void noSuchFile() throws IOException, UnRAVLException {
        ObjectNode node = Json.object(mockJson("{'binary' : '@noSuchFile' }"));
        UnRAVL script = scriptFixture();
        new Binary(script, node, "binary");
    }

    @Test(expected = IOException.class)
    public void noSuchURL() throws IOException, UnRAVLException {
        ObjectNode node = Json.object(mockJson(
                "{'binary' : '@scheme://host:9090/no/such/resource.ext' }"));
        UnRAVL script = scriptFixture();
        new Binary(script, node, "binary");
    }

}
