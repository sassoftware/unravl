package com.sas.unravl.extractors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.annotations.UnRAVLExtractorPlugin;
import com.sas.unravl.generators.Text;
import com.sas.unravl.util.Json;

import org.apache.log4j.Logger;

/**
 * An extractor for
 * 
 * <pre>
 * { "json" : "varName" }
 * { "json" : "@file-name" }
 * </pre>
 *
 * <p>
 * In both cases, the JSON body is also bound to the variable
 * <code>"responseBody"</code>. The value will be a Jackson
 * <code>ObjectNode</code> or <code>ArrayNode</code>.
 * <p>
 * This extractor also allows an option, <code>"unwrap"</code>:
 * </p>
 * 
 * <pre>
 * { "json" : "varName", "unwrap" : true }
 * { "json" : "@file-name", "unwrap" : true }
 * </pre>
 * <p>
 * If the <code>"unwrap"</code> option is <code>true</code>, the JSON value will
 * be "unwrapped". An <code>ObjectNode</code> will be unwrapped into a
 * <code>java.util.Map</code>; a <code>ArrayNode</code> will be unwrapped into a
 * <code>java.util.List</code>.
 *
 * @author David.Biesack@sas.com
 */

@UnRAVLExtractorPlugin("json")
public class JsonExtractor extends BaseUnRAVLExtractor {

    private static final Logger logger = Logger.getLogger(JsonExtractor.class);

    @Override
    public void extract(UnRAVL current, ObjectNode extractor, ApiCall call)
            throws UnRAVLException {
        super.extract(current, extractor, call);
        JsonNode target = Json.firstFieldValue(extractor);
        boolean unwrap = unwrapOption(extractor);
        if (!target.isTextual())
            throw new UnRAVLException(
                    "json binding value must be a var name or a @file-name string");
        String to = target.textValue();
        JsonNode json = Json.parse(Text.utf8ToString(call.getResponseBody()));
        Object result = unwrap ? Json.unwrap(json) : json;
        current.bind("responseBody", result);
        if (to.startsWith(UnRAVL.REDIRECT_PREFIX)) {
            String where = to.substring(UnRAVL.REDIRECT_PREFIX.length());
            where = getScript().expand(where);
            Json.extractToStream(json, where);
            if (!where.equals("-"))
                logger.info("Wrote JSON to file " + where);
        } else {
            current.bind(to, result);
        }
    }

}
