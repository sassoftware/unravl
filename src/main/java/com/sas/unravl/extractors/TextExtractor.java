package com.sas.unravl.extractors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.annotations.UnRAVLExtractorPlugin;
import com.sas.unravl.generators.Text;
import com.sas.unravl.util.Json;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;

import org.apache.log4j.Logger;

/**
 * An extractor for
 *
 * <pre>
 * { "text" : "varName" }
 * { "text" : "@file-name" }
 * </pre>
 *
 * TODO: allow an encoding, such as<br>
 * <code>{ "text" : "@file-name", "encoding": "UTF-16" }</code>
 * 
 * @author David.Biesack@sas.com
 */

@UnRAVLExtractorPlugin("text")
public class TextExtractor extends BaseUnRAVLExtractor {

    private static final Logger logger = Logger.getLogger(TextExtractor.class);

    @Override
    public void extract(UnRAVL current, ObjectNode extractor, ApiCall call)
            throws UnRAVLException {
        super.extract(current, extractor, call);
        JsonNode target = Json.firstFieldValue(extractor);
        if (!target.isTextual())
            throw new UnRAVLException(
                    "json binding value must be a var name or a @file-name string");
        String to = target.textValue();

        String text = Text.utf8ToString(call.getResponseBody());
        current.bind("responseBody", text);
        if (to.startsWith(UnRAVL.REDIRECT_PREFIX)) {
            String where = to.substring(UnRAVL.REDIRECT_PREFIX.length());
            where = getScript().expand(where);
            try {
                boolean stdout = where.equals("-");
                Writer f = stdout ? new PrintWriter(System.out)
                        : new OutputStreamWriter(new FileOutputStream(where),
                                Text.UTF_8);
                f.write(text);
                if (stdout)
                    System.out.println();
                else
                    f.close();
            } catch (IOException e) {
                throw new UnRAVLException(e.getMessage(), e);
            }
            if (!where.equals("-"))
                logger.info("Wrote text to file " + where);
        } else {
            current.bind(to, text);
        }
    }

}
