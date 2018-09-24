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

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.amazonaws.services.securitytoken.model.Credentials;
import com.okta.tools.helpers.*;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLResult;
import com.okta.tools.models.Profile;
import com.okta.tools.models.Session;
import com.okta.tools.saml.OktaSaml;

final class OktaAwsCliAssumeRole {

    private static final Logger logger = LogManager.getLogger(OktaAwsCliAssumeRole.class);

    private OktaAwsCliEnvironment environment;

    private SessionHelper sessionHelper;
    private ConfigHelper configHelper;
    private RoleHelper roleHelper;
    private CredentialsHelper credentialsHelper;
    private ProfileHelper profileHelper;

    private OktaSaml oktaSaml;

    private Optional<Session> currentSession;
    private Optional<Profile> currentProfile;

    static OktaAwsCliAssumeRole withEnvironment(OktaAwsCliEnvironment environment) {
        return new OktaAwsCliAssumeRole(environment);
    }

    private OktaAwsCliAssumeRole(OktaAwsCliEnvironment environment) {
        this.environment = environment;
    }

    private void init() throws Exception {
        CookieHelper cookieHelper = new CookieHelper(environment);
        sessionHelper = new SessionHelper(environment, cookieHelper);
        configHelper = new ConfigHelper(environment);
        roleHelper = new RoleHelper(environment);
        credentialsHelper  = new CredentialsHelper(environment);
        profileHelper = new ProfileHelper(credentialsHelper, environment);

        oktaSaml = new OktaSaml(environment, cookieHelper);

        currentSession = sessionHelper.getCurrentSession();

        if (StringUtils.isEmpty(environment.oktaProfile)) {
            if (currentSession.isPresent()) {
                environment.oktaProfile = currentSession.get().profileName;
            }
        }

        currentProfile = sessionHelper.getFromMultipleProfiles();
    }

    RunResult run(Instant startInstant) throws Exception {
        init();

        environment.awsRoleToAssume = currentProfile.map(profile1 -> profile1.roleArn).orElse(environment.awsRoleToAssume);

        if (currentSession.isPresent() && sessionHelper.sessionIsActive(startInstant, currentSession.get()) &&
                StringUtils.isBlank(environment.oktaProfile)) {
            RunResult runResult = new RunResult();
            runResult.profileName = currentSession.get().profileName;
            return runResult;
        }

        ProfileSAMLResult profileSAMLResult = doRequest(startInstant);

        RunResult runResult = new RunResult();
        runResult.profileName = profileSAMLResult.profileName;
        Credentials credentials = profileSAMLResult.assumeRoleWithSAMLResult.getCredentials();
        runResult.accessKeyId = credentials.getAccessKeyId();
        runResult.secretAccessKey = credentials.getSecretAccessKey();
        runResult.sessionToken = credentials.getSessionToken();

        return runResult;
    }

    class RunResult {
        String profileName;
        String accessKeyId;
        String secretAccessKey;
        String sessionToken;
    }

    AssumeRoleWithSAMLResult getAssumeRoleWithSAMLResult(Instant startInstant) throws Exception {
        init();

        environment.awsRoleToAssume = currentProfile.map(profile1 -> profile1.roleArn).orElse(null);

        ProfileSAMLResult profileSAMLResult = doRequest(startInstant);

        return profileSAMLResult.assumeRoleWithSAMLResult;
    }

    private ProfileSAMLResult doRequest(Instant startInstant) throws IOException {
        String samlResponse = oktaSaml.getSamlResponse();
        AssumeRoleWithSAMLRequest assumeRequest = roleHelper.chooseAwsRoleToAssume(samlResponse);
        Instant sessionExpiry = startInstant.plus(assumeRequest.getDurationSeconds() - 30, ChronoUnit.SECONDS);
        AssumeRoleWithSAMLResult assumeResult = roleHelper.assumeChosenAwsRole(assumeRequest);

        String profileName = profileHelper.getProfileName(assumeResult, environment.oktaProfile);
        if (!environment.oktaEnvMode) {
            profileHelper.createAwsProfile(assumeResult, profileName);
            updateConfig(assumeRequest, sessionExpiry, profileName);
        }

        return new ProfileSAMLResult(assumeResult, profileName);
    }

    private void updateConfig(AssumeRoleWithSAMLRequest assumeRequest, Instant sessionExpiry, String profileName) throws IOException {
        environment.oktaProfile = profileName;
        environment.awsRoleToAssume = assumeRequest.getRoleArn();
        configHelper.updateConfigFile();
        sessionHelper.addOrUpdateProfile(sessionExpiry);
        sessionHelper.updateCurrentSession(sessionExpiry, profileName);
    }

    // Holds the values for the profile name and SAML result shared by CLI and SDK implementations
    private class ProfileSAMLResult {
        String profileName;
        AssumeRoleWithSAMLResult assumeRoleWithSAMLResult;

        ProfileSAMLResult(AssumeRoleWithSAMLResult pAssumeRoleWithSAMLResult, String pProfileName) {
            assumeRoleWithSAMLResult = pAssumeRoleWithSAMLResult;
            profileName = pProfileName;
        }
    }

    public void logoutSession() throws Exception {
        init();

        sessionHelper.logoutCurrentSession();
    }
}
