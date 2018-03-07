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

import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLResult;
import com.okta.tools.authentication.OktaAuthentication;
import com.okta.tools.aws.settings.Profile;
import com.okta.tools.helpers.ConfigHelper;
import com.okta.tools.helpers.ProfileHelper;
import com.okta.tools.helpers.RoleHelper;
import com.okta.tools.helpers.SessionHelper;
import com.okta.tools.saml.OktaSaml;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Scanner;

final class OktaAwsCliAssumeRole {

    private static final Logger logger = LogManager.getLogger(OktaAwsCliAssumeRole.class);

    private final String oktaOrg;
    private final String oktaAWSAppURL;
    private final String oktaUsername;
    private final String oktaPassword;
    private final String awsRoleToAssume;
    private final String oktaProfile;

    static OktaAwsCliAssumeRole createOktaAwsCliAssumeRole(String oktaOrg, String oktaAWSAppURL, String oktaAWSUsername, String oktaAWSPassword, String oktaProfile, String oktaAWSRoleToAssume) {
        return new OktaAwsCliAssumeRole(oktaOrg, oktaAWSAppURL, oktaAWSUsername, oktaAWSPassword, oktaProfile, oktaAWSRoleToAssume);
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
        Optional<com.okta.tools.models.Session> session = SessionHelper.getCurrentSession();
        Profile profile = SessionHelper.getFromMultipleProfiles(oktaProfile);

        if (session.isPresent() && SessionHelper.sessionIsActive(startInstant, session.get()) && oktaProfile.isEmpty())
            return session.get().profileName;

        if (profile == null || startInstant.isAfter(profile.expiry)) {
            String oktaSessionToken = OktaAuthentication.getOktaSessionToken(getUsername(), getPassword(), oktaOrg);
            String samlResponse = OktaSaml.getSamlResponseForAws(oktaAWSAppURL, oktaSessionToken);
            AssumeRoleWithSAMLRequest assumeRequest = RoleHelper.chooseAwsRoleToAssume(samlResponse, awsRoleToAssume);
            Instant sessionExpiry = startInstant.plus(assumeRequest.getDurationSeconds() - 30, ChronoUnit.SECONDS);
            AssumeRoleWithSAMLResult assumeResult = RoleHelper.assumeChosenAwsRole(assumeRequest);
            String profileName = ProfileHelper.createAwsProfile(assumeResult, oktaProfile);

            ConfigHelper.updateConfigFile(profileName, assumeRequest.getRoleArn());
            SessionHelper.addOrUpdateProfile(profileName, assumeRequest.getRoleArn(), sessionExpiry);
            SessionHelper.updateCurrentSession(sessionExpiry, profileName);
            return profileName;
        }
        return oktaProfile;
    }

    public void logoutSession() throws IOException {
        SessionHelper.logoutCurrentSession(oktaProfile);
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
}
