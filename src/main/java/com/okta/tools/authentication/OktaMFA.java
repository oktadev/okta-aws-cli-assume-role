package com.okta.tools.authentication;

import com.okta.tools.helpers.MenuHelper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class OktaMFA
{
    /**
     * Prompt the user for 2FA after primary authentication
     * @param authResponse The response from primary auth
     * @return The session token
     */
    public static String promptForFactor(JSONObject authResponse)
    {
        try {
            //User selects which factor to use
            JSONObject factor = selectFactor(authResponse);
            String factorType = factor.getString("factorType");
            String stateToken = authResponse.getString("stateToken");

            //factor selection handler
            switch (factorType) {
                case ("question"): {
                    //question factor handler
                    String sessionToken = questionFactor(factor, stateToken);
                    if (sessionToken.equals("change factor")) {
                        System.out.println("Factor Change Initiated");
                        return promptForFactor(authResponse);
                    }
                    return sessionToken;
                }
                case ("sms"): {
                    //sms factor handler
                    String sessionToken = smsFactor(factor, stateToken);
                    if (sessionToken.equals("change factor")) {
                        System.out.println("Factor Change Initiated");
                        return promptForFactor(authResponse);
                    }
                    return sessionToken;

                }
                case ("token:hardware"):
                case ("token:software:totp"): {
                    //token factor handler
                    String sessionToken = totpFactor(factor, stateToken);
                    if (sessionToken.equals("change factor")) {
                        System.out.println("Factor Change Initiated");
                        return promptForFactor(authResponse);
                    }
                    return sessionToken;
                }
                case ("push"): {
                    //push factor handles
                    String result = pushFactor(factor, stateToken);
                    if (result.equals("timeout") || result.equals("change factor")) {
                        return promptForFactor(authResponse);
                    }
                    return result;
                }
            }
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    private static JSONObject selectFactor(JSONObject authResponse) throws JSONException
    {
        JSONArray factors = authResponse.getJSONObject("_embedded").getJSONArray("factors");
        String factorType;
        System.out.println("\nMulti-Factor authentication is required. Please select a factor to use.");

        List<JSONObject> supportedFactors = getUsableFactors(factors);
        if (supportedFactors.isEmpty())
            if (factors.length() > 0)
                throw new IllegalStateException("None of your factors are supported.");
            else
                throw new IllegalStateException("You have no factors enrolled.");

        System.out.println("Factors:");
        for (int i = 0; i < supportedFactors.size(); i++) {
            JSONObject factor = supportedFactors.get(i);
            factorType = factor.getString("factorType");
            switch (factorType) {
                case "question":
                    factorType = "Security Question";
                    break;
                case "sms":
                    factorType = "SMS Authentication";
                    break;
                case "token:software:totp":
                    String provider = factor.getString("provider");
                    if (provider.equals("GOOGLE")) {
                        factorType = "Google Authenticator";
                    } else {
                        factorType = "Okta Verify";
                    }
                    break;
            }
            System.out.println("[ " + (i + 1) + " ] : " + factorType);
        }

        //Handles user factor selection
        int selection = MenuHelper.promptForMenuSelection(supportedFactors.size());
        return supportedFactors.get(selection);
    }

    private static List<JSONObject> getUsableFactors(JSONArray factors)
    {
        List<JSONObject> eligibleFactors = new ArrayList<>();
        for (int i = 0; i < factors.length(); i++) {
            JSONObject factor = factors.getJSONObject(i);
            // web-type factors can't be verified from the CLI
            if (!"web".equals(factor.getString("factorType"))) {
                eligibleFactors.add(factor);
            }
        }
        return eligibleFactors;
    }

    private static String questionFactor(JSONObject factor, String stateToken) throws JSONException, IOException {
        String question = factor.getJSONObject("profile").getString("questionText");
        Scanner scanner = new Scanner(System.in);
        String sessionToken = "";
        String answer = "";

        //prompt user for answer
        System.out.println("\nSecurity Question Factor Authentication\nEnter 'change factor' to use a different factor\n");
        while ("".equals(sessionToken)) {
            if (!"".equals(answer)) {
                System.out.println("Incorrect answer, please try again");
            }
            System.out.println(question);
            System.out.println("Answer: ");
            answer = scanner.nextLine();
            //verify answer is correct
            if (answer.toLowerCase().equals("change factor")) {
                return answer;
            }
            sessionToken = verifyAnswer(answer, factor, stateToken, "question");
        }
        return sessionToken;
    }


    /**
     * Handles sms factor authentication
     * Precondition: question factor as JSONObject factor, current state token stateToken
     * Postcondition: return session token as String sessionToken
     */
    private static String smsFactor(JSONObject factor, String stateToken) throws JSONException, IOException {
        Scanner scanner = new Scanner(System.in);
        String answer = "";
        String sessionToken = "";

        System.out.println("\nSMS Factor Authentication \nEnter 'change factor' to use a different factor");
        while ("".equals(sessionToken)) {
            if (!"".equals(answer)) {
                System.out.println("Incorrect passcode, please try again or type 'new code' to be sent a new sms token");
            } else {
                //send initial code to user
                verifyAnswer("", factor, stateToken, "sms");
            }
            System.out.println("SMS Code: ");
            answer = scanner.nextLine();
            switch (answer.toLowerCase()) {
                case "new code":
                    answer = "";
                    System.out.println("New code sent! \n");
                    break;
                case "change factor":
                    return answer;
            }
            sessionToken = verifyAnswer(answer, factor, stateToken, "sms");
        }
        return sessionToken;
    }

    /**
     * Handles token factor authentication, i.e: Google Authenticator or Okta Verify
     * Precondition: question factor as JSONObject factor, current state token stateToken
     * Postcondition: return session token as String sessionToken
     */
    private static String totpFactor(JSONObject factor, String stateToken) throws JSONException, IOException {
        Scanner scanner = new Scanner(System.in);
        String sessionToken = "";
        String answer = "";

        //prompt for token
        System.out.println("\n" + factor.getString("provider") + " Token Factor Authentication\nEnter 'change factor' to use a different factor");
        while ("".equals(sessionToken)) {
            if (!"".equals(answer)) {
                System.out.println("Invalid token, please try again");
            }

            System.out.println("Token: ");
            answer = scanner.nextLine();
            //verify auth Token
            if (answer.toLowerCase().equals("change factor")) {
                return answer;
            }
            sessionToken = verifyAnswer(answer, factor, stateToken, "token:software:totp");
        }
        return sessionToken;
    }


    /**
     * Handles push factor authentication
     */
    private static String pushFactor(JSONObject factor, String stateToken) throws JSONException, IOException {
        String sessionToken = "";

        System.out.println("\nPush Factor Authentication");
        while ("".equals(sessionToken)) {
            //Verify if Okta Push has been pushed
            sessionToken = verifyAnswer(null, factor, stateToken, "push");
            System.out.println(sessionToken);
            if (sessionToken.equals("Timeout")) {
                System.out.println("Session has timed out");
                return "timeout";
            }
        }
        return sessionToken;
    }

    /**
     * Handles verification for all Factor types
     * Precondition: question factor as JSONObject factor, current state token stateToken
     * Postcondition: return session token as String sessionToken
     */
    private static String verifyAnswer(String answer, JSONObject factor, String stateToken, String factorType)
            throws JSONException, IOException {

        String sessionToken = null;

        JSONObject profile = new JSONObject();
        String verifyPoint = factor.getJSONObject("_links").getJSONObject("verify").getString("href");

        profile.put("stateToken", stateToken);

        if (answer != null && !"".equals(answer)) {
            profile.put("answer", answer);
        }

        //create post request
        CloseableHttpClient httpClient = HttpClients.createSystem();

        HttpPost httpost = new HttpPost(verifyPoint);
        httpost.addHeader("Accept", "application/json");
        httpost.addHeader("Content-Type", "application/json");
        httpost.addHeader("Cache-Control", "no-cache");

        StringEntity entity = new StringEntity(profile.toString(), StandardCharsets.UTF_8);
        entity.setContentType("application/json");
        httpost.setEntity(entity);
        CloseableHttpResponse responseAuthenticate = httpClient.execute(httpost);

        BufferedReader br = new BufferedReader(new InputStreamReader((responseAuthenticate.getEntity().getContent())));

        String outputAuthenticate = br.readLine();
        JSONObject jsonObjResponse = new JSONObject(outputAuthenticate);

        if (jsonObjResponse.has("errorCode")) {
            String errorSummary = jsonObjResponse.getString("errorSummary");
            System.out.println(errorSummary);
            System.out.println("Please try again");
            if (factorType.equals("question")) {
                questionFactor(factor, stateToken);
            }

            if (factorType.equals("token:software:totp")) {
                totpFactor(factor, stateToken);
            }
        }

        if (jsonObjResponse.has("sessionToken"))
            sessionToken = jsonObjResponse.getString("sessionToken");

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


                while (pushResult == null || pushResult.equals("WAITING")) {
                    pushResult = null;
                    httpClient = HttpClients.createSystem();

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

                    System.out.println("Waiting for you to approve the Okta push notification on your device...");
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
}
