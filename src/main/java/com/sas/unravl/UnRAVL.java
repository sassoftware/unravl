package com.sas.unravl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.assertions.UnRAVLAssertion;
import com.sas.unravl.assertions.UnRAVLAssertion.Stage;
import com.sas.unravl.extractors.UnRAVLExtractor;
import com.sas.unravl.util.Json;

/**
 * An UnRAVL script object - this is a wrapper around a JSON UnRAVL script. An
 * UnRAVL script (Uniform REST API Validation Language) is an executable domain
 * specific language for validating REST APIs. An UNRAVL script consists of an
 * execution environment (bindings of name/value pairs), an HTTP method, URI,
 * and HTTP headers. The REST API method is called, then values may be extracted
 * from the results and assertions run to validate the call.
 * <p>
 * An UnRAVL script runs within an {@link UnRAVLRuntime runtime environment}
 * which defines the set of possible value bindings (called @link
 * UnRAVLExtractor s) and the set of possible {@link UnRAVLAssertion}s.
 * <p>
 * To execute an UnRAVL script, create an UnRAVLRuntime and invoke one of its
 * execute methods.
 * <p>
 * This class only executes a single UnRAVL test, represented by a JSON object.
 * It does not execute a JSON array of scripts; use UnRAVLRuntime
 *
 * TODO: This class is too large and does too much. Refactor.
 *
 * @author David.Biesack@sas.com
 */
public class UnRAVL {

    private static final String IMPLICIT_TEMPLATE = "implicit.template";
    private static final String TEMPLATE_KEY = "template";
    private static final String NAME_KEY = "name";
    private static final String TEMPLATE_EXTENSION = ".template";
    private static final String TEXT_MEDIA_TYPES_REGEX = "^(text/.*|.*/.*(xml|json)).*$";
    private static final String JSON_MEDIA_TYPES_REGEX = "^.*(\\.|\\+)*json.*$";
    public static final String REDIRECT_PREFIX = "@";
    private UnRAVLRuntime runtime;
    private ObjectNode root;
    private String name;
    private UnRAVL template;
    private List<Header> requestHeaders;
    private Method method;
    private String uri;
    private List<UnRAVLExtractor> extractors;
    static Logger logger = Logger.getLogger(UnRAVL.class);

