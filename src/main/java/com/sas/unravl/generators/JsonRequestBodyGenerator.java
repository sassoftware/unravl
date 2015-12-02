package com.sas.unravl.generators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.annotations.UnRAVLRequestBodyGeneratorPlugin;
import com.sas.unravl.util.Json;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Generates a JSON request body for this API call. The node can have one of
 * several forms:
 * 
 * <pre>
 * { "json" : json-object-or-array }
 * { "json" : "@file-or-url" }
 * { "json" : "varName" }
 * </pre>
 * <p>
 * In the first form the request body is derived from a JSON object or array, as
 * a UTF-8 stream. In the second form, the JSON is first read from an external
 * file or URL. Environment substitution is performed on string values in the
 * input JSON. In the third form, the body is derived from a JSON object or
 * array in the current environment.
 * <p>
 * The JSON node is bound in the current environment as a string named
 * "requestBody". Variables are expanded within text values in the resulting
 * JSON.
 * <p>
 * 
 * @author David.Biesack@sas.com
 * 
 */
@UnRAVLRequestBodyGeneratorPlugin("json")
public class JsonRequestBodyGenerator extends BaseUnRAVLRequestBodyGenerator {

    @Override
    public InputStream getBody(UnRAVL script, ObjectNode bodySpec, ApiCall call)
            throws IOException, UnRAVLException {
        JsonNode json = bodySpec.get("json");
        JsonNode body = null;
        if (json.isTextual()) {
            String val = json.textValue();
            if (val.startsWith(UnRAVL.REDIRECT_PREFIX)) {
                Text request = new Text(script, json);
                json = Json.parse(request.text());
                body = Json.expand(json, script);
            } else {
                Object ref = script.binding(val);
                if (ref instanceof JsonNode) {
                    body = Json.expand((JsonNode) ref, script);
                } else {
                    throw new UnRAVLException(String.format(
                            "Variable %s is not bound to a JSON value in 'json' body generator",
                            val));
                }
            }
        } else if (json.isContainerNode()) {
            body = Json.expand(json, script);
        } else {
            throw new UnRAVLException(String.format(
                    "Unrecognized JSON value %s in 'json' body generator",
                    json));
        }
        script.bind("requestBody", body);
        String jsonText = body.toString();
        return new ByteArrayInputStream(Text.utf8(jsonText));
    }

}
