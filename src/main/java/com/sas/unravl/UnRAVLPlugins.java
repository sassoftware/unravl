// Copyright (c) 2014, SAS Institute Inc., Cary, NC, USA, All Rights Reserved
package com.sas.unravl;

import com.sas.unravl.annotations.UnRAVLAssertionPlugin;
import com.sas.unravl.annotations.UnRAVLAuthPlugin;
import com.sas.unravl.annotations.UnRAVLExtractorPlugin;
import com.sas.unravl.annotations.UnRAVLRequestBodyGeneratorPlugin;
import com.sas.unravl.assertions.UnRAVLAssertion;
import com.sas.unravl.auth.CredentialsProvider;
import com.sas.unravl.auth.NetrcCredentialsProvider;
import com.sas.unravl.auth.UnRAVLAuth;
import com.sas.unravl.extractors.UnRAVLExtractor;
import com.sas.unravl.generators.UnRAVLRequestBodyGenerator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Manages the mappings of keywords to plugin implementation classes.
 * 
 * @author David.Biesack@sas.com
 */
public class UnRAVLPlugins {

    private static final Logger logger = Logger.getLogger(UnRAVLRuntime.class);

    private Map<String, Class<? extends UnRAVLRequestBodyGenerator>> requestBodyGenerators = new HashMap<String, Class<? extends UnRAVLRequestBodyGenerator>>();
    private Map<String, Class<? extends UnRAVLAssertion>> assertions = new HashMap<String, Class<? extends UnRAVLAssertion>>();
    private Map<String, Class<? extends UnRAVLExtractor>> extractors = new HashMap<String, Class<? extends UnRAVLExtractor>>();
    private Map<String, Class<? extends UnRAVLAuth>> auth = new HashMap<String, Class<? extends UnRAVLAuth>>();

    private CredentialsProvider credentialsProvider;

    private RestTemplate defaultRestTemplate;

    // must be "Groovy", "groovy", "JavaScript", "js", "javascript", or another
    // valid ScriptEngine name
    @Value("#{systemProperties['unravl.script.language'] ?: 'groovy'}")
    private String scriptLanguage = "groovy";

    public void setScriptLanguage(String scriptLanguage) {
        this.scriptLanguage = scriptLanguage;
    }

    /**
     * Return a credentials provider - the instance assigned in the setter, or a
     * default {@link NetrcCredentialsProvider}
     * 
     * @return the assigned CredentialsProvider
     */
    public CredentialsProvider getCredentialsProvider() {
        return (credentialsProvider == null) ? new NetrcCredentialsProvider()
                : credentialsProvider;
    }

