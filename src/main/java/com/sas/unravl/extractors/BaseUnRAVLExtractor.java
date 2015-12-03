// Copyright (c) 2014, SAS Institute Inc., Cary, NC, USA, All Rights Reserved
package com.sas.unravl.extractors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.BaseUnRAVLPlugin;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.UnRAVLPlugins;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * The base implementation of an {@link UnRAVL} script extractor, which is run
 * from the "bind" element of the script.
 * <p>
 * Extractor objects run after the script makes the API call. and can bind
 * values from headers or response body, putting the result in the environment
 * or elsewhere. An {@link UnRAVL} script will load UnRAVLExtractor objects
 * while executing the "bind" member of the script. The first field in the
 * assertion member is used as the key, i.e.
 *
 * <pre>
 * { "headers" : [ ... ] }
 * </pre>
 *
 * is a extractor which uses the key "headers". The extractor class is found in
 * the {@link UnRAVLPlugins} list of extractors and instantiated. Then, the
 * {@link #extract(UnRAVL, ObjectNode, ApiCall)} method is run, passing the
 * currently executing {@link UnRAVL} script and the JsonNode element that
 * defines the extractor.
 * <p>
 * Extractors should extend {@link BaseUnRAVLExtractor} and their extract()
 * method should invoke super.extract(script,node)
 *
 * @author David.Biesack@sas.com
 */
public class BaseUnRAVLExtractor extends BaseUnRAVLPlugin
        implements UnRAVLExtractor {

    protected static boolean unwrapOption(ObjectNode scriptlet)
            throws UnRAVLException {
        return booleanOption(scriptlet, "unwrap");
    }

    protected static boolean booleanOption(ObjectNode scriptlet,
            String optionName) throws UnRAVLException {
        JsonNode wrap = scriptlet.get("unwrap");
        if (wrap == null)
            return false;
        if (wrap.isBoolean())
            return wrap.booleanValue();
        String msg = String.format(
                "%s option in %s must be a Boolean value; found %s", optionName,
                key(scriptlet), wrap);
        throw new UnRAVLException(msg);
    }

    @Override
    public ObjectNode extractor() {
        return getScriptlet();
    }

    @Override
    public void setExtractor(ObjectNode extractor) {
        setScriptlet(extractor);
    }

    @Override
    public void extract(UnRAVL current, ObjectNode spec, ApiCall call)
            throws UnRAVLException {
        setScript(current);
        setExtractor(spec);
        setCall(call);
    }

    /**
     * Used to register the extractor class with the UnRAVLRuntime This is
     * called from Spring when the UnRAVLPlugins class is loaded.
     *
     * @param plugins
     *            a plugins instance
     */
    @Autowired
    public void setPluginManager(UnRAVLPlugins plugins) {
        plugins.addExtractor(this.getClass());
    }

    /**
     * Returns the name of this extractor. THis is the name (key) of the first
     * value in the object.
     * 
     * @param extractor
     *            the JSON object containing an UnRAVL extractor
     * @return the key (name) of the first value
     */
    protected static String key(ObjectNode extractor) {
        return extractor.fields().next().getKey();
    }
}
