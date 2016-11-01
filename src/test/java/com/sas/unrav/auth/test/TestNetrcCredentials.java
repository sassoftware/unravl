// Copyright (c) 2015, SAS Institute Inc., Cary, NC, USA, All Rights Reserved
package com.sas.unrav.auth.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.UnRAVLRuntime;
import com.sas.unravl.auth.HostCredentials;
import com.sas.unravl.auth.NetrcCredentialsProvider;
import com.sas.unravl.auth.OAuth2Credentials;
import com.sas.unravl.util.Json;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestNetrcCredentials {

    @Parameters(name = "{index} host {0}, expect user {1}, password {2}")
    public static Collection<Object[]> testCases() {

        return Arrays.asList(new Object[][] { // @formatter:off
                        // this test data should match src/test/netrc/.netrc
                        new String[] { "simple.host.com", "test.user.a",
                                "test.user.a-secret", null, null },
                        new String[] { "host.withport8080.com:8080",
                                "test.user.b", "test.user.b-secret", null, null },
                        new String[] { "host.whitespace.com", "test.user.c",
                                "test.user.c-secret", null, null },
                        new String[] { "host.special.com", "test.user.d",
                                "test.user.d password", null, null },
                        new String[] { "host.alt.order", "test.user.e",
                                "test.password.e", null, null },
                        new String[] { "auth.server1", "OAuthUser-1",
                                "OAuth2 password 1", "0x0123456789abcdef",
                                "0xgfedcba9876543210" } });
    } // @formatter:on

    static String userHome = null;

    @BeforeClass
    public static void beforeClass() {
        // Save current user.home, then run with a temporary user.home
        // to pick up the test .netrc file

        userHome = System.getProperty("user.home");
        File tempUser = new File("src/test/data");
        if (tempUser.exists())
            System.setProperty("user.home", tempUser.getAbsolutePath());
        else
            userHome = null;
    }

    UnRAVLRuntime rt = new UnRAVLRuntime();
    String hostName;
    String expectedUserName;
    String expectedPassword;
    String expectedClientId;
    String expectedClientSecret;

    public TestNetrcCredentials(String hostName, String expectedUserName,
            String expectedPassword, String expectedClientId,
            String expectedClientSecret) {
        this.hostName = hostName;
        this.expectedUserName = expectedUserName;
        this.expectedPassword = expectedPassword;
        this.expectedClientId = expectedClientId;
        this.expectedClientSecret = expectedClientSecret;
    }

    @Test
    public void testNetrcCredentials() throws UnRAVLException, IOException {

        if (userHome == null)
            fail(String
                    .format("no src/test/netrc directory relative to current directory %s",
                            System.getProperty("user.dir")));

        NetrcCredentialsProvider nc = new NetrcCredentialsProvider();
        nc.setRuntime(rt);

        ObjectNode node = (ObjectNode) Json.parse("{ \"basic\" : true }");
        HostCredentials cred = nc.getHostCredentials(hostName, node, false);
        assertEquals(expectedUserName, cred.getUserName());
        assertEquals(expectedPassword, cred.getPassword());
        if (cred instanceof OAuth2Credentials) {
            OAuth2Credentials oauth2 = (OAuth2Credentials) cred;
            assertEquals(expectedClientId, oauth2.getClientId());
            assertEquals(expectedClientSecret, oauth2.getClientSecret());
        }
        
        if (expectedClientId != null) {
        node = (ObjectNode) Json.parse(String.format("{ \"oauth2\" : \"\", \"login\": \"%s\", \"password\" : \"%s\" }", expectedUserName, expectedPassword));
        cred = nc.getHostCredentials(hostName, node, false);
        assertEquals(expectedUserName, cred.getUserName());
        assertEquals(expectedPassword, cred.getPassword());
        assertTrue(cred instanceof OAuth2Credentials); 
        {
            OAuth2Credentials oauth2 = (OAuth2Credentials) cred;
            assertEquals(expectedClientId, oauth2.getClientId());
            assertEquals(expectedClientSecret, oauth2.getClientSecret());
        }


        // verify that we use the password in the auth object, not in .netrc
        node = (ObjectNode) Json.parse(String.format("{ \"oauth2\" : \"\", \"login\": \"%s\", \"password\" : \"override\" }", expectedUserName));
        cred = nc.getHostCredentials(hostName, node, false);
        assertEquals(expectedUserName, cred.getUserName());
        assertEquals("override", cred.getPassword());
        assertTrue(cred instanceof OAuth2Credentials); 
        {
            OAuth2Credentials oauth2 = (OAuth2Credentials) cred;
            assertEquals(expectedClientId, oauth2.getClientId());
            assertEquals(expectedClientSecret, oauth2.getClientSecret());
        }
        
        // verify that we use the password , clientId, and secret in the auth object, not in .netrc
        node = (ObjectNode) Json.parse(String.format("{ \"oauth2\" : \"\", \"login\": \"%s\", \"password\" : \"override\", \"clientId\": \"lClientId\", \"clientSecret\": \"lClientSecret\" }", expectedUserName ));
        cred = nc.getHostCredentials(hostName, node, false);
        assertEquals(expectedUserName, cred.getUserName());
        assertEquals("override", cred.getPassword());
        assertTrue(cred instanceof OAuth2Credentials); 
        {
            OAuth2Credentials oauth2 = (OAuth2Credentials) cred;
            assertEquals("lClientId", oauth2.getClientId());
            assertEquals("lClientSecret", oauth2.getClientSecret());
        }

        }
    }

    @AfterClass
    public static void cleanup() {
        System.setProperty("user.home", userHome);
    }

}
