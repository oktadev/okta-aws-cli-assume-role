package com.okta.tools.authentication;

import com.okta.tools.OktaAwsCliEnvironment;
import com.okta.tools.models.AuthResult;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public final class OktaAuthentication {
    private static final Logger logger = LogManager.getLogger(OktaAuthentication.class);

    private OktaAwsCliEnvironment environment;

    public OktaAuthentication(OktaAwsCliEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Performs primary and secondary (2FA) authentication, then returns a session token
     *
     * @return The session token
     * @throws IOException
     */
    public String getOktaSessionToken() throws IOException {
        JSONObject primaryAuthResult = new JSONObject(getPrimaryAuthResponse(environment.oktaOrg));
        if (primaryAuthResult.getString("status").equals("MFA_REQUIRED")) {
            return OktaMFA.promptForFactor(primaryAuthResult);
        } else {
            return primaryAuthResult.getString("sessionToken");
        }
    }

    /**
     * Performs primary authentication and parses the response.
     *
     * @param oktaOrg The org to authenticate against
     * @return The response of the authentication
     * @throws IOException
     */
    private String getPrimaryAuthResponse(String oktaOrg) throws IOException {
        while (true) {
            AuthResult response = primaryAuthentication(getUsername(), getPassword(), oktaOrg);
            int requestStatus = response.statusLine.getStatusCode();
            primaryAuthFailureHandler(requestStatus, oktaOrg);
            if (requestStatus == HttpStatus.SC_OK) {
                return response.responseContent;
            }
        }
    }

    /**
     * Perform primary authentication against Okta
     *
     * @param username The username of the user
     * @param password The password of the user
     * @param oktaOrg  The org to perform auth against
     * @return The authentication result
     * @throws IOException
     */
    private AuthResult primaryAuthentication(String username, String password, String oktaOrg) throws IOException {
        HttpPost httpPost = new HttpPost("https://" + oktaOrg + "/api/v1/authn");

        httpPost.addHeader("Accept", "application/json");
        httpPost.addHeader("Content-Type", "application/json");
        httpPost.addHeader("Cache-Control", "no-cache");

        JSONObject authnRequest = new JSONObject();
        authnRequest.put("username", username);
        authnRequest.put("password", password);

        StringEntity entity = new StringEntity(authnRequest.toString(), StandardCharsets.UTF_8);
        entity.setContentType("application/json");
        httpPost.setEntity(entity);

        try (CloseableHttpClient httpClient = HttpClients.createSystem()) {
            CloseableHttpResponse authnResponse = httpClient.execute(httpPost);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(65536);
            authnResponse.getEntity().writeTo(byteArrayOutputStream);

            return new AuthResult(authnResponse.getStatusLine(), byteArrayOutputStream.toString());
        }
    }

    /**
     * Handles failures during the primary authentication flow
     *
     * @param responseStatus The status of the response
     * @param oktaOrg        The org against which authentication was performed
     */
    private void primaryAuthFailureHandler(int responseStatus, String oktaOrg) {
        if (responseStatus == 400 || responseStatus == 401) {
            logger.error("Invalid username or password.");
        } else if (responseStatus == 500) {
            logger.error("\nUnable to establish connection with: " + oktaOrg +
                    " \nPlease verify that your Okta org url is correct and try again");
        } else if (responseStatus != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + responseStatus);
        }
    }

    private String getUsername() {
        if (environment.oktaUsername == null || environment.oktaUsername.isEmpty()) {
            System.out.print("Username: ");
            return new Scanner(System.in).next();
        } else {
            System.out.println("Username: " + environment.oktaUsername);
            return environment.oktaUsername;
        }
    }

    private String getPassword() {
        if (environment.oktaPassword == null || environment.oktaPassword.isEmpty()) {
            return promptForPassword();
        } else {
            return environment.oktaPassword;
        }
    }

    private String promptForPassword() {
        if (System.console() == null) { // hack to be able to debug in an IDE
            System.out.print("Password: ");
            return new Scanner(System.in).next();
        } else {
            return new String(System.console().readPassword("Password: "));
        }
    }
}
