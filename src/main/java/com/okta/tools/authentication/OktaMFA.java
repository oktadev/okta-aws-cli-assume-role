/*
 * Copyright 2018 Okta
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class OktaMFA {
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
    public String promptForFactor(JSONObject primaryAuthResponse) {
        try {
            // User selects which factor to use
            JSONObject factor = factorSelector.selectFactor(primaryAuthResponse);
            String factorType = factor.getString("factorType");
            String stateToken = primaryAuthResponse.getString("stateToken");

            // Factor selection handler
            switch (factorType) {
                case ("question"): {
                    // Security Question handler
                    String sessionToken = questionFactor(factor, stateToken);
                    return handleTimeoutsAndChanges(sessionToken, primaryAuthResponse);
                }
                case ("sms"): {
                    // SMS handler
                    String sessionToken = smsFactor(factor, stateToken);
                    return handleTimeoutsAndChanges(sessionToken, primaryAuthResponse);

                }
                case ("token"):
                case ("token:hardware"):
                case ("token:software:totp"): {
                    // Token handler
                    String sessionToken = totpFactor(factor, stateToken);
                    return handleTimeoutsAndChanges(sessionToken, primaryAuthResponse);
                }
                case ("push"): {
                    // Push handler
                    String sessionToken = pushFactor(factor, stateToken);
                    return handleTimeoutsAndChanges(sessionToken, primaryAuthResponse);
                }
            }
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    /**
     * Handles MFA timeouts and factor changes
     *
     * @param sessionToken        The current state of the MFA
     * @param primaryAuthResponse The response from Primary Authentication
     * @return The factor prompt if invalid, session token otherwise
     */
    private String handleTimeoutsAndChanges(String sessionToken, JSONObject primaryAuthResponse) {
        if (sessionToken.equals("change factor")) {
            System.err.println("Factor change initiated");
            return promptForFactor(primaryAuthResponse);
        } else if (sessionToken.equals("timeout")) {
            System.err.println("Factor timed out");
            return promptForFactor(primaryAuthResponse);
        }
        return sessionToken;
    }

    /**
     * Handles the Security Question factor
     *
     * @param factor     A {@link JSONObject} representing the user's factor
     * @param stateToken The current state token
     * @return The session token
     * @throws IOException if a network or protocol error occurs
     */
    private static String questionFactor(JSONObject factor, String stateToken) throws IOException {
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
            if (answer.toLowerCase().equals("change factor")) {
                return answer;
            }

            // Verify the answer's validity
            sessionToken = verifyAnswer(answer, factor, stateToken, "question");
        }

        return sessionToken;
    }

    /**
     * Handles the SMS Verification factor
     *
     * @param factor     A {@link JSONObject} representing the user's factor
     * @param stateToken The current state token
     * @return The session token
     * @throws JSONException
     * @throws IOException
     */
    private static String smsFactor(JSONObject factor, String stateToken) throws JSONException, IOException {
        Scanner scanner = new Scanner(System.in);
        String answer = "";
        String sessionToken = "";

        System.err.println("\nSMS Factor Authentication \nEnter 'change factor' to use a different factor");
        while ("".equals(sessionToken)) {
            if (!"".equals(answer)) {
                System.err.println("Incorrect passcode, please try again or type 'new code' to be sent a new SMS passcode");
            } else {
                // Send initial code to the user
                verifyAnswer("", factor, stateToken, "sms");
            }

            System.err.println("SMS Code: ");
            answer = scanner.nextLine();

            switch (answer.toLowerCase()) {
                case "new code":
                    // New SMS passcode requested
                    answer = "";
                    System.err.println("New code sent! \n");
                    break;
                case "change factor":
                    // Factor change requested
                    return answer;
            }

            // Verify the validity of the SMS passcode
            sessionToken = verifyAnswer(answer, factor, stateToken, "sms");
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
    private static String totpFactor(JSONObject factor, String stateToken) throws IOException {
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
            if (answer.toLowerCase().equals("change factor")) {
                return answer;
            }

            // Verify the validity of the token
            sessionToken = verifyAnswer(answer, factor, stateToken, "token:software:totp");
        }

        return sessionToken;
    }


    /**
     * Handles Push verification
     *
     * @param factor     A {@link JSONObject} representing the user's factor
     * @param stateToken The current state token
     * @return The session token
     * @throws IOException
     */
    private static String pushFactor(JSONObject factor, String stateToken) throws JSONException, IOException {
        String sessionToken = "";
        System.err.println("\nPush Factor Authentication");

        while ("".equals(sessionToken)) {
            // Verify if Okta Push has been verified
            sessionToken = verifyAnswer(null, factor, stateToken, "push");
            System.err.println(sessionToken);

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
    private static String verifyAnswer(String answer, JSONObject factor, String stateToken, String factorType) throws IOException {

        String sessionToken = null;

        JSONObject profile = new JSONObject();
        String verifyPoint = factor.getJSONObject("_links").getJSONObject("verify").getString("href");

        profile.put("stateToken", stateToken);

        if (answer != null && !"".equals(answer)) {
            profile.put("answer", answer);
        }

        // Create POST request
        CloseableHttpClient httpClient = HttpHelper.createClient();

        HttpPost httpPost = new HttpPost(verifyPoint);
        httpPost.addHeader("Accept", "application/json");
        httpPost.addHeader("Content-Type", "application/json");
        httpPost.addHeader("Cache-Control", "no-cache");

        StringEntity entity = new StringEntity(profile.toString(), StandardCharsets.UTF_8);
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        CloseableHttpResponse responseAuthenticate = httpClient.execute(httpPost);

        BufferedReader br = new BufferedReader(new InputStreamReader((responseAuthenticate.getEntity().getContent())));

        String outputAuthenticate = br.readLine();
        JSONObject jsonObjResponse = new JSONObject(outputAuthenticate);

        // An error has been returned by Okta
        if (jsonObjResponse.has("errorCode")) {
            String errorSummary = jsonObjResponse.getString("errorSummary");
            System.err.println(errorSummary);
            System.err.println("Please try again");

            if (factorType.equals("question")) {
                questionFactor(factor, stateToken);
            }

            if (factorType.equals("token:software:totp")) {
                sessionToken = totpFactor(factor, stateToken);
            }
        }

        validateStatus(jsonObjResponse);

        if (jsonObjResponse.has("sessionToken")) {
            sessionToken = jsonObjResponse.getString("sessionToken");
        }

        // Handle Push
        String pushResult = null;
        if (factorType.equals("push")) {
            if (jsonObjResponse.has("_links")) {
                JSONObject linksObj = jsonObjResponse.getJSONObject("_links");

                JSONArray names = linksObj.names();
                JSONArray links = linksObj.toJSONArray(names);
                String pollUrl = "";
                for (int i = 0; i < links.length(); i++) {
                    JSONObject link = links.getJSONObject(i);
                    String linkName = link.getString("name");
                    if (linkName.equals("poll")) {
                        pollUrl = link.getString("href");
                        break;
                    }
                }

                // Wait for push state change
                while (pushResult == null || pushResult.equals("WAITING")) {
                    pushResult = null;
                    httpClient = HttpHelper.createClient();

                    HttpPost pollReq = new HttpPost(pollUrl);
                    pollReq.addHeader("Accept", "application/json");
                    pollReq.addHeader("Content-Type", "application/json");
                    pollReq.addHeader("Cache-Control", "no-cache");

                    entity = new StringEntity(profile.toString(), StandardCharsets.UTF_8);
                    entity.setContentType("application/json");
                    pollReq.setEntity(entity);

                    CloseableHttpResponse responsePush = httpClient.execute(pollReq);

                    br = new BufferedReader(new InputStreamReader((responsePush.getEntity().getContent())));

                    String outputTransaction = br.readLine();
                    JSONObject jsonTransaction = new JSONObject(outputTransaction);

                    if (jsonTransaction.has("factorResult")) {
                        pushResult = jsonTransaction.getString("factorResult");
                    }

                    if (pushResult == null && jsonTransaction.has("status")) {
                        pushResult = jsonTransaction.getString("status");
                    }

                    System.err.println("Waiting for you to approve the Okta push notification on your device...");
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException iex) {
                        throw new RuntimeException(iex);
                    }

                    if (jsonTransaction.has("sessionToken")) {
                        sessionToken = jsonTransaction.getString("sessionToken");
                    }
                }
            }

        }

        if (sessionToken != null) {
            return sessionToken;
        } else {
            return pushResult;
        }
    }

    private static void validateStatus(JSONObject jsonObjResponse) {
        String status = jsonObjResponse.getString("status");
        try {
            TransactionState state = TransactionState.valueOf(status.toUpperCase());
            switch (state) {
                case MFA_REQUIRED:
                    // attempt failed, downstream code already handles this
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
                    "status");
        }
    }
}
