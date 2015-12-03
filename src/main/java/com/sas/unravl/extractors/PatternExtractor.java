// Copyright (c) 2014, SAS Institute Inc., Cary, NC, USA, All Rights Reserved
package com.sas.unravl.extractors;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.annotations.UnRAVLExtractorPlugin;
import com.sas.unravl.assertions.UnRAVLAssertionException;
import com.sas.unravl.util.Json;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

/**
 * Matches text against grouping regular expressions and binds the substrings
 * into constituent variable bindings in the current UnRAVL script environment.
 * The extractor form is
 * 
 * <pre>
 * { "pattern" : [ string, pattern, var0, ... varn ] }
 * </pre>
 * 
 * such as
 * 
 * <pre>
 * { "pattern" : [ "{responseType}", "^(.*)\\s*;\\s*charset=(.*)$", "mediaType", "charset" ] }
 * </pre>
 * 
 * <p>
 * This will match the value of the environment expansion of "{responseType}" to
 * the given regular expression pattern <code>^(.*)\s*;\s*charset=(.*)$</code>,
 * and bind the media type and the encoding character set substrings to the
 * variables <code>mediaType</code> and <code>charset</code>. (Note that a per
 * the JSON grammar, backslash (<code>\</code>) characters in a JSON string must
 * be escaped, so the regular expression notation <code>\s</code> is coded in
 * the JSON string as <code>\\s</code>.)
 * </p>
 * 
 * <p>
 * For example, if the <code>responseType</code> binding in the environment was
 * 
 * <pre>
 * application/json; charset=UTF-8
 * </pre>
 * 
 * this pattern specification will bind the variables:<br>
 * mediaType to <code>"application/json"</code>, and <br>
 * charset to <code>"UTF-8"</code>.
 * <p>
 * If the regular expression does not match, this extractor will throw an
 * {@link UnRAVLAssertionException}
 * 
 * <p>
 * This extractor will unbind all the variables before testing the regular
 * expression, so that bindings left from other tests won't persist and leave a
 * false positive.
 * 
 * @author David.Biesack@sas.com
 *
 */

@UnRAVLExtractorPlugin("pattern")
public class PatternExtractor extends BaseUnRAVLExtractor {

    static Logger logger = Logger.getLogger(UnRAVL.class);

    @Override
    public void extract(UnRAVL current, ObjectNode extractor, ApiCall call)
            throws UnRAVLException {
        super.extract(current, extractor, call);
        ArrayNode a = Json.array(Json.firstFieldValue(extractor));
        if (a.size() < 3)
            throw new UnRAVLException("pattern extractor " + a
                    + " must have at least three strings: [var-name pattern fieldName]");
        for (int i = 1; i < a.size(); i++) {
            if (!a.get(i).isTextual())
                throw new UnRAVLException(
                        "pattern extractor " + a + " must be all strings");
            if (i > 1) {
                getCall().unbind(a.get(i).textValue());
            }
        }
        String value = current.expand(a.get(0).textValue());
        if (value == null)
            throw new UnRAVLException("variable " + value
                    + " in pattern extractor " + extractor + " is not bound");
        String text = value.toString();
        String regex = current.expand(a.get(1).textValue());
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.matches()) {
            for (int i = 1, v = 2; i <= matcher.groupCount()
                    && v < a.size(); i++, v++) {
                value = a.get(v).textValue();
                String res = matcher.group(i);
                current.bind(value, res);
            }
        } else
            throw new UnRAVLAssertionException("pattern " + regex
                    + " does not match " + value + " value '" + value + "'");
    }

}
