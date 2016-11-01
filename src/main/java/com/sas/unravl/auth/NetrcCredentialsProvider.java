// Copyright (c) 2014, SAS Institute Inc., Cary, NC, USA, All Rights Reserved
package com.sas.unravl.auth;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

/**
 * Provides authentication credentials for an UnRAVL script.
 * <p>
 * The credentials may come from the <code>"auth"</code> element itself, via the
 * properties <code>"login"</code> and <code>"password"</code>. Environment
 * substitution is applied to these strings, so you can set the credentials in
 * unRAVL variables or pass them as system properties.
 * </p>
 * <p>
 * If the authentication element does not contain credentials, search for them
 * by host in <code>.netrc</code> file (or <code>._netrc</code> on Windows).
 * That file is read from the current directory, or if not found there, from the
 * home directory (<code>.~/.netrc</code> or <code>.%USERPROFILE%\\.netrc</code>
 * ). The file contains lines in the following format:
 * </p>
 * 
 * <pre>
 * machine <em>qualified.hostname</em> login <em>userid-for-host</em> password <em>password-for-host</em>
 * 
 * machine <em>qualified.hostname</em> login <em>userid-for-host</em> password <em>password-for-host</em> port <em>port-number</em>
 * </pre>
 * <p>
 * Key/Value pairs may be in any order. Lines may use <code>user</code> instead
 * of <code>login</code>; lines may use <code>host</code> instead of
 * <code>machine</code>.
 * </p>
 * <p>
 * Passwords with whitespace in them must be quoted with double quotes. Thus,
 * passwords may not contain double quote characters.
 * 
 * @author DavidBiesack@sas.com
 */
public class NetrcCredentialsProvider extends AbstractCredentialsProvider {

    /**
     * Create a Credentials instance for use with the UnRAVL runtime
     */
    public NetrcCredentialsProvider() {
    }

    // matches: identifier unquoted-text-without-spaces
    // matches: identifier "quoted-text with possible spaces"
    private static final Pattern KEY_VALUE = Pattern
            .compile("\\s*(\\w+)\\s+(\"([^\"]*)\"|([^\\s]+))");
    // pattern match groups:
    private static final int KEY_GROUP = 1;
    private static final int QUOTED_VAL_GROUP = 3;
    private static final int UNQUOTED_VAL_GROUP = 4;
    static final Logger logger = Logger
            .getLogger(NetrcCredentialsProvider.class);

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.sas.unravl.auth.CredentialsProvider#getCredentials(java.lang.String,
     * java.lang.String, java.lang.String, boolean)
     */
    @Override
    public HostCredentials getHostCredentials(String hostPort, String login,
            String password, boolean mock) throws FileNotFoundException,
            IOException {

        if (mock)
            return mockCredentials();
        String clientId = credentialValue(auth, "clientId");
        String clientSecret = credentialValue(auth, "clientSecret");
        String accessToken = credentialValue(auth, "accessToken");

        // Locate the netrc config file that contains credentials for hosts
        File netrc = new File(".netrc"); // look in current dir first
        if (!netrc.exists())
            netrc = new File("_netrc"); // possible Windows file name, in
                                        // current dir
        File home = new File(System.getProperty("user.home"));
        if (!netrc.exists())
            netrc = new File(home, ".netrc");
        if (!netrc.exists())
            netrc = new File(home, "_netrc");
        if (!netrc.exists())
            return null;

        // split host:port into its parts. :port is optional
        String host = host(hostPort);
        String port = port(hostPort);
        // Note: We can read and cache all the credentials, but that
        // is a security issue; we should probably encrypt the cached content.
        // Also, if we do cache the file content, we would still have to check
        // the modification time stamp of the file, in case it has been updated
        // since we cached the content.
        // Also, the loop below would need to read all rows, not stop when a
        // match is found. So we don't cache and simply read the file each time
        // it is needed

        try (BufferedReader reader = new BufferedReader(new FileReader(netrc))) {
            for (String line = reader.readLine(); line != null; line = reader
                    .readLine()) {
                if (line.trim().startsWith("#"))
                    continue;
                Matcher m = KEY_VALUE.matcher(line);
                String lHost = null, lPort = null, lLogin = null, lPassword = null, lClientId = null, lClientSecret = null, lAccessToken = null;
                while (m.find()) {
                    String key = m.group(KEY_GROUP).toLowerCase();
                    String val = m.group(QUOTED_VAL_GROUP);
                    if (val == null)
                        val = m.group(UNQUOTED_VAL_GROUP);
                    switch (key) {
                    case "login":
                    case "user":
                        lLogin = val;
                        break;
                    case "host":
                    case "machine":
                        lHost = val;
                        break;
                    case "port":
                        lPort = val;
                        break;
                    case "password":
                        lPassword = val;
                        break;
                    case "clientid":
                        lClientId = val;
                        break;
                    case "clientsecret":
                        lClientSecret = val;
                        break;
                    case "accesstoken":
                        lAccessToken = val;
                        break;
                    default:
                        logger.warn("Ignoring unknown key " + key
                                + " in netrc file");
                    }
                }
                if (host.equalsIgnoreCase(lHost)
                        && Objects.equals(port, lPort)
                        && (clientId == null || (Objects.equals(clientId,
                                lClientId)))) {
                    if (login == null || lLogin.equals(login)) {
                        if ((or(clientId, lClientId) != null && or(clientSecret, lClientSecret) != null)
                                || or(accessToken, lAccessToken) != null)
                            return credentials(or(login, lLogin), or(password, lPassword),
                                    or(clientId, lClientId),
                                    or(clientSecret, lClientSecret),
                                    or(accessToken, lAccessToken));
                        else if (or(password, lPassword) != null)
                            return credentials(or(login, lLogin), or(password, lPassword));
                    }
                }
            }
        }

        if ((clientId != null && clientSecret != null) || accessToken != null)
            return credentials(login, password, clientId, clientSecret,
                    accessToken);
        else if (login != null && password != null)
            return credentials(login, password);

        return null;
    }

    private String or(String s1, String s2) {
        return s1 == null ? s2 : s1;
    }

}
