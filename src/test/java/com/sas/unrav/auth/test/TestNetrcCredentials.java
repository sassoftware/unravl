// Copyright (c) 2015, SAS Institute Inc., Cary, NC, USA, All Rights Reserved
package com.sas.unrav.auth.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.UnRAVLRuntime;
import com.sas.unravl.auth.HostCredentials;
import com.sas.unravl.auth.NetrcCredentialsProvider;
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
                        "test.user.a-secret" },
                new String[] { "host.withport8080.com:8080", "test.user.b",
                        "test.user.b-secret" },
                new String[] { "host.whitespace.com", "test.user.c",
                        "test.user.c-secret" },
                new String[] { "host.special.com", "test.user.d",
                        "test.user.d password" }, });
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

    public TestNetrcCredentials(String hostName, String expectedUserName,
            String expectedPassword) {
        this.hostName = hostName;
        this.expectedUserName = expectedUserName;
        this.expectedPassword = expectedPassword;
    }

    @Test
    public void testNetrcCredentials() throws UnRAVLException, IOException {

        if (userHome == null)
            fail(String.format(
                    "no src/test/netrc directory relative to current directory %s",
                    System.getProperty("user.dir")));

        NetrcCredentialsProvider nc = new NetrcCredentialsProvider();
        nc.setRuntime(rt);

        ObjectNode node = (ObjectNode) Json.parse("{ \"basic\" : true }");
        HostCredentials cred = nc.getHostCredentials(hostName, node, false);
        assertEquals(expectedUserName, cred.getUserName());
        assertEquals(expectedPassword, cred.getPassword());

    }

    @AfterClass
    public static void cleanup() {
        System.setProperty("user.home", userHome);
    }

}
