package com.sas.unravl.auth;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.BaseUnRAVLPlugin;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.UnRAVLPlugins;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * The base implementation of an {@link UnRAVL} script assertion, which is run
 * from the "assert" or "precondition" elements of the script.
 * <p>
 * Assert objects run before ("precondition") or after ("assert") the script
 * makes the API call. and validate the environment, such as the API response.
 * An {@link UnRAVL} script will load UnRAVLAssertion objects while executing
 * the "assert" or "precondition" members of the script. The first field in the
 * assertion member is used as the key, i.e.
 *
 * <pre>
 * { "response" : [ ... ] }
 * </pre>
 *
 * is an assertion which uses the key "response". The assertion class is found
 * in the {@link UnRAVLPlugins} list of assertions and instantiated. Then, the
 * {@link #authenticate(UnRAVL, ObjectNode, ApiCall)} method is run, passing the
 * currently executing {@link UnRAVL} script and the JsonNode element that
 * defines the assertion scriptlet.
 * <p>
 * Extractors should extend {@link BaseUnRAVLAuth} and their check() method
 * should invoke super.check(script,node)
 *
 * @author David.Biesack@sas.com
 */
public class BaseUnRAVLAuth extends BaseUnRAVLPlugin implements UnRAVLAuth {
    @Override
    public void authenticate(UnRAVL script, ObjectNode auth, ApiCall call)
            throws UnRAVLException {
        setAuth(auth);
        setScript(script);
        setCall(call);
    }

    @Override
    public void setAuth(ObjectNode node) {
        this.setScriptlet(node);
    }

    @Override
    public ObjectNode getAuth() {
        return getScriptlet();
    }

    /**
     * Create a Null Object assertion
     *
     * @param script
     *            the script
     * @param auth
     *            the auth element
     * @return an auth element that does nothing; useful for unit tests
     */
    public static UnRAVLAuth of(UnRAVL script, ObjectNode auth) {
        BaseUnRAVLAuth a = new BaseUnRAVLAuth();
        a.setAuth(auth);
        a.setScript(script);
        return a;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b // .append(u.name())
          // .append(", ")
                .append(getAuth().toString());
        return b.toString();
    }

    /**
     * Used to register the assertion class with the UnRAVL runtime. This is
     * called from Spring when the UnRAVLPlugins class is loaded.
     *
     * @param plugins
     *            a plugins instance
     */
    @Autowired
    public void setPluginManager(UnRAVLPlugins plugins) {
        plugins.addAuth(this.getClass());
    }

}
