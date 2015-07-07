package com.sas.unravl;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.sas.unravl.assertions.BaseUnRAVLAssertion;
import com.sas.unravl.assertions.GroovyAssertion;
import com.sas.unravl.assertions.StatusAssertion;
import com.sas.unravl.assertions.UnRAVLAssertion;
import com.sas.unravl.assertions.UnRAVLAssertion.Stage;
import com.sas.unravl.assertions.UnRAVLAssertionException;
import com.sas.unravl.auth.UnRAVLAuth;
import com.sas.unravl.extractors.GroovyExtractor;
import com.sas.unravl.extractors.UnRAVLExtractor;
import com.sas.unravl.generators.Binary;
import com.sas.unravl.generators.JsonRequestBodyGenerator;
import com.sas.unravl.generators.UnRAVLRequestBodyGenerator;
import com.sas.unravl.util.Json;

import groovy.lang.Binding;
import groovy.lang.MissingPropertyException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;

/**
 * Encapsulate a runtime call to an API, as specified by an UnRAVL script. This
 * runs env bindings, preconditions, generates the request body, if any, and
 * uses the request headers, HTTP method, and URI defined by the UnRAVL script,
 * calls the API, and stores the HTTP status, response headers, and response
 * body, then binds results as per extractors defined in the script, and runs
 * assertions.
 * 
 * @author sasdjb
 */
public class ApiCall {

    private static final Logger logger = Logger.getLogger(ApiCall.class);
    private static final String JSON_GENERATOR_KEY = "json";

    private UnRAVL script;
    private ByteArrayOutputStream requestBody;
    private ByteArrayOutputStream responseBody;

    private int httpStatus;
    private Header responseHeaders[];

    private List<UnRAVLAssertion> passedAssertions, failedAssertions,
            skippedAssertions;
    private UnRAVLException exception;
    private Method method;
    private String uri;

    private static final ObjectNode STATUS_ASSERTION = new ObjectNode(
            JsonNodeFactory.instance);
    static {
        STATUS_ASSERTION.set("status", new TextNode("2.."));
    }

    public ApiCall(UnRAVL script) throws UnRAVLException, IOException {
        this.script = script;

        passedAssertions = new ArrayList<UnRAVLAssertion>();
        failedAssertions = new ArrayList<UnRAVLAssertion>();
        skippedAssertions = new ArrayList<UnRAVLAssertion>();

        script.getRuntime().addApiCall(this);
    }

    public ApiCall run() throws UnRAVLException {
        try {
            if (getScript().isRunnable() && conditionalExecution()) {
                defineEnv();
                bind("unravlScript", getScript());
                if (runAssertions(UnRAVLAssertion.Stage.PRECONDITIONS)) {
                    defineBody();
                    executeAPI();
                    extract();
                    runAssertions(UnRAVLAssertion.Stage.ASSERT);
                }
            }
        } catch (UnRAVLException e) {
            throwException(e);
        } catch (IOException e) {
            throwException(e);
        }
        return this;
    }

    private boolean conditionalExecution() throws UnRAVLException {
        Boolean cond = conditionalExecution(getScript());
        if (cond == null)
            cond = Boolean.valueOf(getRuntime().getFailedAssertionCount() == 0);
        return cond.booleanValue();
    }

    private Boolean conditionalExecution(UnRAVL script) throws UnRAVLException {
        if (script == null)
            return null;
        Boolean inherited = conditionalExecution(script.getTemplate());
        if (inherited != null && !inherited.booleanValue())
            return Boolean.FALSE;
        JsonNode cond = script.getRoot().get("if");
        if (cond == null)
            return inherited;
        Object condition = null;
        if (cond.isBoolean())
            condition = (BooleanNode) cond;
        else if (cond.isTextual()) {
            String condS = cond.textValue();
            if (getScript().bound(condS))
                condition = getScript().binding(condS);
            else {
                try {
                    condition = GroovyExtractor.eval(this, condS);
                } catch (RuntimeException e) {
                    logger.error("Groovy script '" + condS
                            + "' threw a runtime exception "
                            + e.getClass().getName() + ", " + e.getMessage());
                    throw new UnRAVLException(e.getMessage(), e);
                }
            }
        }
        if (condition instanceof Boolean)
            return (Boolean) condition;
        else if (condition instanceof BooleanNode)
            return Boolean.valueOf(((BooleanNode) condition).booleanValue());
        else {
            throw new UnRAVLException(
                    "Invalid condition value for if condition: " + cond);
        }
    }

