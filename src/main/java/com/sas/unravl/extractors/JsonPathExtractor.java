package com.sas.unravl.extractors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.annotations.UnRAVLExtractorPlugin;
import com.sas.unravl.generators.Text;
import com.sas.unravl.util.Json;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * This extractor evaluates one or more <a
 * href='https://github.com/jayway/JsonPath'>JsonPath expressions</a> on the
 * JSON result or another JSON value and binds the resulting values to variables
 * in the current environment. For example, for the JSON result
 *
 * <pre>
 *  {
 *      "results" : [
 *          {
 *              "elevation" : 8815.7158203125,
 *              "location" : {
 *                 "lat" : 27.988056,
 *                 "lng" : 86.92527800000001
 *              },
 *              "resolution" : 152.7032318115234
 *          }
 *      ],
 *      "status" : "OK"
 *  }
 * </pre>
 *
 * the following extracts the JsonPath-addressed values from the response and
 * sets the variables <var>elevation, lat</var>, and <var>lng</var>:
 *
 * <pre>
 *     "bind" : [
 *         { "jsonPath" : { "elevation" : "$.results[0].elevation",
 *                          "lat" : "$.results[0].location.lat",
 *                          "lng" : "$.results[0].location.lng" },
 *            "from" : "jsonResponse" }
 *     ],
 * </pre>
 *
 * Also, the JSON response body is bound to the variable
 * <var>responseBody</var>.
 * <p>
 * This extractor supports an optional parameter "from" as described below.
 * </p>
 * 
 * <pre>
 * "from" : "varName"
 * </pre>
 * <p>
 * allows an UnRAVL script to extract from JSON defined by a UnRAVL environment
 * variable instead of the current API call's response body. The from value can
 * be a value assigned by another extractor or defined in the "env" element.
 * </p>
 *
 * @author David.Biesack@sas.com
 */
@UnRAVLExtractorPlugin({ "jsonPath", "jsonpath" })
public class JsonPathExtractor extends JsonExtractor {

    private static final Logger logger = Logger
            .getLogger(JsonPathExtractor.class);

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public void extract(UnRAVL script, ObjectNode scriptlet, ApiCall call)
            throws UnRAVLException {
        Object fromObject = getJsonSource(script, scriptlet, call);
        ObjectNode bindings = Json.object(Json.firstFieldValue(scriptlet));
        for (Map.Entry<String, JsonNode> entry : Json.fields(bindings)) {
            JsonNode path = entry.getValue();
            if (!path.isTextual()) {
                throw new UnRAVLException(
                        "JsonPath extractor requires string path values, found "
                                + path);
            }
            String pathString = call.getScript().expand(path.textValue());
            Object value = JsonPath.read(fromObject, pathString);
            script.bind(entry.getKey(), value);
        }
    }

    private Object getJsonSource(UnRAVL script, ObjectNode scriptlet,
            ApiCall call) throws UnRAVLException {
        JsonNode from = scriptlet.get("from");
        Object fromObject = null;
        if (from == null) {
            // assert response body is valid JSON; extract JSON into
            // responseBody
            from = Json.parse(Text.utf8ToString(call.getResponseBody()));
            script.bind("responseBody", from);
            fromObject = Json.unwrap(from);
        } else {
            if (from.isTextual()) {
                Object val = script.binding(from.textValue());
                if (val instanceof Map)
                    fromObject = val;
                else if (val instanceof List)
                    fromObject = val;
                else if (val instanceof ObjectNode) {
                    fromObject = mapper.convertValue((ObjectNode) val,
                            Map.class);
                } else if (val instanceof ArrayNode) {
                    fromObject = mapper.convertValue((ObjectNode) val,
                            List.class);
                } else {
                    String msg = String
                            .format("Variable named by 'from' value %s in %s extractor is not an object or array. Value is %s",
                                    from, key(scriptlet), val);
                    logger.error(msg);
                    throw new UnRAVLException(msg);
                }

            } else {
                String msg = String.format(
                        "'from' value %s in %s extractor is not a string.",
                        from, key(scriptlet));
                logger.error(msg);
                throw new UnRAVLException(msg);
            }
        }
        return fromObject;
    }
}
