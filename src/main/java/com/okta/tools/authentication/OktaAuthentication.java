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
import java.util.stream.Stream;

public final class OktaAuthentication {
    private static final Logger logger = LogManager.getLogger(OktaAuthentication.class);

    private final OktaAwsCliEnvironment environment;

    public OktaAuthentication(OktaAwsCliEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Performs primary and secondary (2FA) authentication, then returns a session token
     *
     * @return The session token
     * @throws IOException If an error occurs during the api call or during the processing of the result.
     */
    public String getOktaSessionToken() throws IOException {
        // Returns an Okta Authentication Transaction object.
        // See: https://developer.okta.com/docs/api/resources/authn#authentication-transaction-model
        JSONObject primaryAuthResult = new JSONObject(getPrimaryAuthResponse(environment.oktaOrg));

        // "statusProperty" = The current state of the authentication transaction.
        final String statusProperty = "status";

        // "sessionProperty" = An ephemeral one-time token used to bootstrap an Okta session.
        final String sessionProperty = "sessionToken";

        // Template used to build a missing property exception message.
        final String missingProperty = "Could not find the expected property \"%s\" in the response message.";

        // Sanity check: Does the (required) status property exist?
        if (!primaryAuthResult.has(statusProperty)) {
            throw makeException(primaryAuthResult, missingProperty, statusProperty);
        }

        // Validate status value.
        final TransactionState state;
        try {
            state = TransactionState.valueOf(primaryAuthResult.getString(statusProperty).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw makeException(
                primaryAuthResult,
                e,
                "The response message contained an unrecognized value \"%s\" for property \"%s\".",
                primaryAuthResult.getString(statusProperty),
                statusProperty);
        }

        // Handle the response
        switch(state) {
            // Handled States
            case INVALID:
                throw new IllegalStateException("Invalid value - should never happen.");
            case MFA_REQUIRED:
                // Handle second-factor
                return OktaMFA.promptForFactor(primaryAuthResult);
            case SUCCESS:
                if (primaryAuthResult.has(sessionProperty)) {
                    return primaryAuthResult.getString(sessionProperty);
                } else {
                    throw makeException(primaryAuthResult, missingProperty, sessionProperty);
                }

            // Unhandled States
            // If support for handling a new state is added, move to the 'Handled' block and keep the
            // values sorted in the order given by TransactionState.java.
            case UNAUTHENTICATED:
            case PASSWORD_WARN:
            case PASSWORD_EXPIRED:
            case RECOVERY:
            case RECOVERY_CHALLENGE:
            case PASSWORD_RESET:
            case LOCKED_OUT:
            case MFA_ENROLL:
            case MFA_ENROLL_ACTIVATE:
            case MFA_CHALLENGE:
            default:
                throw makeException(
                    primaryAuthResult,
                    "Handling for the received status code is not currently implemented.\n" +
                    "The status code received was:\n%s: %s",
                    state.toString(),
                    state.getDescription());
        }
    }

    /**
     * Performs primary authentication and parses the response.
     *
     * @param oktaOrg The org to authenticate against
     * @return The response of the authentication
     * @throws IOException If an error occurs during the api call or during the processing of the result.
     */
    private String getPrimaryAuthResponse(String oktaOrg) throws IOException {
        while (true) {
            AuthResult response = primaryAuthentication(getUsername(), getPassword(), oktaOrg);
            int requestStatus = response.statusLine.getStatusCode();
            primaryAuthFailureHandler(requestStatus, oktaOrg);
            if (requestStatus == HttpStatus.SC_OK) {
                return response.responseContent;
            }
            if (environment.oktaPassword != null) {
                throw new IllegalStateException("Stored username or password is invalid.");
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
     * @throws IOException If an error occurs during the api call or during the processing of the result.
     */
    private AuthResult primaryAuthentication(String username, String password, String oktaOrg) throws IOException {
        // Okta authn API docs: https://developer.okta.com/docs/api/resources/authn#primary-authentication
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

        logger.debug("Calling okta authn service at " + httpPost.getURI());
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
            System.err.print("Username: ");
            return new Scanner(System.in).next();
        } else {
            System.err.println("Username: " + environment.oktaUsername);
            return environment.oktaUsername;
        }
    }

    private String getPassword() {
        if (environment.oktaPassword == null) {
            return promptForPassword();
        } else {
            return environment.oktaPassword.get();
        }
    }

    private String promptForPassword() {
        if (System.console() == null) { // hack to be able to debug in an IDE
            System.err.print("Password: ");
            return new Scanner(System.in).next();
        } else {
            return new String(System.console().readPassword("Password: "));
        }
    }

    private RuntimeException makeException(JSONObject primaryAuthResult, String template, Object... args) {
        return makeException(primaryAuthResult, null, template, args);
    }

    // Create an exception by formatting a string with arguments and appending the json message.
    private RuntimeException makeException(JSONObject primaryAuthResult, Exception e, String template, Object... args) {
        Object[] argsWithMessageJson =
            Stream.concat(Stream.of(args), Stream.of(primaryAuthResult.toString(2))).toArray();
        String message = String.format(template + "\n\nMessage:\n%s\n", argsWithMessageJson);
        if (e != null) {
            return new IllegalStateException(message, e);
        } else {
            return new IllegalStateException(message);
        }
    }
}
