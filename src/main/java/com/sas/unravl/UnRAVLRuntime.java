package com.sas.unravl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import com.sas.unravl.annotations.UnRAVLAssertionPlugin;
import com.sas.unravl.annotations.UnRAVLExtractorPlugin;
import com.sas.unravl.assertions.UnRAVLAssertion;
import com.sas.unravl.assertions.UnRAVLAssertionException;
import com.sas.unravl.generators.UnRAVLRequestBodyGenerator;
import com.sas.unravl.util.Json;
import com.sas.unravl.util.VariableResolver;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.SimpleBindings;

import org.apache.log4j.Logger;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * The runtime environment for running UnRAVL scripts. The runtime contains the
 * bindings which are accessed and set by scripts, and provides environment
 * expansion of strings. The runtime also contains the global mappings of
 * assertions, extractors, and request body generators, and a map of scripts and
 * templates
 *
 * @author DavidBiesack@sas.com
 */
@Component
public class UnRAVLRuntime {

    private static final Logger logger = Logger.getLogger(UnRAVLRuntime.class);
    private Map<String, Object> env; // script variables
    private Map<String, UnRAVL> scripts = new LinkedHashMap<String, UnRAVL>();
    private Map<String, UnRAVL> templates = new LinkedHashMap<String, UnRAVL>();
    // a history of the API calls we've made in this runtime
    private ArrayList<ApiCall> calls = new ArrayList<ApiCall>();
    private int failedAssertionCount;

    // used to expand variable references {varName} in strings:
    private VariableResolver variableResolver;
    private String scriptLanguage;
    private boolean canceled;

    private RestTemplate restTemplate = new RestTemplate();

    public UnRAVLRuntime() {
        this(new LinkedHashMap<String, Object>());
    }

