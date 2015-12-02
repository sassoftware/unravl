// Copyright (c) 2014, SAS Institute Inc., Cary, NC, USA, All Rights Reserved
package com.sas.unravl.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.sas.unravl.ApiCall;
import com.sas.unravl.UnRAVL;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.annotations.UnRAVLAuthPlugin;
import com.sas.unravl.assertions.UnRAVLAssertionException;
import com.sas.unravl.generators.Binary;
import com.sas.unravl.generators.Text;
import com.sas.unravl.util.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.log4j.Logger;

/**
 * An auth plugin which verifies that the API call is authenticated with Central
 * Authentication Service. This authenticates to obtain a Ticket Granting Ticket
 * (TGT) using the user's credentials stored in .netrc in the current directory,
 * the user's ~/.netrc or %USERPROFILE%\_netrc file (Windows), then uses that
 * TGT to obtain a Service Ticket for the current UnRAVL script API. Optionally,
 * credentials may be enclosed directly in the "cas" element.
 * <p>
 * This auth element is specified via
 *
 * <pre>
 * "auth" : { "cas" : <em>logon-URL</em> }
 * "auth" : { "cas" : <em>logon-URL</em>, "login" : "myuserid" }
 * "auth" : { "cas" : <em>logon-URL</em>, "login" : "userid", "password" : "mySecret" }
 * "auth" : { "cas" : <em>logon-URL</em>, "mock" : <em>boolean-value</em>
 * </pre>
 *
 * where <em>logon-URL</em> is a string containing the URL of the ticket
 * granting ticket authentication API, such as
 * <code>"http://www.example.com/SASLogon/v1/tickets"</code>
 * <p>
 * The service ticket is appended as a query parameter to the end of the URI as
 * <code>&amp;ticket=&lt;<em>service-ticket</em>&gt;</code> or
 * <code>?ticket=&lt;<em>service-ticket</em>&gt;</code> as needed. The TGT
 * location is added to the environment as <code>&lt;<em>hostname</em>
 * &gt;.TGT</code> where hostname is taken from the logon-URL value in the JSON
 * specification
 * <p>
 * If mock is true, this precondition will create a mock service ticket.
 *
 * @author DavidBiesack@sas.com
 */
@UnRAVLAuthPlugin("cas")
public class CentralAuthenticationServiceAuth extends BaseUnRAVLAuth {

    private String logonUrl;
    private boolean mock; // JSON spec contains "mock" : true, then mock out the
                          // CAS responses and create a fake ?ticket=
                          // parameter
    private ByteArrayOutputStream responseBody;
    private static final Logger logger = Logger
            .getLogger(CentralAuthenticationServiceAuth.class);

    @Override
    public void authenticate(UnRAVL script, ObjectNode casAuthSpec,
            ApiCall call) throws UnRAVLException {
        super.authenticate(script, casAuthSpec, call);
        authenticateAndCreateServiceTicket(casAuthSpec);
    }

    void authenticateAndCreateServiceTicket(ObjectNode auth)
            throws UnRAVLException {
        try {
            if (getScript().getURI() == null || getScript().getMethod() == null)
                throw new UnRAVLException(
                        "cas auth requires an HTTP method and URI");
            // Note: The URI should already be expanded at this point
            String location = getCall().getURI();
            URI uri = new URI(location);
            if (uri.toString().contains("?ticket=")
                    || uri.toString().contains("&ticket=")) {
                logger.warn("Warning: uri already contains ticket= "
                        + uri.toString());
                return;
            }
            long start = System.currentTimeMillis();
            JsonNode logon = Json.firstFieldValue(getScriptlet());
            if (logon == null || !(logon instanceof TextNode))
                throw new UnRAVLException("cas auth requires a logon element.");
            if (getScriptlet().get("mock") != null)
                mock = getScriptlet().get("mock").booleanValue();
            logonUrl = getScript().expand(logon.textValue());
            String tgtLocation = logon(new URI(logonUrl), auth);
            String st = serviceTicket(tgtLocation, uri);
            getScript().bind("casAuth.ST", st);
            String ticketedUri = serviceTicket(location, st);
            getCall().setURI(ticketedUri);
            long end = System.currentTimeMillis();
            logger.trace("CAS authentication took " + (end - start) + "ms");

        } catch (URISyntaxException e) {
            new UnRAVLException(e.getMessage(), e);
        } catch (IOException e) {
            new UnRAVLException(e.getMessage(), e);
        }

    }