    /**
     * Assign a credentials provider
     * 
     * @param credentialsProvider
     *            the instance which can get userid/password for a host
     */
    public void setCredentialsProvider(CredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    public String getScriptLanguage() {
        if (scriptLanguage == null || scriptLanguage.trim().length() == 0)
            return "groovy";
        else
            return scriptLanguage;
    }

    public ScriptEngine interpreter(String lang) throws UnRAVLException {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager
                .getEngineByName(lang == null ? getScriptLanguage() : lang);
        if (engine == null) {
            logSupportedScriptEngines();
            throw new UnRAVLException(String.format(
                    "No script engine available for %sscript lanaguge %s",
                    lang == null ? "unravl.script.langauge " : "",
                    getScriptLanguage()));
        }
        return engine;
    }

    public void addAssertion(Class<? extends UnRAVLAssertion> class1) {
        UnRAVLAssertionPlugin a = class1
                .getAnnotation(UnRAVLAssertionPlugin.class);
        String[] keys = a.value();
        for (String key : keys) {
            logger.trace("Define assertion '" + key + "' via " + class1);
            assertions.put(key, class1);
        }
    }

    public void addAuth(Class<? extends UnRAVLAuth> class1) {
        UnRAVLAuthPlugin a = class1.getAnnotation(UnRAVLAuthPlugin.class);
        String[] keys = a.value();
        for (String key : keys) {
            logger.trace("Define auth '" + key + "' via " + class1);
            auth.put(key, class1);
        }
    }

    public void addExtractor(Class<? extends UnRAVLExtractor> class1) {
        UnRAVLExtractorPlugin a = class1
                .getAnnotation(UnRAVLExtractorPlugin.class);
        for (String key : a.value()) {
            logger.trace("Define extractor '" + key + "' via " + class1);
            extractors.put(key, class1);
        }

    }

    public void addRequestBodyGenerator(
            Class<? extends UnRAVLRequestBodyGenerator> class1) {
        UnRAVLRequestBodyGeneratorPlugin a = class1
                .getAnnotation(UnRAVLRequestBodyGeneratorPlugin.class);
        for (String key : a.value()) {
            logger.trace("Define body generator '" + key + "' via " + class1);
            requestBodyGenerators.put(key, class1);
        }
    }

    public Map<String, Class<? extends UnRAVLAssertion>> getAssertions() {
        return assertions;
    }

    public Map<String, Class<? extends UnRAVLRequestBodyGenerator>> getBodyGenerators() {
        return requestBodyGenerators;
    }

    public Map<String, Class<? extends UnRAVLExtractor>> getExtractors() {
        return extractors;
    }

    public Map<String, Class<? extends UnRAVLAuth>> getAuth() {
        return auth;
    }

    /**
     * log the availability of scripting engines supported in this environment.
     */
    public static void logSupportedScriptEngines() {
        ScriptEngineManager manager = new ScriptEngineManager();
        logger.error("Available Script Engines:");
        for (final ScriptEngineFactory scriptEngine : manager
                .getEngineFactories()) {
            logger.error(scriptEngine.getEngineName() + " "
                    + scriptEngine.getEngineVersion());
            logger.error("\tLanguage: " + scriptEngine.getLanguageName() + " "
                    + scriptEngine.getLanguageVersion());
            StringBuilder es = new StringBuilder();
            for (final String engineAlias : scriptEngine.getNames()) {
                es.append(engineAlias).append(",");
            }
            logger.error("\tAliases: " + es.toString());
        }
    }

    /**
     * Set the default RestTemplate instance that UnRAVL and ApiCall will use.
     * 
     * @param restTemplate
     *            the default RestTemplate instance
     */
    public void setRestTemplate(RestTemplate restTemplate) {
        this.defaultRestTemplate = restTemplate;
    }

    /**
     * @return the default RestTemplate instance that UnRAVL and ApiCall will
     *         use
     */
    public RestTemplate getRestTemplate() {
        return defaultRestTemplate == null ? newRestTemplate()
                : defaultRestTemplate;
    }

    /**
     * This RestTemplate uses HttpComponentsClientHttpRequestFactory that
     * follows redirect for GET and HEAD calls. We use
     * HttpComponentsClientHttpRequestFactory because the default
     * HttpUrlConnection used by Spring throws I/O exceptions from
     * response.getBody() when the remote call returns 4xx responses, which
     * means the client can't read the response body that accompanies such
     * errors. The Apache HTTP Commons implementation of HttpUrlConnection does
     * not throw such exceptions, so UnRAVL can read the response body in all
     * cases.
     * <p>
     * This instance also sets an error handler which ignores all errors, so
     * that ApiCall can extract the HTTP response code, headers, and response
     * body.
     * </p>
     * 
     * @return a RestTemplate instance to use for making HTTP calls when running
     *         UnRAVL scripts.
     */
    public static RestTemplate newRestTemplate() {

        final ResponseErrorHandler ignoreResponseErrors = new ResponseErrorHandler() {

            @Override
            public void handleError(ClientHttpResponse response)
                    throws IOException {
                // NO OP. This is only called if hasError returns true,
                // but below we always return false, so we can extract the body
                // and the HTTP status code, even for 4xx and 5xx errors.
            }

            @Override
            public boolean hasError(ClientHttpResponse response)
                    throws IOException {
                return false;
            }
        };
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        final HttpClient httpClient = HttpClientBuilder.create()
                .setRedirectStrategy(new UnRAVLRedirectStrategy()).build();
        factory.setHttpClient(httpClient);

        RestTemplate rt = new RestTemplate(factory);
        rt.setErrorHandler(ignoreResponseErrors);
        return rt;
    }

    private static final class UnRAVLRedirectStrategy extends
            DefaultRedirectStrategy {

        @Override
        protected boolean isRedirectable(final String method) {
            return HttpGet.METHOD_NAME.equalsIgnoreCase(method) //
                    || HttpHead.METHOD_NAME.equalsIgnoreCase(method);
        }
    }
}