    private void authenticate() throws UnRAVLException, IOException {
        authenticate(script);
    }

    private void authenticate(UnRAVL script) throws UnRAVLException,
            IOException {
        if (script == null)
            return;

        JsonNode auth = script.getRoot().get("auth");
        if (auth == null) {
            authenticate(script.getTemplate()); // recurse on template
            return;
        }
        ObjectNode spec = Json.object(auth);
        String authKey = spec.fields().next().getKey();
        Class<? extends UnRAVLAuth> authClass = getPlugins().getAuth().get(
                authKey);
        try {
            UnRAVLAuth authInstance = authClass.newInstance();
            authInstance.authenticate(getScript(), spec, this);
        } catch (InstantiationException e) {
            throw new UnRAVLException(
                    "Could not instantiate authentication plugin for " + auth);
        } catch (IllegalAccessException e) {
            throw new UnRAVLException(
                    "Could not instantiate authentication plugin for " + auth);
        }

    }

    private void defineBody() throws UnRAVLException, IOException {
        defineBody(script);
    }

    private void defineBody(UnRAVL script) throws UnRAVLException, IOException {
        if (script == null)
            return;
        JsonNode body = script.getRoot().get("body");

        if (body == null || body.isNull()) {
            defineBody(script.getTemplate());
            return;
        }
        if (body.isTextual() && !isVariableHoldingJson(body.asText())) {
            String s = script.expand(body.asText());
            if (!s.trim().startsWith(UnRAVL.REDIRECT_PREFIX)) {
                requestBody = new ByteArrayOutputStream();
                try {
                    requestBody.write(s.getBytes("UTF-8"));
                    requestBody.close();
                } catch (IOException e) {
                    throw new UnRAVLException(
                            "Could not encode string using UTF-8 for body "
                                    + body);
                }
                return;
            }
        }

        ObjectNode bodyObj = null;
        String generatorKey = null;
        Class<? extends UnRAVLRequestBodyGenerator> bgClass = null;

        if (body.isObject() && body.fields().hasNext()) {
            bodyObj = Json.object(body);
            generatorKey = body.fields().next().getKey();
            bgClass = getPlugins().getBodyGenerators().get(generatorKey);

        }

        if (bgClass == null || body.isArray() || body.isTextual()) {
            bodyObj = new ObjectNode(JsonNodeFactory.instance);
            bodyObj.set(JSON_GENERATOR_KEY, body);
            generatorKey = JSON_GENERATOR_KEY;
            bgClass = JsonRequestBodyGenerator.class;
        }

        try {
            UnRAVLRequestBodyGenerator gen = bgClass.newInstance();
            InputStream is = gen.getBody(script, bodyObj, this);
            // TODO: Instead of storing in memory, store in a temp file?
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Binary.copy(is, baos);
            baos.close();
            requestBody = baos;
        } catch (InstantiationException e) {
            throw new UnRAVLException(
                    "Could not instantiate body generator plugin for " + body);
        } catch (IllegalAccessException e) {
            throw new UnRAVLException(
                    "Could not instantiate body generator plugin for " + body);
        }

    }

    private boolean isVariableHoldingJson(String value) {
        if (value != null && !value.startsWith(UnRAVL.REDIRECT_PREFIX)) {
            try {
                Object ref = script.getEnv().getVariable(value);
                if (ref instanceof JsonNode) {
                    return true;
                }
            } catch (MissingPropertyException e) {

            }
        }
        return false;
    }

    private void extract() throws UnRAVLException {
        extract(script);
    }

    private void extract(UnRAVL script) throws UnRAVLException {
        try {
            if (script == null)
                return;
            extract(script.getTemplate());
            JsonNode bind = script.getRoot().get("bind");
            if (bind == null)
                return;
            if (bind.isObject()) {
                bind = Json.wrapInArray(bind);
            }
            for (JsonNode j : Json.array(bind)) {
                ObjectNode ob = Json.object(j);
                Map.Entry<String, JsonNode> first = Json.firstField(ob);
                String key = first.getKey();
                Class<? extends UnRAVLExtractor> ec = getPlugins()
                        .getExtractors().get(key);
                if (ec == null)
                    if (!bind.isObject())
                        throw new UnRAVLException("No defined extractor " + key);
                UnRAVLExtractor ex;
                try {
                    ex = ec.newInstance();
                    ex.extract(script, ob, this);
                } catch (InstantiationException e1) {
                    throw new UnRAVLException(
                            "Could not instantiate extractor " + key
                                    + " using class " + ec.getName(), e1);
                } catch (IllegalAccessException e1) {
                    throw new UnRAVLException(
                            "Could not instantiate extractor " + key
                                    + " using class " + ec.getName(), e1);
                }
            }
        } finally {
            // Must do this after making more bindings
            getRuntime().resetBindings();
        }
    }