    public UnRAVLRuntime(Map<String, Object> environment) {
        configure();
        this.env = environment;
        setScriptLanguage(getPlugins().getScriptLanguage());
        for (Map.Entry<Object, Object> e : System.getProperties().entrySet())
            bind(e.getKey().toString(), e.getValue());
        bind("failedAssertionCount", Integer.valueOf(0));
        resetBindings();
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * @return this runtime's default script language
     */
    public String getScriptLanguage() {
        return scriptLanguage;
    }

    /**
     * Set this runtime's default script language, used to evaluate "if"
     * conditions, "links"/"hrefs" from expressions, and string assertions
     * 
     * @param language
     *            the script language, such as "groovy" or "javascript"
     */
    public void setScriptLanguage(String language) {
        this.scriptLanguage = language;
    }

    /**
     * Return a script engine that can evaluate (interpret) script strings. The
     * returned engine is determined by the UnRAVLPlugins; the default is a
     * Groovy engine if Groovy is available. The system property
     * unravl.script.language may be set to a valid engine such as JavaScript;
     * the default is "Groovy". Run with -Dunravl.script.language=
     * <em>language</em> such as -Dunravl.script.language=JavaScript to choose
     * an alternate language
     * 
     * @return a script engine
     * @throws UnRAVLException
     *             if no interpreter exists for the configured script language
     */
    public ScriptEngine interpreter() throws UnRAVLException {
        return interpreter(null);
    }

    /**
     * Return a script engine that can evaluate (interpret) script strings using
     * the named script language
     * 
     * @param lang
     *            the script language, such as "groovy' or "javascript"
     * @return a script interpreter
     * @throws UnRAVLException
     *             is no engine exists for the script language <var>lang</var>
     */
    public ScriptEngine interpreter(String lang) throws UnRAVLException {
        ScriptEngine engine = getPlugins().interpreter(lang);
        SimpleBindings bindings = new SimpleBindings(getBindings());
        engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
        return engine;
    }

    public Map<String, Object> getBindings() {
        return env;
    }

    public int getFailedAssertionCount() {
        return failedAssertionCount;
    }

    public void incrementFailedAssertionCount() {
        failedAssertionCount++;
        bind("failedAssertionCount", Integer.valueOf(failedAssertionCount));
    }

    public Map<String, UnRAVL> getScripts() {
        return scripts;
    }

    private Map<String, UnRAVL> getTemplates() {
        return templates;
    }

    private static ClassPathXmlApplicationContext ctx = null;

    /**
     * UnRAVL can be configured with Spring by loading the Spring config
     * classpath:/META-INF/spring/unravlApplicationContext.xml . If that has not
     * been done, this method initializes Spring. Spring performs a
     * component-scan for assertions, body generators, and extractors. As each
     * such component is loaded, it's runtime property is {@literal @} autowired
     * to a UnRAVLRuntime instance this calls the register*() methods. See
     * {@link UnRAVLRequestBodyGenerator}, {@link UnRAVLAssertionPlugin}, and
     * {@link UnRAVLExtractorPlugin}.
     */
    public static synchronized void configure() {
        if (ctx != null)
            return;

        // Configure Spring. Works on Unix; fails on Windows?
        String[] contextXml = new String[] {
                "/META-INF/spring/unravlApplicationContext.xml" };
        ctx = new ClassPathXmlApplicationContext(contextXml);
        assert (ctx != null);

        // Configure jsonPath to use Jackson
        Configuration.Defaults jsonPathConfig = new Configuration.Defaults() {

            private final JsonProvider jsonProvider = new JacksonJsonProvider();
            private final MappingProvider mappingProvider = new JacksonMappingProvider();

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        };

        Configuration.setDefaults(jsonPathConfig);
    }

    public UnRAVLRuntime execute(String[] argv) throws UnRAVLException {
        // for now, assume each command line arg is an UnRAVL script
        canceled = false;
        for (String scriptFile : argv) {
            try {
                List<JsonNode> roots = read(scriptFile);
                if (isCanceled())
                    break;
                execute(roots);
            } catch (IOException e) {
                logger.error(e.getMessage() + " while running UnRAVL script "
                        + scriptFile);
                throw new UnRAVLException(e);
            } catch (UnRAVLException e) {
                logger.error(e.getMessage() + " while running UnRAVL script "
                        + scriptFile);
                throw (e);
            }
        }
        return this;
    }

    public void execute(JsonNode... roots)
            throws JsonProcessingException, IOException, UnRAVLException {
        execute(Arrays.asList(roots));
    }

    public void execute(List<JsonNode> listOfScripts)
            throws JsonProcessingException, IOException, UnRAVLException {
        canceled = false;
        for (int i = 0; !isCanceled() && i < listOfScripts.size(); i++) {
            JsonNode root = listOfScripts.get(i);
            if (root.isTextual()) {
                String ref = root.textValue();
                if (ref.startsWith(UnRAVL.REDIRECT_PREFIX)) {
                    String sublist = expand(
                            ref.substring(UnRAVL.REDIRECT_PREFIX.length()));
                    execute(read(sublist));
                    continue;
                }
            }
            String label = "";
            try {
                UnRAVL u = null;
                if (root.isTextual()) {
                    String name = root.textValue();
                    label = name;
                    u = getScripts().get(name);
                    if (u == null) {
                        throw new UnRAVLException(String.format(
                                "No such UnRAVL script named '%s'", name));
                    }
                } else
                    u = new UnRAVL(this, (ObjectNode) root, restTemplate);
                label = u.getName();
                u.run();
            } catch (UnRAVLAssertionException e) {
                logger.error(e.getMessage() + " while running UnRAVL script "
                        + label);
                incrementFailedAssertionCount();
            } catch (RuntimeException rte) {
                if (rte.getCause() instanceof UnRAVLException) { // tunneled
                                                                 // exception
                    UnRAVLException e = (UnRAVLException) rte.getCause();
                    throw e;
                } else
                    throw rte;
            }

        }

    }

    public UnRAVLRuntime execute(String scriptFile) throws UnRAVLException {
        canceled = false;
        // for now, assume each command line arg is an UnRAVL script
        try {
            List<JsonNode> roots = read(scriptFile);
            execute(roots);
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new UnRAVLException(e);
        } catch (UnRAVLException e) {
            logger.error(e.getMessage());
            throw (e);
        }
        return this;
    }

    public boolean isCanceled() {
        return canceled;
    }

    /** Stop execution. */
    public void cancel() {
        this.canceled = true;
    }

    /**
     * Expand environment variables in a string. For example, if string is
     *
     * <pre>
     * &quot;{time} is the time for {which} {who} to come to the aid of their {where}&quot;
     * </pre>
     *
     * and the environment contains the bindings
     *
     * <pre>
     * time = "Mon, Aug 4, 2014"
     * which = 16
     * who = "hackers
     * where = "API"
     * </pre>
     *
     * the result will be
     *
     * <pre>
     * &quot;Mon, Aug 4, 2014 is the time for 16 hackers to come to the aid of their API&quot;
     * </pre>
     *
     * The toString() value of each binding in the environment is substituted.
     * An optional notation is allowed to provide a default value if a variable
     * is not bound. <code>{varName|alt text}</code> will resolve to the value
     * of varName if it is bound, or to alt text if varName is not bound. The
     * alt text may also contain embedded variable expansions.
     *
     * @param text
     *            an input string
     * @return the string, with environment variables replaced.
     */
    public String expand(String text) {
        if (variableResolver == null) {
            variableResolver = new VariableResolver(getBindings());
        }
        return variableResolver.expand(text);
    }

    public void bind(String varName, Object value) {
        if (VariableResolver.isUnicodeCodePointName(varName)) {
            UnRAVLException ue = new UnRAVLException(String.format(
                    "Cannot rebind special Unicode variable %s", varName));
            throw new RuntimeException(ue);
        }
        env.put(varName, value);
        logger.trace("bind(" + varName + "," + value + ")");
        resetBindings();
    }

    public boolean bound(String varName) {
        return env.containsKey(varName);
    }

    public Object binding(String varName) {
        return env.get(varName);
    }

    /**
     * Call this when bindings have changed.
     */
    public void resetBindings() {
        // null signals that we need to recreate the resolver after
        // the bindings have changed.
        // if null, variableResolver gets recreated if needed in expand(String).
        //
        // We no longer need to reset the resolver instance
        // since we do not copy the environment.
        // This used to do:

        /* variableResolver = null; */
    }

    public List<JsonNode> read(String scriptFile)
            throws JsonProcessingException, IOException, UnRAVLException {
        JsonNode root;
        List<JsonNode> roots = new ArrayList<JsonNode>();
        ObjectMapper mapper = new ObjectMapper();
        URL url = null;
        try {
            url = new URL(scriptFile);
        } catch (MalformedURLException e) {
        }
        if (url != null)
            root = mapper.readTree(url);
        else {
            File f = new File(scriptFile);
            root = mapper.readTree(f);
        }

        if (root.isArray()) {
            for (JsonNode next : Json.array(root)) {
                roots.add(next);
            }
        } else {
            roots.add(root);
        }
        return roots;
    }

    public int report() {
        int failed = (calls.size() == 0 ? 1 : 0);
        String separator = "";
        for (ApiCall call : calls) {
            UnRAVL script = call.getScript();
            String title = "Script '" + script.getName() + "' "
                    + (call.getMethod() == null ? "<no method>"
                            : script.getMethod().toString())
                    + " "
                    + (call.getURI() == null ? "<no URI>" : call.getURI());
            System.out.print(separator);
            for (int i = title.length(); i > 0; i--)
                System.out.print('-');
            System.out.println();
            System.out.println(title);

            if (call.getException() != null) {
                System.out.println(
                        "Caught exception running test " + script.getName()
                                + " " + call.getMethod() + " " + call.getURI());
                System.out.println(call.getException().getMessage());
                System.out.flush();
                failed++;
            }
            report(call.getPassedAssertions(), "Passed");
            failed += report(call.getFailedAssertions(), "Failed");
            report(call.getSkippedAssertions(), "Skipped");
            separator = System.getProperty("line.separator").toString();
        }
        if (canceled)
            System.out.println("UnRAVL script execution was canceled.");
        return failed;
    }

    private int report(List<UnRAVLAssertion> as, String label) {
        if (as.size() > 0) {
            System.out.println(as.size() + " " + label + ":");
            for (UnRAVLAssertion a : as) {
                System.out.println(
                        label + " " + a.getStage().getName() + " " + a);
                System.out.flush();
                UnRAVLAssertionException e = a.getUnRAVLAssertionException();
                if (e != null) {
                    System.out.println(
                            e.getClass().getName() + " " + e.getMessage());
                }
            }
        }
        return as.size();
    }

    public List<ApiCall> getApiCalls() {
        return calls;
    }

    public void addApiCall(ApiCall apiCall) {
        calls.add(apiCall);
    }

    public UnRAVLPlugins getPlugins() {
        return ctx.getBean(UnRAVLPlugins.class);
    }

    public UnRAVL getTemplate(String templateName) {
        return getTemplates().get(templateName);
    }

    public void setTemplate(String name, UnRAVL template) {
        // TODO: check for template cycles
        if (hasTemplate(name)) {
            logger.warn("Replacing template " + name);
        }
        getTemplates().put(name, template);

    }

    public boolean hasTemplate(String name) {
        return getTemplates().containsKey(name);
    }

    public void unbind(String key) {
        env.remove(key);
    }

}
