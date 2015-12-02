package com.sas.unravl.generators;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.BaseUnRAVLPlugin;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.UnRAVLPlugins;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * The base implementation of an {@link UnRAVL} script request body generator,
 * which is run from the "body" element of the script.
 * <p>
 * Request body generator objects run before the script makes the API call. An
 * {@link UnRAVL} script will load {@link UnRAVLRequestBodyGenerator} objects
 * while executing the "body" member of the script. The first field in the
 * assertion member is used as the key, i.e.
 * 
 * <pre>
 * { "json" : <em>json-body</em>
 * </pre>
 * 
 * is a body generator which uses the key "json". The body generator class is
 * found in the {@link UnRAVLPlugins} list of body generator classes and
 * instantiated. Then, the {@link #getBody(UnRAVL, ObjectNode, ApiCall)} method
 * is run, passing the currently executing {@link UnRAVL} script and the
 * JsonNode scriptlet element that defines the body generator.
 * <p>
 * Body generators should extend {@link BaseUnRAVLRequestBodyGenerator} and
 * their getBody() method should invoke super.getBody(script,node)
 * 
 * @author David.Biesack@sas.com
 */
public class BaseUnRAVLRequestBodyGenerator extends BaseUnRAVLPlugin
        implements UnRAVLRequestBodyGenerator {

    @Override
    public InputStream getBody(UnRAVL script, ObjectNode scriptlet,
            ApiCall call) throws IOException, UnRAVLException {
        setScript(script);
        setScriptlet(scriptlet);
        setCall(call);
        return null;
    }

    /**
     * Used to register the body generator class with the UnRAVL runtime This is
     * called from Spring when the UnRAVLPlugins class is loaded.
     * 
     * @param plugins
     *            UnRVAL plugins manager instance
     */
    @Autowired
    public void setPluginManager(UnRAVLPlugins plugins) {
        plugins.addRequestBodyGenerator(this.getClass());
    }

}