    public UnRAVL(UnRAVLRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Create an instance
     *
     * @param runtime
     *            the runtime environment; this may not be null
     * @param script
     *            The UnRAVL test object. To run an ArrayNode, use the execute
     *            method in UnRAVLRuntime
     * @throws JsonProcessingException
     *             if the script cannot be parsed
     * @throws IllegalArgumentException
     *             if the arguments are null or if the node is not an ObjectNode
     * @throws UnRAVLException
     *             if the script is invalid
     */
    public UnRAVL(UnRAVLRuntime runtime, ObjectNode script)
            throws JsonProcessingException, IOException, UnRAVLException {
        // TODO: create a new transient env for this test?
        // this.env = new Binding(env.getVariables());
        // Unfortunately, unlike java.util.Properties,
        // Binding does not support dynamic parent bindings

        if (script == null || runtime == null)
            throw new IllegalArgumentException(
                    "the runtime and script objects may not be null.");
        this.runtime = runtime;
        this.root = script;
        initialize();
    }

    @Override
    public String toString() {
        return "UnRAVL:[" + getName() + " " + safe(getMethod(), "<no method>")
                + " " + safe(getURI(), "<no url>") + "]";
    }

    private String safe(Object method2, String string) {
        return (method2 == null) ? string : method2.toString();
    }

    public UnRAVLRuntime getRuntime() {
        return runtime;
    }

    // public Map<String, Object> getEnv() {
    // return runtime.getBindings();
    // }

    public JsonNode getRoot() {
        return root;
    }

    public String getName() {
        return name;
    }

    public void setName() {
        JsonNode nameNode = root.get(NAME_KEY);
        if (nameNode != null) {
            name = nameNode.textValue();
            if (name.endsWith(TEMPLATE_EXTENSION))
                runtime.setTemplate(name, this);
        } else
            name = new Date().toString();
        if (isRunnable())
            runtime.getScripts().put(name, this);
        return;
    }

    public UnRAVL getTemplate() {
        if (template == null && !IMPLICIT_TEMPLATE.equals(getName()) // avoid
                                                                     // infinite
                                                                     // recursion
                                                                     // where
                                                                     // implicit.template
                                                                     // gets
                                                                     // itself
                && runtime.getTemplate(IMPLICIT_TEMPLATE) != null) {
            return runtime.getTemplate(IMPLICIT_TEMPLATE);
        }
        return template;
    }

    private void setTemplate() throws UnRAVLException {
        JsonNode tempNode = root.get(TEMPLATE_KEY);
        if (tempNode != null) {

            if (tempNode.isArray())
                throw new UnRAVLException(
                        "array template values are not yet supported.");
            if (!tempNode.isTextual())
                throw new UnRAVLException("template value must be a text node.");
            String templateName = expand(tempNode.textValue());
            if (!templateName.endsWith(TEMPLATE_EXTENSION))
                templateName += TEMPLATE_EXTENSION;
            template = runtime.getTemplate(templateName);
            if (template == null)
                throw new UnRAVLException("No such template " + templateName);
        }
    }

    public List<Header> getRequestHeaders() {
        return requestHeaders;
    }

    public Method getMethod() {
        return method;
    }

    public String getURI() {
        return uri;
    }

    public void setURI(String uri) {
        this.uri = uri;
    }

    public List<UnRAVLExtractor> getExtractors() {
        return extractors;
    }

    private void initialize() throws UnRAVLException, IOException {
        setName();
        setTemplate();
        defineAPICall();
    }

    private void defineAPICall() throws UnRAVLException, IOException {
        defineAPICall(this);
        defineHeaders();
    }

    private void defineAPICall(UnRAVL script) throws UnRAVLException,
            IOException {
        if (script == null)
            return;
        if (script.method != null) {
            this.method = script.method;
            this.uri = script.uri;
            return;
        }
        // Look for a "GET", "HEAD" or other method name in this script
        for (Map.Entry<String, JsonNode> f : Json.fields(script.root)) {
            String methodName = f.getKey().toUpperCase();
            Method m = httpMethod(methodName);
            if (m == null)
                continue;
            JsonNode node = f.getValue();
            if (node == null)
                node = script.root.get(m.toString().toLowerCase());
            if (node != null) {
                if (method != null) {
                    logger.warn(String
                            .format("Warning: HTTP method %s found but method already defined as %s %s",
                                    m, method, uri));
                }
                method = m;
                if (!node.isTextual())
                    throw new UnRAVLException(
                            String.format(
                                    "URI for method %s must be a string; found %s instead.",
                                    m, node));
                uri = node.textValue();
            }
        }
        if (method == null)
            defineAPICall(script.getTemplate());
    }

    private static Method httpMethod(String methodName) {
        for (Method m : Method.values()) {
            if (m.name().equals(methodName)) {
                return m;
            }
        }
        return null;
    }

    private void defineHeaders() throws UnRAVLException {
        ArrayList<Header> headers = new ArrayList<Header>();
        defineHeaders(this, headers);
        this.requestHeaders = headers;
    }

    public void addRequestHeader(Header header) {
        requestHeaders.add(header);
    }

    private void defineHeaders(UnRAVL from, List<Header> headers)
            throws UnRAVLException {
        if (from == null)
            return;
        UnRAVL template = from.getTemplate();
        defineHeaders(template, headers);
        JsonNode headersNode = from.getRoot().get("headers");
        if (headersNode == null)
            return;
        for (Map.Entry<String, JsonNode> h : Json.fields(headersNode)) {
            String string = h.getKey();
            // Do not expand headers here; do so in
            // ApiCall.mapHeaders(List<Header> requestHeaders)
            // This method is called before "env" is evaluated,
            // so expansion here is premature.
            String val = h.getValue().asText();
            BasicHeader header = new BasicHeader(string, val);
            headers.add(header);
        }
    }

    public ApiCall run() throws UnRAVLException, IOException {
        ApiCall apiCall = new ApiCall(this);
        try {
            return apiCall.run();
        } finally {
            apiCall.report(System.out);
        }
    }

    /** Stop execution. */
    public void cancel() {
        getRuntime().cancel();
    }

    /**
     * Bind a value within this script's environment. This will add a new
     * binding if <var>varName</var> is not yet bound, or replace the old
     * binding.
     *
     * @param varName
     *            the variable name
     * @param value
     *            the variable value
     * @return this script, which allows chaining bind calls.
     * @see UnRAVLRuntime#bind(String, Object)
     */
    public UnRAVL bind(String varName, Object value) {
        getRuntime().bind(varName, value);
        return this;
    }

    /**
     * Return the value bound to a variable in this script's environment
     *
     * @param varName
     *            the variable name
     * @return the value bound to the variable
     * @see UnRAVLRuntime#binding(String)
     */
    public Object binding(String varName) {
        return getRuntime().binding(varName);
    }

    /**
     * Test if the value bound in this script's environment
     *
     * @param varName
     *            the variable name
     * @return true iff the variable is bound
     * @see UnRAVLRuntime#bound(String)
     */
    public boolean bound(String varName) {
        return getRuntime().bound(varName);
    }

    public boolean bodyIsTextual(Header headers[]) {
        return headersMatchPattern(headers, TEXT_MEDIA_TYPES_REGEX);
    }

    public boolean bodyIsJson(Header headers[]) {
        return headersMatchPattern(headers, JSON_MEDIA_TYPES_REGEX);
    }

    private boolean headersMatchPattern(Header headers[], String pattern) {
        for (Header h : headers)
            if (h.getValue().matches(pattern))
                return true;
        return false;
    }

    public static ObjectNode statusAssertion(UnRAVL script)
            throws UnRAVLException {
        if (script == null)
            return null;
        // really need to use JSONPath ...
        JsonNode node = script.root.get("assert");
        node = ApiCall.assertionArray(node, Stage.ASSERT);
        if (node != null) {
            for (JsonNode n : Json.array(node)) {
                if (n.isObject() && Json.firstFieldName(n).equals("status"))
                    return Json.object(n);
            }
        }
        return statusAssertion(script.getTemplate());
    }

    public boolean isRunnable() {
        return name != null && !name.endsWith(TEMPLATE_EXTENSION);
    }

    public String expand(String textValue) {
        return getRuntime().expand(textValue);
    }

    public Object eval(String expression) throws UnRAVLException {
        return evalWith(expression, getRuntime().getScriptLanguage());
    }

    public Object evalWith(String expression, String lang)
            throws UnRAVLException {
        ScriptEngine engine = getRuntime().interpreter(lang);
        try {
            Object result = engine.eval(expression, engine.getContext());
            return result;
        } catch (ScriptException e) {
            logger.error("script '" + expression
                    + "' threw a runtime script exception "
                    + e.getClass().getName() + ", " + e.getMessage());
            throw new UnRAVLException(e.getMessage(), e);
        }
    }

    /**
     * Gets the value for the variable in a text node in a form of {@varName@
     * }. The values are retrieved from the Environment keys-values
     * map bindings.
     *
     * @param varName
     *            the variable name in braces
     * @return the object value of the variable
     */
    public Object obtainVariableValue(String varName) {
        return getRuntime().obtainVariableValue(varName);
    }

}
