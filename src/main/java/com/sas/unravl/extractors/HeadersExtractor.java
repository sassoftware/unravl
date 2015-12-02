// Copyright (c) 2014, SAS Institute Inc., Cary, NC, USA, All Rights Reserved
package com.sas.unravl.extractors;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.annotations.UnRAVLExtractorPlugin;
import com.sas.unravl.assertions.UnRAVLAssertionException;
import com.sas.unravl.util.Json;

/**
 * Extract headers from the HTTP response, place them in variables, and
 * optionally parse them into constituent fields.
 * <p>
 * The format is:
 *
 * <pre>
 * { "headers" : json-object }
 * </pre>
 *
 * For example,
 *
 * <pre>
 * { "headers" : { "contentType" : "Content-Type",
 *                 "location" : "Location"
 *               }
 * }
 * </pre>
 *
 * This will bind the value of the Content-Type header to the variable
 * "contentType" and the value of the Location header to the variable
 * "location". If the right hand side of a pair is an array instead of a string,
 * then all elements must be a string. The first is the header name, the second
 * is a regular expression pattern, and the remainder are names which will be
 * bound to the groups that are matched from the pattern. The value of the
 * header is parsed with a regular expression and the groups bound to variables.
 * For example,
 *
 * <pre>
 * { "headers" : {
 *     "contentType" : [ "Content-Type", "^(.*)\\s*;\\s*charset=(.*)$", "mediaType", "charset" ]
 *     }
 * }
 * </pre>
 *
 * will bind contentTye to the complete value of the Content-Type header, and
 * extract the media type and charset into variables named "mediaType" and
 * "charset" according to the pattern.
 * <p>
 * (Note that a per the JSON grammar, \\ characters in a JSON string must be
 * escaped, so the regular expression notation <code>\\s</code> is coded in the
 * JSON string as <code>\\\\s</code>.)
 *
 * </p>
 * For example, if the Content-Type header was
 *
 * <pre>
 * application/json;charset=UTF-8
 * </pre>
 *
 * this headers specification will bind the variables:<br>
 * responseType to "application/json; charset=UTF-8"<br>
 * mediaType to "application/json, and <br>
 * charset to "UTF-8".
 * <p>
 * If the regular expression does not match, this extractor will throw an
 * {@link UnRAVLAssertionException}
 * <p>
 * This extractor will unbind all the variables before testing the regular
 * expression, so that bindings left from other tests won't persist and leave a
 * false positive.
 * <p>
 * The more advanced form works like the {@link PatternExtractor} and binds
 * regular expression grouping values into additional environment variables. For
 * example,
 *
 * <pre>
 * [ "Content-Type", "responseType", "^(.*)\\s*;\\s*charset=(.*)$", "mediaType", "charset" ]
 * </pre>
 *
 * will bind the value of the Content-Type header into the environment variable
 * named "responseType", then perform pattern matching to extract the media type
 * and the encoding character set into the variables mediaType and charset.
 *
 * @author David.Biesack@sas.com
 */

@UnRAVLExtractorPlugin("headers")
public class HeadersExtractor extends BaseUnRAVLExtractor {

    static Logger logger = Logger.getLogger(UnRAVL.class);

    @Override
    public void extract(UnRAVL current, ObjectNode extractor, ApiCall call)
            throws UnRAVLException {
        super.extract(current, extractor, call);
        JsonNode val = Json.firstFieldValue(extractor);
        if (val.isObject()) {
            extractHeadesr(current, (ObjectNode) val, call);
        } else {
            throw new UnRAVLException(
                    String.format(
                            "Unrecognized headers extractor: object expected but found: %s",
                            val));
        }
    }

    private void extractHeadesr(UnRAVL current, ObjectNode val, ApiCall call)
            throws UnRAVLException {
        for (Map.Entry<String, JsonNode> e : Json.fields(val)) {
            String varName = e.getKey();
            JsonNode node = e.getValue();
            ArrayNode a = null;
            String headerName;
            if (node.isTextual())
                headerName = node.textValue();
            else {
                a = Json.array(node);
                for (int i = 0; i < a.size(); i++) {
                    if (!a.get(i).isTextual())
                        throw new UnRAVLException("headers extractor " + val
                                + " must be all strings");
                    if (i > 1) {
                        call.unbind(a.get(i).textValue());
                    }
                }
                if (a.size() < 2)
                    throw new UnRAVLException(
                            "Header pattern array must contain at least two values.");
                headerName = a.get(0).textValue();
            }
            List<String> header = call.getResponseHeader(headerName);
            if (header == null)
                throw new UnRAVLException("header not found for binding " + a);
            String hval = null;
            for (String h : header) {
                logger.trace("header " + headerName + ":" + h);
                getScript().bind(varName, h);
                hval = h;
            }
            if (a != null)
                bindHeaderByPattern(current, a, headerName, hval, 0);
        }
    }

    private void bindHeaderByPattern(UnRAVL current, ArrayNode a,
            String headerName, String headerValue, int offset)
            throws UnRAVLAssertionException {
        String varName;
        {
            String regex = current.expand(a.get(offset + 1).textValue());
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(headerValue);
            if (matcher.matches()) {
                for (int i = 1, v = offset + 2; i <= matcher.groupCount()
                        && v < a.size(); i++, v++) {
                    varName = a.get(v).textValue();
                    String value = matcher.group(i);
                    current.bind(varName, value);
                }
            } else
                throw new UnRAVLAssertionException("header pattern " + regex
                        + " does not match " + headerName + " value "
                        + headerValue);
        }
    }

}