    /**
     * Save the TGT for this URI's host in the environment, so subsequent
     * casAuth preconditions do not have to reauthenticate. The most recently
     * acquired TGT is also stored in the environment as "{user}.{host}.TGT",
     * and also to "casauth.TGT", which enables logging out by performing a
     * DELETE on the TGT.
     *
     * @param tgt
     *            the ticket granting ticket
     * @param uri
     *            the current call's URI
     * @param user
     *            the userid
     */
    private void bindTGT(String tgt, URI uri, String user) {
        String host = uri.getHost();
        getScript().bind(user + "." + host + "." + "TGT", tgt);
        getScript().bind("casAuth.TGT", tgt); // This allows logout via DELETE
                                              // to the TGT

    }

    private String serviceTicket(String location, String serviceTicket)
            throws UnsupportedEncodingException {
        String encodedTicket = Text.urlEncode(serviceTicket);
        if (location.indexOf('?') == -1)
            return location + "?ticket=" + encodedTicket;
        else
            return location + "&ticket=" + encodedTicket;
    }

    private String serviceTicket(String tgt, URI uri) throws UnRAVLException,
            URISyntaxException, ClientProtocolException, IOException {
        if (mock)
            return "ST-18-umUeNL4yUkWHES2VdtKki5mFzatga43kNNCe3niguLWaUxl1aK-cas";
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            HttpPost post = new HttpPost();
            post.setURI(new URI(tgt));
            Header requestHeaders[] = new Header[] {
                    new BasicHeader("Content-Type", "text/plain") };
            post.setHeaders(requestHeaders);
            String body = "service=" + Text.urlEncode(uri.toString());
            HttpEntity entity = new StringEntity(body);
            post.setEntity(entity);
            ResponseHandler<HttpResponse> responseHandler = new CasAuthResponseHandler();
            HttpResponse response = httpclient.execute(post, responseHandler);
            // TODO: If we get back a response that indicates a timed out
            // TGT, we should login again.
            int status = response.getStatusLine().getStatusCode();
            if (status != 200)
                throw new UnRAVLException("Cannot get Service Ticket for " + uri
                        + ", response returned " + status);
            String st = Text.utf8ToString(responseBody.toByteArray());
            return st;
        } finally {
            httpclient.close();
        }
    }

    private String logon(URI logonURI, ObjectNode auth) throws UnRAVLException,
            URISyntaxException, ClientProtocolException, IOException {
        if (mock)
            return "https://sasserver:port/SASLogon/v1/tickets/TGT-18-umUeNL4yUkWHES2VdtKki5mFzatga43kNNCe3niguLWaUxl1aK-cas";
        String host = logonURI.getHost();
        CredentialsProvider cp = getScript().getRuntime().getPlugins()
                .getCredentialsProvider();
        cp.setRuntime(getScript().getRuntime());
        HostCredentials credentials = cp.getHostCredentials(host, auth, false);
        if (credentials == null)
            throw new UnRAVLAssertionException(
                    "No CAS credentials for host " + host);

        String user = credentials.getUserName();
        String key = user + "." + host + ".TGT";
        String tgt = null;
        // See if the TGT is cached for this user/host
        // Note that this risks a TGT timeout
        if (getCall().bound(key)) {
            Object tgto = getCall().getVariable(key);
            if (tgto instanceof String) {
                tgt = (String) getCall().getVariable(key);
                logger.info("Using cached CAS TGT " + tgt);
                return tgt;
            }
        }
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            HttpPost post = new HttpPost();
            Header requestHeaders[] = new Header[] { new BasicHeader(
                    "Content-Type", "application/x-www-form-urlencoded") };
            String u = Text.urlEncode(user);
            String p = Text.urlEncode(credentials.getPassword());
            String body = String.format("username=%s&password=%s", u, p);
            // security: don't hold onto credentials in memory
            credentials.clear();
            credentials = null;
            p = null;
            post.setURI(logonURI);
            post.setHeaders(requestHeaders);
            post.setEntity(new StringEntity(body));
            ResponseHandler<HttpResponse> responseHandler = new CasAuthResponseHandler();
            HttpResponse response = httpclient.execute(post, responseHandler);
            // security: don't hold onto credentials in memory
            body = null;
            int status = response.getStatusLine().getStatusCode();
            if (status != 201)
                throw new UnRAVLException("Cannot login via " + logonURI
                        + ", response: " + response.getStatusLine());
            Header location = response.getFirstHeader("Location");
            if (location == null)
                throw new UnRAVLException("Cannot login via " + logonURI
                        + ", no Location header returned.");

            tgt = location.getValue();
            bindTGT(tgt, logonURI, user);
            return tgt;
        } finally {
            httpclient.close();
        }
    }

    private class CasAuthResponseHandler
            implements ResponseHandler<HttpResponse> {
        @Override
        public HttpResponse handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
            if (response.getEntity() == null)
                return response;
            InputStream input = response.getEntity().getContent();
            if (input != null) {
                responseBody = new ByteArrayOutputStream();
                Binary.copy(input, responseBody);
                responseBody.close();
            } else
                responseBody = null;

            return response;
        }

    }

}
