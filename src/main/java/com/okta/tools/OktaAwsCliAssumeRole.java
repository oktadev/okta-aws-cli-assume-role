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

    private OktaAwsCliAssumeRole(String oktaOrg, String oktaAWSAppURL, String oktaUsername, String oktaAWSPassword,String oktaProfile, String awsRoleToAssume) {
        this.oktaOrg = oktaOrg;
        this.oktaAWSAppURL = oktaAWSAppURL;
        this.oktaUsername = oktaUsername;
        this.oktaPassword = oktaAWSPassword;
        this.oktaProfile = oktaProfile;
        this.awsRoleToAssume = awsRoleToAssume;
    }

    String run(Instant startInstant) throws Exception {
        Optional<Session> session = AwsSessionHelper.getCurrentSession();

        if (session.isPresent() && AwsSessionHelper.sessionIsActive(startInstant, session.get())) {
            return session.get().profileName;
        }

        String oktaSessionToken = OktaAuthentication.getOktaSessionToken(getUsername(), getPassword(), oktaOrg);
        String samlResponse = OktaSaml.getSamlResponseForAws(oktaAWSAppURL, oktaSessionToken);
        AssumeRoleWithSAMLRequest assumeRequest = chooseAwsRoleToAssume(samlResponse);
        Instant sessionExpiry = startInstant.plus(assumeRequest.getDurationSeconds() - 30, ChronoUnit.SECONDS);
        AssumeRoleWithSAMLResult assumeResult = assumeChosenAwsRole(assumeRequest);
        String profileName = createAwsProfile(assumeResult);

        updateConfigFile(profileName, assumeRequest.getRoleArn());
        AwsSessionHelper.updateCurrentSession(sessionExpiry, profileName);
        return profileName;
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