    private ObjectNode statusAssertion(UnRAVL script) throws UnRAVLException {
        return UnRAVL.statusAssertion(script);
    }

    private HttpRequestBase newHttpRequest() throws UnRAVLException {
        switch (script.getMethod()) {
        case HEAD:
            return new HttpHead();
        case GET:
            return new HttpGet();
        case DELETE:
            return new HttpDelete();
        case PUT:
            HttpPut put = new HttpPut();
            attachBody(put);
            return put;
        case POST:
            HttpPost post = new HttpPost();
            attachBody(post);
            return post;
        case PATCH:
            HttpPatch patch = new HttpPatch();
            attachBody(patch);
            return patch;
        default:
            throw new UnRAVLException("Unknown method " + script.getMethod());
        }
    }

    public Header getResponseHeader(String headerName) {
        for (Header h : responseHeaders) {
            if (h.getName().equalsIgnoreCase(headerName))
                return h;
        }
        return null;
    }

    // read all the values in "env" and bind them to this instance's env
    // Scalars are bound as Java scalar types; JSON arrays and objects
    // are bound as JsonNode objects
    private void defineEnv() throws UnRAVLException {
        defineEnv(getScript());
    }

    static void defineEnv(UnRAVL script) throws UnRAVLException {
        if (script == null)
            return;
        defineEnv(script.getTemplate());
        JsonNode envNode = script.getRoot().get("env");
        if (script.getName() != null)
            script.bind("name", script.getName());
        if (envNode != null) {
            for (Map.Entry<String, JsonNode> e : Json.fields(envNode)) {
                String name = e.getKey();
                JsonNode n = e.getValue();
                Object value = null;
                if (n.isValueNode()) {
                    JsonToken t = n.asToken();
                    switch (t) {
                    case VALUE_FALSE:
                        value = Boolean.FALSE;
                        break;
                    case VALUE_TRUE:
                        value = Boolean.TRUE;
                        break;
                    case VALUE_NULL:
                        value = null;
                        break;
                    case VALUE_NUMBER_FLOAT:
                        value = new Double(n.toString());
                        break;
                    case VALUE_NUMBER_INT:
                        value = new Long(n.toString());
                        break;
                    case VALUE_STRING:
                        value = script.expand(n.textValue());
                        break;
                    default:
                        value = n;
                        break;
                    }
                } else {
                    value = Json.expand(n, script);
                }
                script.bind(name, value);
            }

        }

    }

    /**
     * Remove a binding from this script's environment. After this,
     * {@link #getVariable(String)} will return null and
     * {@link #isBound(String)} will return false
     * 
     * @param key
     *            the var name
     * @see #bind(String, Object)
     * @see #isBound(String)
     */
    public void unbind(String key) {
        // The contract for Binding does not include a remove operation,
        // nor does it say that getVariables() is active and not a read-only
        // view,
        // so this is dangerous and not guaranteed to work.
        getEnv().getVariables().remove(key);
    }

    /**
     * Return the value of a variable from this script's environment
     * 
     * @param key
     *            the variable name
     * @return the bound value, or null
     * @see #bind(String, Object)
     * @see #unbind(String)
     * @see #isBound(String)
     */
    public Object getVariable(String key) {
        return getEnv().getVariable(key);
    }

    /**
     * Test if a variable is bound in this script's environment
     * 
     * @param key
     *            the variable name
     * @return true if the variable is bound, else false
     * @see #bind(String, Object)
     * @see #unbind(String)
     */
    public boolean isBound(String key) {
        return getEnv().getVariables().containsKey(key);
    }

    public InputStream getResponseBodyAsInputStream() {
        ByteArrayInputStream i = new ByteArrayInputStream(getResponseBody()
                .toByteArray());
        return i;
    }

