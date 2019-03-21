/*
 * Copyright 2019 Okta
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.okta.tools.authentication;

import com.okta.tools.OktaAwsCliEnvironment;
import com.okta.tools.models.AuthResult;
import org.apache.http.HttpStatus;
import org.json.JSONObject;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class OktaAuthentication {
    private static final Logger logger = Logger.getLogger(OktaAuthentication.class.getName());

    private final OktaAwsCliEnvironment environment;
    private final OktaMFA oktaMFA;
    private final UserConsole userConsole;
    private final OktaAuthnClient oktaAuthnClient;

    public OktaAuthentication(OktaAwsCliEnvironment environment, OktaMFA oktaMFA, UserConsole userConsole, OktaAuthnClient oktaAuthnClient) {
        this.environment = environment;
        this.oktaMFA = oktaMFA;
        this.userConsole = userConsole;
        this.oktaAuthnClient = oktaAuthnClient;
    }

    /**
     * Performs primary and secondary (2FA) authentication, then returns a session token
     *
     * @return The session token
     * @throws IOException If an error occurs during the api call or during the processing of the result.
     */
    public String getOktaSessionToken() throws IOException, InterruptedException {
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
                return oktaMFA.promptForFactor(primaryAuthResult);
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
    private String getPrimaryAuthResponse(String oktaOrg) throws IOException, InterruptedException {
        while (true) {
            AuthResult response = oktaAuthnClient.primaryAuthentication(getUsername(), getPassword(), oktaOrg);
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
     * Handles failures during the primary authentication flow
     *
     * @param responseStatus The status of the response
     * @param oktaOrg        The org against which authentication was performed
     */
    private void primaryAuthFailureHandler(int responseStatus, String oktaOrg) {
        if (responseStatus == 400 || responseStatus == 401) {
            logger.severe("Invalid username or password.");
        } else if (responseStatus == 500) {
            logger.severe(() -> "Unable to establish connection with: " + oktaOrg +
                    "\nPlease verify that your Okta org url is correct and try again");
        } else if (responseStatus != 200) {
            throw new IllegalStateException("Failed : HTTP error code : " + responseStatus);
        }
    }

    private String getUsername() {
        if (environment.oktaUsername == null || environment.oktaUsername.isEmpty()) {
            return userConsole.promptForUsername();
        } else {
            System.err.println("Username: " + environment.oktaUsername);
            return environment.oktaUsername;
        }
    }

    private String getPassword() throws InterruptedException {
        if (environment.oktaPassword == null) {
            return userConsole.promptForPassword();
        } else {
            return environment.oktaPassword.get();
        }
    }

    static RuntimeException makeException(JSONObject primaryAuthResult, String template, Object... args) {
        return makeException(primaryAuthResult, null, template, args);
    }

    // Create an exception by formatting a string with arguments and appending the json message.
    static RuntimeException makeException(JSONObject primaryAuthResult, Exception e, String template, Object... args) {
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
