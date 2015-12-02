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
 * Items must appeaqr in this order. You may use <code>user</code> instead of
 * <code>login</code>.
 * </p>
 * <p>
 * Passwords with whitespace in them must be quoted with double quotes.
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

    // This regex pattern matches the .netrc file format, described in the
    // javadoc comment above
    // examples are in src/test/data/.netrc
    static final Pattern NETRC = Pattern.compile(
            "^\\s*machine\\s+([^\\s]+)\\s+(login|user)\\s+([^\\s]+)\\s+password\\s+(\"([^\"]+)\"|([^\\s]+))(\\s+port\\s+([\\d]+))?.*$");
    // groups: 1 2 3 4 5 5 6 6 7 8 8
    static final int MACHINE_GROUP = 1;
    static final int USER_GROUP = 3;
    static final int QUOTED_PASSWORD_GROUP = 5;
    static final int UNQUOTED_PASSWORD_GROUP = 6;
    static final int PORT_GROUP = 8;

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.sas.unravl.auth.CredentialsProvider#getCredentials(java.lang.String,
     * java.lang.String, java.lang.String, boolean)
     */
    @Override
    public HostCredentials getHostCredentials(String hostPort, String login,
            String password, boolean mock)
                    throws FileNotFoundException, IOException {

        if (mock)
            return mockCredentials();
        if (login != null) {
            login = runtime.expand(login);
            if (password != null)
                return credentials(login, password);
        }

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
        // since
        // we cached the content.
        // Also, the loop below would need to read all rows, not stop when a
        // match is found. So we don't cache and simply read the file each time
        // it is needed
        BufferedReader reader = null;
        reader = new BufferedReader(new FileReader(netrc));
        try {
            for (String line = reader.readLine(); line != null; line = reader
                    .readLine()) {
                Matcher m = NETRC.matcher(line);
                if (m.matches()) {
                    String netrchost = m.group(MACHINE_GROUP);
                    String netrcport = m.group(PORT_GROUP);
                    if (host.equals(netrchost)
                            && Objects.equals(port, netrcport)) {
                        if (login == null
                                || m.group(USER_GROUP).equals(login)) {
                            login = m.group(USER_GROUP);
                            password = password(m);
                            return credentials(login, password);
                        }
                    }
                }
            }
        } finally {
            if (reader != null)
                reader.close();
        }
        return null;
    }

    private String password(Matcher m) {
        if (m.end(QUOTED_PASSWORD_GROUP) > m.start(QUOTED_PASSWORD_GROUP))
            return m.group(QUOTED_PASSWORD_GROUP);
        if (m.end(UNQUOTED_PASSWORD_GROUP) > m.start(UNQUOTED_PASSWORD_GROUP))
            return m.group(UNQUOTED_PASSWORD_GROUP);
        throw new AssertionError(
                "Regular expression did not match password in .netrc line.");
    }
}
