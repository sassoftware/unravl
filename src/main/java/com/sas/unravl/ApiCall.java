package com.sas.unravl;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.sas.unravl.assertions.BaseUnRAVLAssertion;
import com.sas.unravl.assertions.StatusAssertion;
import com.sas.unravl.assertions.UnRAVLAssertion;
import com.sas.unravl.assertions.UnRAVLAssertion.Stage;
import com.sas.unravl.assertions.UnRAVLAssertionException;
import com.sas.unravl.auth.UnRAVLAuth;
import com.sas.unravl.extractors.UnRAVLExtractor;
import com.sas.unravl.generators.Binary;
import com.sas.unravl.generators.JsonRequestBodyGenerator;
import com.sas.unravl.generators.UnRAVLRequestBodyGenerator;
import com.sas.unravl.util.Json;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.apache.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

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

    private boolean isCanceled() {
        return getScript().getRuntime().isCanceled();
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
                    condition = script.eval(condS);
                } catch (RuntimeException e) {
                    logger.error("script '" + condS
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
        if (isCanceled() || script == null)
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
            Object ref = script.binding(value);
            if (ref instanceof JsonNode) {
                return true;
            }
        }
        return false;
    }

    private void extract() throws UnRAVLException {
        extract(script);
    }

    private void extract(UnRAVL script) throws UnRAVLException {
        try {
            if (isCanceled() || script == null)
                return;
            extract(script.getTemplate());
            JsonNode bind = script.getRoot().get("bind");
            if (bind == null)
                return;
            if (bind.isObject()) {
                bind = Json.wrapInArray(bind);
            }
            for (JsonNode j : Json.array(bind)) {
                if (isCanceled())
                    return;
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
     * {@link #getVariable(String)} will return null and {@link #bound(String)}
     * will return false
     * 
     * @param key
     *            the var name
     * @see #bind(String, Object)
     * @see #bound(String)
     */
    public void unbind(String key) {
        getRuntime().unbind(key);
    }

    /**
     * Return the value of a variable from this script's environment
     * 
     * @param key
     *            the variable name
     * @return the bound value, or null
     * @see #bind(String, Object)
     * @see #unbind(String)
     * @see #bound(String)
     */
    public Object getVariable(String key) {
        return getRuntime().binding(key);
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
    public boolean bound(String key) {
        return getRuntime().bound(key);
    }

    public InputStream getResponseBodyAsInputStream() {
        ByteArrayInputStream i = new ByteArrayInputStream(getResponseBody()
                .toByteArray());
        return i;
    }

    public void executeAPI() throws UnRAVLException {
        if (isCanceled())
            return;
        if (script.getMethod() == null || script.getURI() == null) {
            logger.warn("Warning: Non-template script " + script.getName()
                    + " does not define an HTTP method or URI.");
            return;
        }
        setMethod(script.getMethod());
        setURI(script.expand(script.getURI()));
        RestTemplate restTemplate = getRuntime().getPlugins().getRestTemplate();
        HttpHeaders headers = mapHeaders(script.getRequestHeaders());
        executeAPIWithRestTemplate(restTemplate, headers);
    }

    // ApiCall originally invoked the API via Apache HTTP Client
    // and the members of ApiCall still reflect that.
    // If invoking with RestTemplate, we must map between the
    // Apache Headers and Spring headers representations.
    private void executeAPIWithRestTemplate(RestTemplate restTemplate,
            final HttpHeaders headers) throws UnRAVLException {

        // We're using the requestcallback here and responseextractor below in
        // case the request or response is binary.
        final RequestCallback requestCallback = new RequestCallback() {
            @Override
            public void doWithRequest(final ClientHttpRequest request)
                    throws IOException {
                request.getHeaders().putAll(headers);
                if (requestBody != null)
                    request.getBody().write(requestBody.toByteArray());
                ;
            }
        };
        ResponseExtractor<InternalResponse> responseExtractor = new ResponseExtractor<InternalResponse>() {
            @Override
            public InternalResponse extractData(ClientHttpResponse response)
                    throws IOException {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Binary.copy(response.getBody(), baos);
                return new InternalResponse(response.getStatusCode(),
                        baos.toByteArray(), response.getHeaders());
            }
        };
        try {
            authenticate();
        } catch (IOException e) {
            throwException(e);
        }

        long start = System.currentTimeMillis();
        InternalResponse response = restTemplate.execute(getURI(),
                HttpMethod.valueOf(method.name()), requestCallback,
                responseExtractor);
        setResponseHeaders(mapHeaders(response.headers));
        httpStatus = response.status.value();
        responseBody = new ByteArrayOutputStream();
        try {
            responseBody.write(response.responseBody);
        } catch (IOException e) {
            throwException(e);
        }
        long end = System.currentTimeMillis();

        logger.info(script.getMethod() + " took " + (end - start)
                + "ms, returned HTTP status " + response.status);
        log("Response body:", responseBody, "Response headers:",
                response.headers);
        assertStatus(response.status.value());
    }

    private class InternalResponse {
        private HttpStatus status;
        private byte[] responseBody;
        private HttpHeaders headers;

        public InternalResponse(HttpStatus status, byte[] responseBody,
                HttpHeaders headers) {
            super();
            this.status = status;
            this.responseBody = responseBody;
            this.headers = headers;
        }
    }

    // Convert from Apache Headers Spring Headers
    private HttpHeaders mapHeaders(List<Header> requestHeaders) {
        HttpHeaders headers = new HttpHeaders();
        for (Header h : requestHeaders)
            headers.add(h.getName(), h.getValue());
        return headers;
    }

    // Convert from Spring Headers to Apache Headers
    private Header[] mapHeaders(HttpHeaders responseHeaders) {
        ArrayList<Header> h = new ArrayList<Header>(responseHeaders.size());
        for (Entry<String, List<String>> es : responseHeaders.entrySet()) {
            String name = es.getKey();
            for (String v : es.getValue()) {
                Header header = new BasicHeader(name, v);
                h.add(header);
            }
        }
        return h.toArray(new Header[h.size()]);
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

    // public Binding getEnv() {
    // return getRuntime().getBindings();
    // }

    private void assertStatus(int httpStatus) throws UnRAVLAssertionException,
            UnRAVLException {
        StatusAssertion sa = new StatusAssertion();
        sa.setScript(script);
        sa.setScriptlet(STATUS_ASSERTION);
        try {
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

    private boolean runAssertions(Stage stage) throws UnRAVLException {
        return runAssertions(script, stage);
    }

    private boolean runAssertions(UnRAVL unravl, Stage stage)
            throws UnRAVLException {
        if (isCanceled() || unravl == null)
            return true;
        if (!runAssertions(unravl.getTemplate(), stage))
            return false;
        if (isCanceled())
            return true;
        JsonNode assertionNode = unravl.getRoot().get(stage.getName());
        if (assertionNode == null)
            return true;
        ArrayNode assertions = assertionArray(assertionNode, stage);
        for (int i = 0; !isCanceled() && i < assertions.size(); i++) {
            JsonNode s = assertions.get(i);
            ObjectNode assertionScriptlet = null;
            UnRAVLAssertion a = null;
            Class<? extends UnRAVLAssertion> aClass = null;
            try {
                if (s.isTextual()) {
                    ObjectNode o = new ObjectNode(JsonNodeFactory.instance);
                    o.set(getRuntime().getScriptLanguage(), (TextNode) s);
                    s = o;
                }
                String aName = Json.firstFieldName(s);
                assertionScriptlet = Json.object(s);
                aClass = getPlugins().getAssertions().get(aName);
                if (aClass == null)
                    throw new UnRAVLException(
                            "No such assertion class registered for " + stage
                                    + " keyword " + aName);
                a = aClass.newInstance();
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
                    if (skipped.isTextual()) {
                        ObjectNode g = new ObjectNode(JsonNodeFactory.instance);
                        g.set("groovy", (TextNode) skipped);
                        skipped = g;
                    }
                    skippedAssertions.add(BaseUnRAVLAssertion.of(getScript(),
                            Json.object(skipped)));
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

    private void log(String bodyLabel, ByteArrayOutputStream bytes,
            String headersLabel, HttpHeaders headers) {

        if (headers != null && headers.size() > 0) {
            logger.info(headersLabel);
            Header hs[] = mapHeaders(headers);
            for (Header h : hs) {
                if (h.getName() == "Authorization") // Don't log easily decoded
                                                    // credentials
                    logger.info(h.getName() + ": ************");
                else
                    logger.info(h);
            }
        }
        MediaType contentType = headers.getContentType();
        if (contentType == null)
            return;
        Header ct[] = new Header[] { new BasicHeader("Content-Type",
                contentType.toString()) };
        if (script.bodyIsTextual(ct))
            try {
                if (bytes == null) {
                    if (getMethod() != Method.HEAD)
                        logger.warn("Warning: Non-HEAD request returned a text Content-Type header but defines no body.");
                    return;
                }
                if (logger.isInfoEnabled()) {
                    logger.info(bodyLabel);
                    if (script.bodyIsJson(ct)) {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            mapper.enable(SerializationFeature.INDENT_OUTPUT);
                            JsonNode json = Json.parse(bytes.toString("UTF-8"));
                            ByteArrayOutputStream os = new ByteArrayOutputStream();
                            os.write(mapper.writeValueAsBytes(json));
                            os.close();
                            bytes = os;
                        } catch (UnRAVLException e) {
                            // ignore parse/format errors; just print bytes w/o
                            // pretty print.
                        }
                    }
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
