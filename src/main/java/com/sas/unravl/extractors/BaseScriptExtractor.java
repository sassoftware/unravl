package com.sas.unravl.extractors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.generators.Text;
import com.sas.unravl.util.Json;

import java.io.IOException;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * A base class for script-based extractors, such as {@link GroovyExtractor} and
 * {@link JavaScriptExtractor}.
 * <p>
 * Usage:
 *
 * <pre>
 * "bind" : [
 *    { "lang" : { var-value-pairs } }
 *    ]
 * </pre>
 *
 * where "lang" is a supported script language name such as "groovy" or
 * "javascript".
 * <p>
 * The <var>var-value-pairs</var> are JSON object notation such as
 * <code>"varName" : "<em>expression</em>"</code>. Each
 * <code><em>expression</em></code> may be a string, a string in the form
 * "@file-or-URL", or an array of such source specifications, as defined in
 * {@link Text}. The resulting text is evaluated as a script in the target
 * language.
 * </p>
 * <p>
 * Expressions may use any variable binding that is currently in effect,
 * including preceding <var>var-value-pairs</var> in the current scriptlet.
 * Example (for Groovy):
 * </p>
 *
 * <pre>
 * "bind" : [
 *    { "groovy" : { "pi" : "Math.PI",
 * 	             "r" : "json.a[2].doubleValue()",
 * 	             "pirsquared" : "pi*r*r",
 * 	             "itemName" : "json.name.textValue()"
 * 	            }
 * 	  }
 * ]
 * </pre>
 *
 * <p>
 * The script value is subject to environment expansion before running. All
 * variables in the environment are available as local variables when the script
 * runs.
 * </p>
 *
 * @author David.Biesack@sas.com
 */
public class BaseScriptExtractor extends BaseUnRAVLExtractor {

    private static final Logger logger = Logger
            .getLogger(BaseUnRAVLExtractor.class);

    private final String language;

    public BaseScriptExtractor(String language) {
        this.language = language;
    }

    @Override
    public void extract(UnRAVL unravl, ObjectNode scriptlet, ApiCall call)
            throws UnRAVLException {
        super.extract(unravl, scriptlet, call);
        boolean unwrap = unwrapOption(scriptlet);
        ObjectNode bindings = Json.object(Json.firstFieldValue(scriptlet));
        for (Map.Entry<String, JsonNode> e : Json.fields(bindings)) {
            JsonNode sourceNode = null;

            sourceNode = e.getValue();
            try {
                String name = e.getKey();
                if (!(sourceNode.isTextual() || sourceNode.isArray())) {
                    throw new UnRAVLException(key(scriptlet)
                            + " extractor requires a string or array of strings, found "
                            + sourceNode);
                }
                String expressionString = new Text(unravl, sourceNode).text();
                expressionString = call.getScript().expand(expressionString);
                Object value = evaluate(unravl, expressionString);
                unravl.bind(name, unwrap ? Json.unwrap(value) : value);

            } catch (RuntimeException rte) {
                logger.error("Script '" + sourceNode + "' (expansion '"
                        + sourceNode + "') threw a runtime exception "
                        + e.getClass().getName() + ", " + rte.getMessage());
                throw new UnRAVLException(rte.getMessage(), rte);
            } catch (IOException ioe) {
                logger.error("I/O exception " + ioe.getMessage()
                        + " reading script '" + sourceNode);
                throw new UnRAVLException(ioe.getMessage(), ioe);
            }
        }
    }

    /**
     * Evaluate an expression. Subclasses may override this. The default
     * implementation evaluates the expression using the Java script language
     * associated with this instance (Groovy, JavaScript, etc.)
     * 
     * @param unravl
     *            the current UnRAVL script
     * @param expressionString
     *            the expression to evaluate
     * @return the result of evaluating the expression
     * @throws UnRAVLException
     *             if the expression is invalid or results in an error during
     *             evaluation
     */
    protected Object evaluate(UnRAVL unravl, String expressionString)
            throws UnRAVLException {
        return unravl.evalWith(expressionString, language);
    }

}
