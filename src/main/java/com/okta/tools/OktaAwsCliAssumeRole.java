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
import com.okta.tools.authentication.OktaAuthentication;
import com.okta.tools.aws.settings.Configuration;
import com.okta.tools.aws.settings.Credentials;
import com.okta.tools.aws.settings.MultipleProfile;
import com.okta.tools.models.Session;
import com.okta.tools.saml.*;
import com.okta.tools.helpers.*;
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

    private final String oktaOrg;
    private final String oktaAWSAppURL;
    private final String oktaUsername;
    private final String oktaPassword;
    private final String awsRoleToAssume;
    private final String oktaProfile;

    static OktaAwsCliAssumeRole createOktaAwsCliAssumeRole(String oktaOrg, String oktaAWSAppURL, String oktaAWSUsername, String oktaAWSPassword,String oktaProfile, String oktaAWSRoleToAssume) {
        return new OktaAwsCliAssumeRole(oktaOrg, oktaAWSAppURL, oktaAWSUsername, oktaAWSPassword,oktaProfile, oktaAWSRoleToAssume);
    }

    private OktaAwsCliAssumeRole(String oktaOrg, String oktaAWSAppURL, String oktaUsername, String oktaAWSPassword, String oktaProfile, String awsRoleToAssume) {
        this.oktaOrg = oktaOrg;
        this.oktaAWSAppURL = oktaAWSAppURL;
        this.oktaUsername = oktaUsername;
        this.oktaPassword = oktaAWSPassword;
        this.oktaProfile = oktaProfile;
        this.awsRoleToAssume = awsRoleToAssume;
    }

    String run(Instant startInstant) throws Exception {
        Optional<Session> session = getCurrentSession();
        Path multiprofile = getMultipleProfilesIniPath();
        FileReader reader = new FileReader(multiprofile.toFile());
        MultipleProfile multipleProfile = new MultipleProfile(reader);
        com.okta.tools.aws.settings.Profile profile = multipleProfile.getProfile(oktaProfile, multiprofile);


        if (session.isPresent() && sessionIsActive(startInstant, session.get()) && oktaProfile.isEmpty())
            return session.get().profileName;
        if (profile == null || startInstant.isAfter(profile.expiry)) {
                String oktaSessionToken = getOktaSessionToken();
                String samlResponse = getSamlResponseForAws(oktaSessionToken);
                AssumeRoleWithSAMLRequest assumeRequest = chooseAwsRoleToAssume(samlResponse);
                Instant sessionExpiry = startInstant.plus(assumeRequest.getDurationSeconds() - 30, ChronoUnit.SECONDS);
                AssumeRoleWithSAMLResult assumeResult = assumeChosenAwsRole(assumeRequest);
                String profileName = createAwsProfile(assumeResult);

                updateConfigFile(profileName, assumeRequest.getRoleArn());
                addOrUpdateProfile(profileName, assumeRequest.getRoleArn(), sessionExpiry);
                updateCurrentSession(sessionExpiry, profileName);
                return profileName;
        }
        return oktaProfile;
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

    public void logoutSession() throws IOException {
        if (oktaProfile != null) {
            logoutMulti(oktaProfile);
        }
        if (Files.exists(getSessionFilePath())) {
            Files.delete(getSessionFilePath());
        }
    }

    private void logoutMulti(String oktaProfile) throws IOException {
        Path multiprofile = getMultipleProfilesIniPath();
        String profilestore = multiprofile.toString();
        FileReader reader = new FileReader(multiprofile.toFile());
        MultipleProfile multipleProfile = new MultipleProfile(reader);
        multipleProfile.deleteProfile(profilestore,oktaProfile);
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

    private static Path getMultipleProfilesIniPath() throws IOException {
        Path userHome = Paths.get(System.getProperty("user.home"));
        Path oktaDir = userHome.resolve(".okta");
        Path profileIni = oktaDir.resolve("profiles");
        if (!Files.exists(oktaDir)) {
            Files.createDirectory(oktaDir);
        }
        if(!Files.exists(profileIni)) {
            Files.createFile(profileIni);
        }
        return profileIni;
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
                selection = MenuHelper.promptForMenuSelection(roleArns.size());
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

        try (Reader reader = AwsCredentialsHelper.getCredsReader())
        {
            // Create the credentials object with the data read from the credentials file
            Credentials credentials = new Credentials(reader);

            // Write the given profile data
            credentials.addOrUpdateProfile(profileName, awsAccessKey, awsSecretKey, awsSessionToken);

            // Write the updated profile
            try (FileWriter fileWriter = AwsCredentialsHelper.getCredsWriter())
            {
                credentials.save(fileWriter);
            }
        }
    }

    private void addOrUpdateProfile(String profileName,String oktaSession ,Instant start) throws IOException {
        String profileStore = getMultipleProfilesIniPath().toString();
        Reader reader = new FileReader(profileStore);
        MultipleProfile multipleProfile = new MultipleProfile(reader);
        multipleProfile.addOrUpdateProfile(profileName, oktaSession, start);
        try (final FileWriter fileWriter = new FileWriter(profileStore)) {
            multipleProfile.save(fileWriter);
        }
    }


    private void updateConfigFile(String profileName, String roleToAssume) throws IOException {
        try (Reader reader = AwsConfigHelper.getConfigReader()) {
            // Create the configuration object with the data from the config file
            Configuration configuration = new Configuration(reader);

            // Write the given profile data
            configuration.addOrUpdateProfile(profileName, roleToAssume);

            // Write the updated profile
            try (FileWriter fileWriter = AwsConfigHelper.getConfigWriter()) {
                configuration.save(fileWriter);
            }
        }
    }
}
