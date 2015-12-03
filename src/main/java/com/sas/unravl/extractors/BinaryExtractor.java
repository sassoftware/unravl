package com.sas.unravl.extractors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.annotations.UnRAVLExtractorPlugin;
import com.sas.unravl.generators.Binary;
import com.sas.unravl.util.Json;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.log4j.Logger;

/**
 * An extractor for <code>{ "binary" : "varName" }</code> or
 * <code>{ "binary" : "@file-name" }</code>
 * 
 * @author David.Biesack@sas.com
 */
@UnRAVLExtractorPlugin("binary")
public class BinaryExtractor extends BaseUnRAVLExtractor {

    private static final Logger logger = Logger
            .getLogger(BinaryExtractor.class);

    @Override
    public void extract(UnRAVL current, ObjectNode extractor, ApiCall call)
            throws UnRAVLException {
        super.extract(current, extractor, call);
        JsonNode target = Json.firstFieldValue(extractor);
        if (!target.isTextual())
            throw new UnRAVLException(
                    "json binding value must be a var name or a @file-name string");
        byte bytes[] = call.getResponseBody();
        current.bind("responseBody", bytes);
        String to = target.textValue();
        if (to.startsWith(UnRAVL.REDIRECT_PREFIX)) {
            String where = to.substring(UnRAVL.REDIRECT_PREFIX.length());
            where = getScript().expand(where);
            try {
                BufferedOutputStream b = new BufferedOutputStream(
                        new FileOutputStream(where));
                Binary.copy(new ByteArrayInputStream(bytes), b);
                b.close();
                logger.info("Wrote binary to file " + where);
            } catch (IOException e) {
                throw new UnRAVLException(e.getMessage(), e);
            }
        } else {
            current.bind(to, bytes);
        }
    }

}