    public void executeAPI() throws UnRAVLException {
        if (script.getMethod() == null || script.getURI() == null) {
            logger.warn("Warning: Non-template script " + script.getName()
                    + " does not define an HTTP method or URI.");
            return;
        }
        long start = System.currentTimeMillis();
        String apiUri = script.getURI();
        apiUri = script.expand(apiUri);
        CloseableHttpClient httpclient = HttpClients.createSystem();
        HttpRequestBase request = newHttpRequest();
        try {
            setURI(apiUri);
            setMethod(script.getMethod());
            authenticate();
            request.setURI(new URI(getURI()));

            if (script.getRequestHeaders() != null)
                request.setHeaders(script.getRequestHeaders().toArray(
                        (new Header[script.getRequestHeaders().size()])));
            log(request, request.getURI());
            ResponseHandler<HttpResponse> responseHandler = new UnravlResponseHandler();
            HttpResponse response = httpclient
                    .execute(request, responseHandler);
            long end = System.currentTimeMillis();
            logger.info(script.getMethod() + " took " + (end - start)
                    + "ms, returned HTTP status " + response);
            setResponseHeaders(response.getAllHeaders());
            log(response);
            assertStatus(response);
        } catch (ClientProtocolException e) {
            throwException(e);
        } catch (IOException e) {
            throwException(e);
        } catch (URISyntaxException e) {
            throwException(e);
        } finally {
            try {
                httpclient.close();
            } catch (IOException e) {
                throwException(e);
            }
        }
    }

    private void setMethod(Method method) {
        this.method = method;
    }

    public void setURI(String uri) {
        this.uri = uri;
    }

    public String getURI() {
        return uri;
    }

    public Method getMethod() {
        return method;
    }

    public void bind(String varName, Object value) {
        getRuntime().bind(varName, value);
    }

    public ByteArrayOutputStream getRequestBody() {
        return requestBody;
    }

    public ByteArrayOutputStream getResponseBody() {
        return responseBody;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Header[] getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Header responseHeaders[]) {
        this.responseHeaders = responseHeaders;
    }

    public Binding getEnv() {
        return getRuntime().getBindings();
    }

    private void assertStatus(HttpResponse response)
            throws UnRAVLAssertionException, UnRAVLException {

        StatusAssertion sa = new StatusAssertion();
        sa.setScript(script);
        sa.setScriptlet(STATUS_ASSERTION);
        try {
            httpStatus = response.getStatusLine().getStatusCode();
            bind("status", new Integer(httpStatus));
            ObjectNode node = statusAssertion();
            if (node != null) {
                sa.check(script, node, UnRAVLAssertion.Stage.ASSERT, this);
            } else {
                if (httpStatus < 200 || httpStatus >= 300)
                    throw new UnRAVLAssertionException("http status "
                            + httpStatus + " not a 2xx status.");
            }
        } catch (UnRAVLAssertionException e) {
            failedAssertions.add(sa);
            throw e;
        }
    }

    public void setException(UnRAVLException exception) {
        this.exception = exception;
    }

    public UnRAVLException getException() {
        return exception;
    }

    /**
     * Wrap exception in an UnRAVLException (unless it already is one), then
     * throw the UnRAVLException
     * 
     * @param exception
     *            an exception
     * @throws UnRAVLException
     *             the wrapped exception
     */
    public void throwException(Exception exception) throws UnRAVLException {
        if (exception instanceof UnRAVLException)
            setException((UnRAVLException) exception);
        else
            setException(new UnRAVLException(exception));
        throw getException();
    }

    public UnRAVLRuntime getRuntime() {
        return script.getRuntime();
    }

    private ObjectNode statusAssertion() throws UnRAVLException {
        return statusAssertion(script);
    }

    private void attachBody(HttpEntityEnclosingRequestBase method) {
        if (requestBody != null) {
            HttpEntity entity = new InputStreamEntity(new ByteArrayInputStream(
                    requestBody.toByteArray()));
            method.setEntity(entity);
        }
    }

