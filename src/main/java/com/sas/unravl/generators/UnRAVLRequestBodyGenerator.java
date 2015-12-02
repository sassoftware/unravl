package com.sas.unravl.generators;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;

import java.io.IOException;
import java.io.InputStream;

/**
 * The interface for a request body generator, which can create a REST request
 * body from a specification in an {@link UnRAVL} script. Body generators
 * typically bind a value named "requestBody" to the content that they generate
 * (usually a String value). Implementors should extend
 * {@link BaseUnRAVLRequestBodyGenerator}.
 * 
 * @author David.Biesack@sas.com
 */
public interface UnRAVLRequestBodyGenerator {
    /**
     * Generate the body. This is done by providing an input stream (Perhaps
     * this should just be a Future&lt;byte []&gt;). The UnRAVL script will call
     * this and read the stream and store the result.
     * 
     * @param script
     *            the currently running UnRAVL script
     * @param scriptlet
     *            the JSON specification for this instance
     * @param call
     *            The current API call
     * @return an input stream which the script will read
     * @throws IOException
     *             if an I/O problem occurs
     * @throws UnRAVLException
     *             if the body cannot be generated
     */
    public InputStream getBody(UnRAVL script, ObjectNode scriptlet,
            ApiCall call) throws IOException, UnRAVLException;
}
