// Copyright (c) 2014, SAS Institute Inc., Cary, NC, USA, All Rights Reserved
package com.sas.unravl;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A base implementation class for {@link UnRAVLPlugin}
 * 
 * @author DavidBiesack@sas.com
 */
public abstract class BaseUnRAVLPlugin implements UnRAVLPlugin {

    private UnRAVL script;
    private ObjectNode scriptlet;
    private ApiCall call;

    /**
     * @return the API call that this plugin is processing
     */
    public ApiCall getCall() {
        return call;
    }

    /**
     * Set the runtime API call instance
     * 
     * @param call
     *            the current API call instance
     */
    public void setCall(ApiCall call) {
        this.call = call;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sas.unravl.UnRAVLPlugin#getScriptlet()
     */
    @Override
    public ObjectNode getScriptlet() {
        return scriptlet;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sas.unravl.UnRAVLPlugin#setScriptlet(org.codehaus.jackson.node.
     * ObjectNode )
     */
    @Override
    public void setScriptlet(ObjectNode scriptlet) {
        this.scriptlet = scriptlet;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sas.unravl.UnRAVLPlugin#setScript(com.sas.unravl.UnRAVL)
     */
    @Override
    public void setScript(UnRAVL script) {
        this.script = script;

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sas.unravl.UnRAVLPlugin#script()
     */
    @Override
    public UnRAVL getScript() {
        return script;
    }
}
