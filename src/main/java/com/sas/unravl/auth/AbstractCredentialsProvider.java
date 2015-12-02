// Copyright (c) 2015, SAS Institute Inc., Cary, NC, USA, All Rights Reserved
package com.sas.unravl.auth;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.UnRAVLRuntime;
import com.sas.unravl.util.Json;

import java.io.IOException;

/**
 * Abstract implementation of the CredentialsProvider interface. Subclasses need
 * only implement
 * 
 * <pre>
 *  public HostCredentials getHostCredentials(String host, String port,
 *          String userName, String password, boolean mock)
 * </pre>
 * <p>
 * This class also provides utility methods {@link #host(String)} and
 * {@link #port(String)} to split host:port into its parts.
 * 
 * @author David.Biesack@sas.com
 */
abstract public class AbstractCredentialsProvider
        implements CredentialsProvider {

    protected UnRAVLRuntime runtime;

    public AbstractCredentialsProvider() {
        super();
    }

    @Override
    public void setRuntime(UnRAVLRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Get credentials for the host. Note: If reading credentials from a .netrc
     * file, the credentials are <em>not</em> cached; we reread the .netrc file
     * each time. (This allows one script to obtain credentials from a service
     * and store them in .netrc in the current directory.)
     * 
     * @param host
     *            the host name or host:port string
     * @param auth
     *            The JsonNode containing the auth contents (usually login and
     *            password)
     * @param mock
     *            if true, return mock credentials
     * @return an object containing the username and password
     * @throws IOException
     *             if we could not read the .netrc file
     */
    @Override
    public HostCredentials getHostCredentials(String host, ObjectNode auth,
            boolean mock) throws IOException {
        if (mock)
            return mockCredentials();
        String userName = Json.stringFieldOr(auth, "login", null);
        String password = Json.stringFieldOr(auth, "password", null);
        return getHostCredentials(host, userName, password, mock);
    }

    /**
     * @param hostPort
     *            A hostname or hostname:port string
     * @return the host string, minus ant :port suffix
     */
    public static String host(String hostPort) {
        int colon = hostPort.indexOf(':');
        if (colon > -1)
            return hostPort.substring(0, colon);
        return hostPort;
    }

    /**
     * @param hostPort
     *            A hostname or hostname:port string
     * @return the port string if hostPort string matches host:port, else null
     */
    public static String port(String hostPort) {
        int colon = hostPort.indexOf(':');
        if (colon > -1)
            return hostPort.substring(colon + 1);
        return null;
    }

    /**
     * Mock credentials, for testing
     * 
     * @return "mockuser", "mockpassword"
     */
    protected HostCredentials mockCredentials() {
        return credentials("mockuser", "mockpassword");
    }

    protected HostCredentials credentials(String login, String password) {
        return new HostCredentials(runtime.expand(login),
                runtime.expand(password));
    }

}