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

import com.okta.tools.helpers.HttpHelper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class OktaMFA {
    private static final String FACTOR_TYPE_QUESTION = "question";
    private static final String FACTOR_TYPE_SMS = "sms";
    private static final String FACTOR_TYPE_TOKEN = "token";
    private static final String FACTOR_TYPE_TOKEN_HARDWARE = "token:hardware";
    private static final String FACTOR_TYPE_TOKEN_SOFTWARE_TOTP = "token:software:totp";
    private static final String FACTOR_TYPE_PUSH = "push";
    private static final String SESSION_TOKEN = "sessionToken";
    private static final String FACTOR_RESULT = "factorResult";
    private static final String STATUS = "status";
    private static final String FACTOR_TYPE = "factorType";
    private static final String STATE_TOKEN = "stateToken";
    private static final String CHANGE_FACTOR = "change factor";
    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
    private static final String LINKS = "_links";
    private static final String ERROR_OBTAINING_TOKEN = "ERROR_OBTAINING_TOKEN";

    private final OktaFactorSelector factorSelector;

    public OktaMFA(OktaFactorSelector factorSelector) {
        this.factorSelector = factorSelector;
    }

    /**
     * Prompt the user for 2FA after primary authentication
     *
     * @param primaryAuthResponse The response from primary auth
     * @return The session token
     */
    String promptForFactor(JSONObject primaryAuthResponse) throws InterruptedException, IOException {
        JSONObject factor = factorSelector.selectFactor(primaryAuthResponse);
        String stateToken = primaryAuthResponse.getString(STATE_TOKEN);
        String sessionToken = getSessionToken(factor, stateToken);

        while (isMfaRetryRequired(sessionToken)) {
            factor = factorSelector.selectFactor(primaryAuthResponse);
            stateToken = primaryAuthResponse.getString(STATE_TOKEN);
            sessionToken = getSessionToken(factor, stateToken);
        }

        return sessionToken;
    }

    private boolean isMfaRetryRequired(String sessionToken) {
        switch (sessionToken) {
            case ERROR_OBTAINING_TOKEN:
                System.err.println("Please try again");
                return true;
            case CHANGE_FACTOR:
                System.err.println("Factor change initiated");
                return true;
            case "timeout":
                System.err.println("Factor timed out");
                return true;
            default:
                return false;
        }
    }

    private String getSessionToken(JSONObject factor, String stateToken) throws IOException, InterruptedException {
        String factorType = factor.getString(FACTOR_TYPE);
        switch (factorType) {
            case FACTOR_TYPE_QUESTION:
                return questionFactor(factor, stateToken);
            case FACTOR_TYPE_SMS:
                return smsFactor(factor, stateToken);
            case FACTOR_TYPE_TOKEN:
            case FACTOR_TYPE_TOKEN_HARDWARE:
            case FACTOR_TYPE_TOKEN_SOFTWARE_TOTP:
                return totpFactor(factor, stateToken);
            case FACTOR_TYPE_PUSH:
                return pushFactor(factor, stateToken);
            default:
                throw new IllegalArgumentException("Unsupported factor type " + factorType);
        }
    }

    /**
     * Handles the Security Question factor
     *
     * @param factor     A {@link JSONObject} representing the user's factor
     * @param stateToken The current state token
     * @return The session token
     * @throws IOException if a network or protocol error occurs
     */
    private static String questionFactor(JSONObject factor, String stateToken) throws IOException, InterruptedException {
        String question = factor.getJSONObject("profile").getString("questionText");
        Scanner scanner = new Scanner(System.in);
        String sessionToken = "";
        String answer = "";

        // Prompt the user for the Security Question Answer
        System.err.println("\nSecurity Question Factor Authentication\nEnter 'change factor' to use a different factor\n");
        while ("".equals(sessionToken)) {
            if (!"".equals(answer)) {
                System.err.println("Incorrect answer, please try again");
            }
            System.err.println(question);
            System.err.println("Answer: ");
            answer = scanner.nextLine();

            // Factor change requested
            if (answer.equalsIgnoreCase(CHANGE_FACTOR)) {
                return answer;
            }

            // Verify the answer's validity
            sessionToken = verifyAnswer(answer, factor, stateToken, FACTOR_TYPE_QUESTION);
        }

        return sessionToken;
    }

    /**
     * Handles the SMS Verification factor
     *
     * @param factor     A {@link JSONObject} representing the user's factor
     * @param stateToken The current state token
     * @return The session token
     * @throws JSONException if the JSON response cannot be parsed
     * @throws IOException if the network connection fails
     * @throws InterruptedException if the process is interrupted during the HTTP request
     */
    private static String smsFactor(JSONObject factor, String stateToken) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        String answer = "";
        String sessionToken = "";

        System.err.println("\nSMS Factor Authentication \nEnter 'change factor' to use a different factor");
        while ("".equals(sessionToken)) {
            if (!"".equals(answer)) {
                System.err.println("Incorrect passcode, please try again or type 'new code' to be sent a new SMS passcode");
            } else {
                // Send initial code to the user
                verifyAnswer("", factor, stateToken, FACTOR_TYPE_SMS);
            }

            System.err.println("SMS Code: ");
            answer = scanner.nextLine();

            switch (answer.toLowerCase()) {
                case "new code":
                    // New SMS passcode requested
                    answer = "";
                    System.err.println("New code sent! \n");
                    break;
                case CHANGE_FACTOR:
                    // Factor change requested
                    return answer;
                default:
                    break;
            }

            // Verify the validity of the SMS passcode
            sessionToken = verifyAnswer(answer, factor, stateToken, FACTOR_TYPE_SMS);
        }

        return sessionToken;
    }

    /**
     * Handles Token Factor verification
     *
     * @param factor     A {@link JSONObject} representing the user's factor
     * @param stateToken The current state token
     * @return The session token
     * @throws IOException if a network or protocol error occurs
     */
    private static String totpFactor(JSONObject factor, String stateToken) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        String sessionToken = "";
        String answer = "";

        // Prompt for token
        System.err.println("\n" + factor.getString("provider") + " Token Factor Authentication\nEnter 'change factor' to use a different factor");
        while ("".equals(sessionToken)) {
            if (!"".equals(answer)) {
                System.err.println("Invalid token, please try again");
            }

            System.err.println("Token: ");
            answer = scanner.nextLine();

            // Factor change requested
            if (answer.equalsIgnoreCase(CHANGE_FACTOR)) {
                return answer;
            }

            // Verify the validity of the token
            sessionToken = verifyAnswer(answer, factor, stateToken, FACTOR_TYPE_TOKEN_SOFTWARE_TOTP);
        }

        return sessionToken;
    }


    /**
     * Handles Push verification
     *
     * @param factor     A {@link JSONObject} representing the user's factor
     * @param stateToken The current state token
     * @return The session token
     * @throws IOException if the connection fails when attempting the request
     */
    private static String pushFactor(JSONObject factor, String stateToken) throws IOException, InterruptedException {
        String sessionToken = "";
        System.err.println("\nPush Factor Authentication");

        while ("".equals(sessionToken)) {
            // Verify if Okta Push has been verified
            sessionToken = verifyAnswer(null, factor, stateToken, FACTOR_TYPE_PUSH);

            // Session has timed out
            if (sessionToken.equals("Timeout")) {
                System.err.println("Session has timed out");
                return "timeout";
            }
        }

        return sessionToken;
    }

    /**
     * Handles verification for all factor types
     *
     * @param answer     The answer to the factor
     * @param factor     A {@link JSONObject} representing the factor to be verified
     * @param stateToken The current state token
     * @param factorType The factor type
     * @return The session token
     */
    private static String verifyAnswer(String answer, JSONObject factor, String stateToken, String factorType) throws IOException, InterruptedException {
        JSONObject profile = new JSONObject();
        String verifyPoint = factor.getJSONObject(LINKS).getJSONObject("verify").getString("href");

        profile.put(STATE_TOKEN, stateToken);

        if (answer != null && !"".equals(answer)) {
            profile.put("answer", answer);
        }

        JSONObject verifyResponse = postAndGetJsonResponse(profile, verifyPoint);

        if (verifyResponse.has("errorCode")) {
            String errorSummary = verifyResponse.getString("errorSummary");
            System.err.println(errorSummary);

            return ERROR_OBTAINING_TOKEN;
        }

        validateStatus(verifyResponse);

        if (factorType.equals(FACTOR_TYPE_PUSH) && verifyResponse.has(LINKS)) {
            return handlePushPolling(profile, verifyResponse);
        } else {
            if (verifyResponse.has(SESSION_TOKEN)) {
                return verifyResponse.getString(SESSION_TOKEN);
            }
            return "";
        }
    }

    private static String handlePushPolling(JSONObject profile, JSONObject jsonObjResponse) throws IOException, InterruptedException {
        String pollUrl = getPollURL(jsonObjResponse);

        JSONObject pollResult = postAndGetJsonResponse(profile, pollUrl);
        String result = pollResult.getString(FACTOR_RESULT);
        while ("WAITING".equals(result)) {
            System.err.println("Waiting for you to approve the Okta push notification on your device...");
            Thread.sleep(500);
            pollResult = postAndGetJsonResponse(profile, pollUrl);
            String status = pollResult.getString(STATUS);
            if ("SUCCESS".equals(status)) {
                return pollResult.getString(SESSION_TOKEN);
            }
            result = pollResult.getString(FACTOR_RESULT);
        }
        return result;
    }

    private static String getPollURL(JSONObject jsonObjResponse) throws RuntimeException {
        JSONObject linksObj = jsonObjResponse.getJSONObject(LINKS);
        JSONArray linkNames = linksObj.names();
        JSONArray links = linksObj.toJSONArray(linkNames);
        JSONObject pollLink = null;
        for (int i = 0; i < links.length(); i++) {
            JSONObject link = links.getJSONObject(i);
            String linkName = link.getString("name");
            if (linkName.equals("poll")) {
                pollLink = link;
                break;
            }
        }
        if (pollLink == null) {
            throw new IllegalStateException("Could not determine URL for MFA polling");
        }
        return pollLink.getString("href");
    }

    private static JSONObject postAndGetJsonResponse(JSONObject profile, String url) throws IOException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader("Accept", CONTENT_TYPE_APPLICATION_JSON);
        httpPost.addHeader("Content-Type", CONTENT_TYPE_APPLICATION_JSON);
        httpPost.addHeader("Cache-Control", "no-cache");

        StringEntity entity = new StringEntity(profile.toString(), StandardCharsets.UTF_8);
        entity.setContentType(CONTENT_TYPE_APPLICATION_JSON);
        httpPost.setEntity(entity);

        try (CloseableHttpClient httpClient = HttpHelper.createClient();
             CloseableHttpResponse responseAuthenticate = httpClient.execute(httpPost)) {
            String outputAuthenticate = EntityUtils.toString(responseAuthenticate.getEntity());
            return new JSONObject(outputAuthenticate);
        }
    }

    private static void validateStatus(JSONObject jsonObjResponse) {
        String status = jsonObjResponse.getString(STATUS);
        try {
            TransactionState state = TransactionState.valueOf(status.toUpperCase());
            switch (state) {
                case MFA_REQUIRED:
                    // attempt failed, downstream code already handles this
                    break;
                case MFA_CHALLENGE:
                    // push authentication requires polling, downstream code handles that
                    break;
                case SUCCESS:
                    // MFA succeeded, let downstream code do its thing
                    break;
                case PASSWORD_WARN:
                    JSONObject embedded = jsonObjResponse.getJSONObject("_embedded");
                    JSONObject policy = embedded.getJSONObject("policy");
                    JSONObject expiration = policy.getJSONObject("expiration");
                    int passwordExpireDays = expiration.getInt("passwordExpireDays");
                    System.err.format("WARN: Your password will expire in %d days.", passwordExpireDays);
                    break;
                case PASSWORD_EXPIRED:
                    throw new IllegalStateException("Your password has expired");
                case MFA_ENROLL:
                    throw new IllegalStateException("Factor enrolment required (not yet supported)");
                default:
                    throw OktaAuthentication.makeException(
                            jsonObjResponse,
                            "Handling for the received status code is not currently implemented.\n" +
                            "The status code received was:\n%s: %s",
                            state.toString(),
                            state.getDescription());
            }
        } catch (IllegalArgumentException e) {
            throw OktaAuthentication.makeException(
                    jsonObjResponse,
                    e,
                    "The response message contained an unrecognized value \"%s\" for property \"%s\".",
                    status,
                    STATUS);
        }
    }
}
