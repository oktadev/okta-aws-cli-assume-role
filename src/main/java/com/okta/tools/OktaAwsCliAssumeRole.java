/*
 * Copyright 2017 Okta
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
package com.okta.tools;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLResult;
import com.okta.tools.aws.settings.Configuration;
import com.okta.tools.aws.settings.Credentials;
import com.okta.tools.saml.*;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ini4j.Profile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

final class OktaAwsCliAssumeRole {

    private static final Logger logger = LogManager.getLogger(OktaAwsCliAssumeRole.class);
    private static final String OKTA_AWS_CLI_SESSION_FILE = ".okta-aws-cli-session";
    private static final String OKTA_AWS_CLI_EXPIRY_PROPERTY = "OKTA_AWS_CLI_EXPIRY";
    private static final String OKTA_AWS_CLI_PROFILE_PROPERTY = "OKTA_AWS_CLI_PROFILE";

    private final String oktaOrg;
    private final String oktaAWSAppURL;
    private final String oktaUsername;
    private final String oktaPassword;
    private final String awsRoleToAssume;
    private final String oktaProfile;

    static OktaAwsCliAssumeRole createOktaAwsCliAssumeRole(String oktaOrg, String oktaAWSAppURL, String oktaAWSUsername, String oktaAWSPassword,String oktaProfile, String oktaAWSRoleToAssume) {
        return new OktaAwsCliAssumeRole(oktaOrg, oktaAWSAppURL, oktaAWSUsername, oktaAWSPassword,oktaProfile, oktaAWSRoleToAssume);
    }

    private OktaAwsCliAssumeRole(String oktaOrg, String oktaAWSAppURL, String oktaUsername, String oktaAWSPassword,String oktaProfile, String awsRoleToAssume) {
        this.oktaOrg = oktaOrg;
        this.oktaAWSAppURL = oktaAWSAppURL;
        this.oktaUsername = oktaUsername;
        this.oktaPassword = oktaAWSPassword;
        this.oktaProfile = oktaProfile;
        this.awsRoleToAssume = awsRoleToAssume;
    }

    private static String getSessionFile() {
        return getSessionFilePath().toString();
    }

    private static Path getSessionFilePath() {
        return Paths.get(System.getProperty("user.home") , OKTA_AWS_CLI_SESSION_FILE);
    }

    String run(Instant startInstant) throws Exception {
        Optional<Session> session = getCurrentSession();
        if (session.isPresent() && sessionIsActive(startInstant, session.get()))
            return session.get().profileName;
        String oktaSessionToken = getOktaSessionToken();
        String samlResponse = getSamlResponseForAws(oktaSessionToken);
        AssumeRoleWithSAMLRequest assumeRequest = chooseAwsRoleToAssume(samlResponse);
        Instant sessionExpiry = startInstant.plus(assumeRequest.getDurationSeconds() - 30, ChronoUnit.SECONDS);
        AssumeRoleWithSAMLResult assumeResult = assumeChosenAwsRole(assumeRequest);
        String profileName = createAwsProfile(assumeResult);

        updateConfigFile(profileName, assumeRequest.getRoleArn());
        updateCurrentSession(sessionExpiry, profileName);
        return profileName;
    }

    private static class Session
    {
        final String profileName;
        final Instant expiry;

        private Session(String profileName, Instant expiry) {
            this.profileName = profileName;
            this.expiry = expiry;
        }
    }

    public static void logoutSession() throws IOException {
        if (Files.exists(getSessionFilePath())) {
            Files.delete(getSessionFilePath());
        }
    }

    private Optional<Session> getCurrentSession() throws IOException {
        if (Files.exists(getSessionFilePath())) {
            Properties properties = new Properties();
            properties.load(new FileReader(getSessionFile()));
            String expiry = properties.getProperty(OKTA_AWS_CLI_EXPIRY_PROPERTY);
            String profileName = properties.getProperty(OKTA_AWS_CLI_PROFILE_PROPERTY);
            Instant expiryInstant = Instant.parse(expiry);
            return Optional.of(new Session(profileName, expiryInstant));
        }
        return Optional.empty();
    }
    private boolean sessionIsActive(Instant startInstant, Session session) {
        return startInstant.isBefore(session.expiry);
    }

    private String getOktaSessionToken() throws IOException {
        JSONObject authnResult = new JSONObject(getAuthnResponse());
        if (authnResult.getString("status").equals("MFA_REQUIRED")) {
            return promptForFactor(authnResult);
        } else {
            return authnResult.getString("sessionToken");
        }
    }

    private void updateCurrentSession(Instant expiryInstant, String profileName) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(OKTA_AWS_CLI_PROFILE_PROPERTY, profileName);
        properties.setProperty(OKTA_AWS_CLI_EXPIRY_PROPERTY, expiryInstant.toString());
        properties.store(new FileWriter(getSessionFile()), "Saved at: " + Instant.now().toString());
    }

    private String getAuthnResponse() throws IOException {
        while (true) {
            LoginResult response = logInToOkta(getUsername(), getPassword());
            int requestStatus = response.statusLine.getStatusCode();
            authnFailHandler(requestStatus);
            if (requestStatus == HttpStatus.SC_OK)
                return response.responseContent;
        }
    }

    private String getUsername() {
        if (this.oktaUsername == null || this.oktaUsername.isEmpty()) {
            System.out.print("Username: ");
            return new Scanner(System.in).next();
        } else {
            System.out.println("Username: " + oktaUsername);
            return this.oktaUsername;
        }
    }

    private String getPassword() {
        if (this.oktaPassword == null || this.oktaPassword.isEmpty()) {
            return promptForPassword();
        } else {
            return this.oktaPassword;
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

    private static final class LoginResult {
        final StatusLine statusLine;
        final String responseContent;

        private LoginResult(StatusLine statusLine, String responseContent) {
            this.statusLine = statusLine;
            this.responseContent = responseContent;
        }
    }
    /**
     * Uses user's credentials to obtain Okta session Token
     */
    private LoginResult logInToOkta(String username, String password) throws JSONException, IOException {
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

            return new LoginResult(authnResponse.getStatusLine(), byteArrayOutputStream.toString());
        }
    }

    private AssumeRoleWithSAMLResult assumeChosenAwsRole(AssumeRoleWithSAMLRequest assumeRequest) {
        BasicAWSCredentials nullCredentials = new BasicAWSCredentials("", "");
        AWSCredentialsProvider nullCredentialsProvider = new AWSStaticCredentialsProvider(nullCredentials);
        AWSSecurityTokenService sts = AWSSecurityTokenServiceClientBuilder
                .standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(nullCredentialsProvider)
                .build();
        return sts.assumeRoleWithSAML(assumeRequest);
    }

    private void authnFailHandler(int requestStatus) {
        if (requestStatus == 400 || requestStatus == 401) {
            logger.error("Invalid username or password.");
        } else if (requestStatus == 500) {
            logger.error("\nUnable to establish connection with: " + oktaOrg +
                    " \nPlease verify that your Okta org url is correct and try again");
        } else if (requestStatus != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + requestStatus);
        }
    }

    private int promptForMenuSelection(int max) {
        if (max == 1) return 0;
        Scanner scanner = new Scanner(System.in);

        int selection = -1;
        while (selection == -1) {
            //prompt user for selection
            System.out.print("Selection: ");
            String selectInput = scanner.nextLine();
            try {
                selection = Integer.parseInt(selectInput) - 1;
                if (selection < 0 || selection >= max) {
                    throw new InputMismatchException();
                }
            } catch (InputMismatchException e) {
                logger.error("Invalid input: Please enter a valid selection\n");
                selection = -1;
            } catch (NumberFormatException e) {
                logger.error("Invalid input: Please enter in a number \n");
                selection = -1;
            }
        }
        return selection;
    }

    private String getSamlResponseForAws(String oktaSessionToken) throws IOException {
        Document document = launchOktaAwsApp(oktaSessionToken);
        Elements samlResponseInputElement = document.select("form input[name=SAMLResponse]");
        if (samlResponseInputElement.isEmpty()) {
            throw new RuntimeException("You do not have access to AWS through Okta. \nPlease contact your administrator.");
        }
        return samlResponseInputElement.attr("value");
    }

    private Document launchOktaAwsApp(String oktaSessionToken) throws IOException {

        HttpGet httpget = new HttpGet(oktaAWSAppURL + "?onetimetoken=" + oktaSessionToken);
        try (CloseableHttpClient httpClient = HttpClients.createSystem();
             CloseableHttpResponse responseSAML = httpClient.execute(httpget)) {

            if (responseSAML.getStatusLine().getStatusCode() >= 500) {
                throw new RuntimeException("Server error when loading Okta AWS App: "
                        + responseSAML.getStatusLine().getStatusCode());
            } else if (responseSAML.getStatusLine().getStatusCode() >= 400) {
                throw new RuntimeException("Client error when loading Okta AWS App: "
                        + responseSAML.getStatusLine().getStatusCode());
            }

            return Jsoup.parse(
                    responseSAML.getEntity().getContent(),
                    StandardCharsets.UTF_8.name(),
                    oktaAWSAppURL
            );
        }
    }

    private AssumeRoleWithSAMLRequest chooseAwsRoleToAssume(String samlResponse) throws IOException {
        Map<String, String> roleIdpPairs = AwsSamlRoleUtils.getRoles(samlResponse);
        List<String> roleArns = new ArrayList<>();

        String principalArn;
        String roleArn;

        if (roleIdpPairs.size() > 1) {
            List<AccountOption> accountOptions = getAvailableRoles(samlResponse);

            System.out.println("\nPlease choose the role you would like to assume: ");
            System.out.println(roleIdpPairs.toString());
            System.out.println(accountOptions.toString());
            //Gather list of applicable AWS roles
            int i = 0;
            int j = -1;

            for (AccountOption accountOption : accountOptions) {
                System.out.println(accountOption.accountName);
                for (RoleOption roleOption : accountOption.roleOptions) {
                    roleArns.add(roleOption.roleArn);
                    System.out.println("\t[ " + (i + 1) + " ]: " + roleOption.roleName);
                    if (roleOption.roleArn.equals(awsRoleToAssume)) {
                        j = i;
                    }
                    i++;
                }
            }
            if ((awsRoleToAssume != null && !awsRoleToAssume.isEmpty()) && j == -1) {
                System.out.println("No match for role " + awsRoleToAssume);
            }

            // Default to no selection
            final int selection;

            // If config.properties has matching role, use it and don't prompt user to select
            if (j >= 0) {
                selection = j;
                System.out.println("Selected option "+ (j+1) + " based on OKTA_AWS_ROLE_TO_ASSUME value");
            } else {
                //Prompt user for role selection
                selection = promptForMenuSelection(roleArns.size());
            }

            roleArn = roleArns.get(selection);
            principalArn = roleIdpPairs.get(roleArn);
        } else {
            Map.Entry<String, String> role = roleIdpPairs.entrySet().iterator().next();
            System.out.println("Auto select role as only one is available : " + role.getKey());
            roleArn = role.getKey();
            principalArn = role.getValue();
        }

        return new AssumeRoleWithSAMLRequest()
                .withPrincipalArn(principalArn)
                .withRoleArn(roleArn)
                .withSAMLAssertion(samlResponse)
                .withDurationSeconds(3600);
    }

    private List<AccountOption> getAvailableRoles(String samlResponse) throws IOException {
        Document document = getSigninPageDocument(samlResponse);
        return AwsSamlSigninParser.parseAccountOptions(document);
    }

    private Document getSigninPageDocument(String samlResponse) throws IOException {
        HttpPost httpPost = new HttpPost("https://signin.aws.amazon.com/saml");
        UrlEncodedFormEntity samlForm = new UrlEncodedFormEntity(Arrays.asList(
                new BasicNameValuePair("SAMLResponse", samlResponse),
                new BasicNameValuePair("RelayState", "")
        ), StandardCharsets.UTF_8);
        httpPost.setEntity(samlForm);
        try (CloseableHttpClient httpClient = HttpClients.createSystem();
             CloseableHttpResponse samlSigninResponse = httpClient.execute(httpPost)) {
            return Jsoup.parse(
                    samlSigninResponse.getEntity().getContent(),
                    StandardCharsets.UTF_8.name(),
                    "https://signin.aws.amazon.com/saml"
            );
        }
    }

    private String createAwsProfile(AssumeRoleWithSAMLResult assumeResult) throws IOException {
        BasicSessionCredentials temporaryCredentials =
                new BasicSessionCredentials(
                        assumeResult.getCredentials().getAccessKeyId(),
                        assumeResult.getCredentials().getSecretAccessKey(),
                        assumeResult.getCredentials().getSessionToken());

        String awsAccessKey = temporaryCredentials.getAWSAccessKeyId();
        String awsSecretKey = temporaryCredentials.getAWSSecretKey();
        String awsSessionToken = temporaryCredentials.getSessionToken();

        String credentialsProfileName = getProfileName(assumeResult);
        updateCredentialsFile(credentialsProfileName, awsAccessKey, awsSecretKey, awsSessionToken);
        return credentialsProfileName;
    }

    private String getProfileName(AssumeRoleWithSAMLResult assumeResult) {
        String credentialsProfileName;
        if(StringUtils.isNotBlank(oktaProfile))  {
            credentialsProfileName = oktaProfile;
        } else {
            credentialsProfileName = assumeResult.getAssumedRoleUser().getArn();
            if (credentialsProfileName.startsWith("arn:aws:sts::")) {
                credentialsProfileName = credentialsProfileName.substring(13);
            }
            if (credentialsProfileName.contains(":assumed-role")) {
                credentialsProfileName = credentialsProfileName.replaceAll(":assumed-role", "");
            }
        }
        return credentialsProfileName;
    }

    private void updateCredentialsFile(String profileName, String awsAccessKey, String awsSecretKey, String awsSessionToken)
            throws IOException {
        File credentialsLocation = Paths.get(System.getProperty("user.home"))
                .resolve(".aws").resolve("credentials").toFile();
        try (final Reader reader = credentialsLocation.isFile() ?
                new FileReader(credentialsLocation) : new StringReader("")) {
            // Create the credentials object with the data read from credentialsLocation
            Credentials credentials = new Credentials(reader);

            // Write the given profile data
            credentials.addOrUpdateProfile(profileName, awsAccessKey, awsSecretKey, awsSessionToken);
            // Write the updated profile (reader is already closed by the Credentials constructor)
            try (final FileWriter fileWriter = new FileWriter(credentialsLocation)) {
                credentials.save(fileWriter);
            }
        }
    }

    private void updateConfigFile(String profileName, String roleToAssume) throws IOException {
        String configLocation = System.getProperty("user.home") + "/.aws/config";
        boolean newConfiguration = !new File(configLocation).isFile();
        try (Reader reader = newConfiguration ?
                new StringReader("") : new FileReader(configLocation)) {
            // Create the configuration object with the data read from configLocation
            Configuration configuration = new Configuration(reader);
            // Write the given profile data
            configuration.addOrUpdateProfile(profileName, roleToAssume);
            // Write the updated profile (reader is already closed by the Credentials constructor)
            try (FileWriter fileWriter = new FileWriter(configLocation)) {
                configuration.save(fileWriter);
            }
        }
    }

    private String promptForFactor(JSONObject authResponse) {

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

    private JSONObject selectFactor(JSONObject authResponse) throws JSONException {
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
        int selection = promptForMenuSelection(supportedFactors.size());
        return supportedFactors.get(selection);
    }

    private List<JSONObject> getUsableFactors(JSONArray factors) {
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


    private String questionFactor(JSONObject factor, String stateToken) throws JSONException, IOException {
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
    private String smsFactor(JSONObject factor, String stateToken) throws JSONException, IOException {
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
    private String totpFactor(JSONObject factor, String stateToken) throws JSONException, IOException {
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
    private String pushFactor(JSONObject factor, String stateToken) throws JSONException, IOException {
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
    private String verifyAnswer(String answer, JSONObject factor, String stateToken, String factorType)
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


        if (sessionToken != null)
            return sessionToken;
        else
            return pushResult;
    }
}