    private class UnravlResponseHandler implements
            ResponseHandler<HttpResponse> {

        @Override
        public HttpResponse handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
            if (response.getEntity() == null)
                return response;
            InputStream input = response.getEntity().getContent();
            if (input != null) {
                responseBody = new ByteArrayOutputStream();
                Binary.copy(input, responseBody);
                responseBody.close();
            } else
                responseBody = null;

            return response;
        }

    }

    private boolean runAssertions(Stage stage) throws UnRAVLException {
        return runAssertions(script, stage);
    }

    private boolean runAssertions(UnRAVL unravl, Stage stage)
            throws UnRAVLException {
        if (unravl == null)
            return true;
        if (!runAssertions(unravl.getTemplate(), stage))
            return false;
        JsonNode assertionNode = unravl.getRoot().get(stage.getName());
        if (assertionNode == null)
            return true;
        ArrayNode assertions = assertionArray(assertionNode, stage);
        for (int i = 0; i < assertions.size(); i++) {
            JsonNode s = assertions.get(i);
            ObjectNode assertionScriptlet = null;
            UnRAVLAssertion a = null;
            Class<? extends UnRAVLAssertion> aClass = null;
            try {
                if (s.isTextual()) {
                    assertionScriptlet = GroovyAssertion
                            .assertionScriptlet((TextNode) s);
                    a = new GroovyAssertion();
                } else {
                    String aName = Json.firstFieldName(s);
                    assertionScriptlet = Json.object(s);
                    aClass = getPlugins().getAssertions().get(aName);
                    if (aClass == null)
                        throw new UnRAVLException(
                                "No such assertion class registered for "
                                        + stage + " keyword " + aName);
                    a = aClass.newInstance();
                }
                a.setAssertion(assertionScriptlet);
                a.check(this.script, assertionScriptlet, stage, this);
                passedAssertions.add(a);
            } catch (InstantiationException e) {
                failedAssertions.add(BaseUnRAVLAssertion.of(script,
                        assertionScriptlet));
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                failedAssertions.add(BaseUnRAVLAssertion.of(script,
                        assertionScriptlet));
                logger.error(e.getMessage());
                throw new UnRAVLException("Assertion class " + aClass.getName()
                        + " cannot be instantiated.");
            } catch (UnRAVLAssertionException e) {
                failedAssertions.add(a);
                for (int j = i + 1; j < assertions.size(); j++) {
                    JsonNode skipped = assertions.get(j);
                    skippedAssertions.add(BaseUnRAVLAssertion.of(getScript(),
                            Json.object(skipped.isTextual() ? GroovyAssertion
                                    .assertionScriptlet((TextNode) skipped)
                                    : skipped)));
                }
                throw e;
            }

        }
        return failedAssertions.size() == 0;
    }

    private UnRAVLPlugins getPlugins() {
        return getRuntime().getPlugins();
    }

    /**
     * Convert the value of an assert or preconditions into an array.
     * 
     * @param val
     *            the "assert" or "preconditions" scriptlet
     * @param stage
     *            which stage is running
     * @return an array of JSON assertions. If the input scriptlet value is a
     *         string or an object, embed it in this array. If the value is
     *         already an array, return it.
     * @throws UnRAVLException
     *             if the value of the assertion node is not one of the three
     *             expected forms.
     */
    public static ArrayNode assertionArray(JsonNode val, Stage stage)
            throws UnRAVLException {
        if (val == null)
            return null;
        if (val.isArray())
            return (ArrayNode) val;
        if (val.isTextual())
            return Json.wrapInArray(val);
        if (val.isObject())
            return Json.wrapInArray(val);
        throw new UnRAVLException("Value of " + stage.getName()
                + " must be a string, an object, or an array.");
    }

    private void log(HttpRequestBase request, URI uri) {
        logger.info(script.getMethod() + " " + uri);
        for (Header h : request.getAllHeaders()) {
            if (h.getName() == "Authorization") // Don't log easily decoded
                                                // credentials
                logger.info(h.getName() + ": ************");
            else
                logger.info(h);
        }
        log("request body:", requestBody, request.getHeaders("Content-Type"));
    }

    private void log(HttpResponse response) {
        for (Header h : response.getAllHeaders())
            logger.info(h);
        log("response body:", responseBody, response.getHeaders("Content-Type"));
    }

    private void log(String label, ByteArrayOutputStream bytes, Header[] headers) {
        if (script.bodyIsTextual(headers))
            try {
                if (bytes == null) {
                    if (getMethod() != Method.HEAD)
                        logger.warn("Warning: Non-HEAD request returned a text Content-Type header but defines no body.");
                    return;
                }
                if (logger.isInfoEnabled()) {
                    // TODO: pretty print JSON, XML
                    logger.info(label);
                    bytes.writeTo(System.out);
                    System.out.println();
                }
            } catch (IOException e) {
                logger.error(e);
            }
    }

    public UnRAVL getScript() {
        return script;
    }

    public List<UnRAVLAssertion> getPassedAssertions() {
        return passedAssertions;
    }

    public List<UnRAVLAssertion> getFailedAssertions() {
        return failedAssertions;
    }

    public List<UnRAVLAssertion> getSkippedAssertions() {
        return skippedAssertions;
    }

}
