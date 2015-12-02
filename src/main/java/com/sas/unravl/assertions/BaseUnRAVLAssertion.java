package com.sas.unravl.assertions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.BaseUnRAVLPlugin;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.UnRAVLPlugins;

import org.apache.log4j.Logger;
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
 * {@link #check(UnRAVL, ObjectNode, Stage, ApiCall)} method is run, passing the
 * currently executing {@link UnRAVL} script and the JsonNode element that
 * defines the assertion scriptlet.
 * <p>
 * Extractors should extend {@link BaseUnRAVLAssertion} and their check() method
 * should invoke super.check(script,node)
 * 
 * @author David.Biesack@sas.com
 */
public class BaseUnRAVLAssertion extends BaseUnRAVLPlugin
        implements UnRAVLAssertion {

    private UnRAVLAssertionException e;
    private static Logger logger = Logger.getLogger(BaseUnRAVLAssertion.class);
    private UnRAVLAssertion.Stage stage = Stage.ASSERT;

    @Override
    public void check(UnRAVL script, ObjectNode assertion, Stage stage,
            ApiCall call) throws UnRAVLAssertionException, UnRAVLException {
        setAssertion(assertion);
        setScript(script);
        setStage(stage);
        setCall(call);
        logger.trace(stage.toString().toLowerCase() + ": " + assertion);
    }

    @Override
    public UnRAVLAssertionException getUnRAVLAssertionException() {
        return e;
    }

    @Override
    public void setUnRAVLAssertionException(UnRAVLAssertionException e) {
        this.e = e;
    }

    @Override
    public void setAssertion(ObjectNode node) {
        this.setScriptlet(node);
    }

    @Override
    public ObjectNode getAssertion() {
        return getScriptlet();
    }

    /**
     * Create a Null Object assertion
     * 
     * @param script
     *            the script
     * @param assertion
     *            the assertion member
     * @return an assertion that does nothing; useful for unit tests
     */
    public static UnRAVLAssertion of(UnRAVL script, ObjectNode assertion) {
        BaseUnRAVLAssertion a = new BaseUnRAVLAssertion();
        a.setAssertion(assertion);
        a.setScript(script);
        return a;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b // .append(u.name())
          // .append(", ")
                .append(getAssertion().toString());
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
        plugins.addAssertion(this.getClass());
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @Override
    public Stage getStage() {
        return stage;
    }
}
