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
package com.okta.tools;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.okta.tools.authentication.*;
import com.okta.tools.helpers.*;
import com.okta.tools.saml.OktaAppClient;
import com.okta.tools.saml.OktaAppClientImpl;
import org.apache.commons.lang.StringUtils;

import software.amazon.awssdk.services.sts.model.AssumeRoleWithSamlRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithSamlResponse;
import com.okta.tools.models.Profile;
import com.okta.tools.models.Session;
import com.okta.tools.saml.OktaSaml;
import software.amazon.awssdk.services.sts.model.Credentials;

final class OktaAwsCliAssumeRole {
    final private OktaAwsCliEnvironment environment;

    private SessionHelper sessionHelper;
    private RoleHelper roleHelper;
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

    private void init() throws IOException {
        CookieHelper cookieHelper = new CookieHelper(environment);
        CredentialsHelper credentialsHelper  = new CredentialsHelper();
        sessionHelper = new SessionHelper(environment, cookieHelper, credentialsHelper);
        roleHelper = new RoleHelper(environment);
        profileHelper = new ProfileHelper(credentialsHelper, environment);
        MenuHelper menuHelper = new MenuHelperImpl();
        OktaFactorSelector factorSelector = new OktaFactorSelectorImpl(environment, menuHelper);
        OktaMFA oktaMFA = new OktaMFA(factorSelector);
        UserConsole userConsole = new UserConsoleImpl();
        OktaAuthnClient oktaAuthnClient = new OktaAuthnClientImpl();
        OktaAuthentication oktaAuthentication = new OktaAuthentication(environment, oktaMFA, userConsole, oktaAuthnClient);
        OktaAppClient oktaAppClient = new OktaAppClientImpl(cookieHelper);

        oktaSaml = new OktaSaml(environment, oktaAuthentication, oktaAppClient);

        currentSession = sessionHelper.getCurrentSession();

        if (StringUtils.isEmpty(environment.oktaProfile) && currentSession.isPresent()) {
            environment.oktaProfile = currentSession.get().profileName;
        }

        currentProfile = sessionHelper.getFromMultipleProfiles();
    }

    RunResult run(Instant startInstant) throws IOException, InterruptedException {
        init();

        environment.awsRoleToAssume = currentProfile.map(profile1 -> profile1.roleArn).orElse(environment.awsRoleToAssume);

        if (currentSession.isPresent() && sessionHelper.sessionIsActive(startInstant, currentSession.get())) {
            RunResult runResult = new RunResult();
            runResult.profileName = currentSession.get().profileName;
            return runResult;
        }

        if (currentProfile.isPresent()) {
            Profile profile = currentProfile.get();
            Session profileSession = new Session(environment.oktaProfile, profile.expiry);
            if (sessionHelper.sessionIsActive(startInstant, profileSession)) {
                RunResult runResult = new RunResult();
                runResult.profileName = environment.oktaProfile;
                return runResult;
            }
        }

        ProfileSAMLResult profileSAMLResult = doRequest(startInstant);

        RunResult runResult = new RunResult();
        runResult.profileName = profileSAMLResult.profileName;
        Credentials credentials = profileSAMLResult.assumeRoleWithSAMLResult.credentials();
        runResult.accessKeyId = credentials.accessKeyId();
        runResult.secretAccessKey = credentials.secretAccessKey();
        runResult.sessionToken = credentials.sessionToken();

        return runResult;
    }

    class RunResult {
        String profileName;
        String accessKeyId;
        String secretAccessKey;
        String sessionToken;
    }

    AssumeRoleWithSamlResponse getAssumeRoleWithSAMLResult(Instant startInstant) throws IOException, InterruptedException {
        init();

        environment.awsRoleToAssume = currentProfile.map(profile1 -> profile1.roleArn).orElse(environment.awsRoleToAssume);

        ProfileSAMLResult profileSAMLResult = doRequest(startInstant);

        return profileSAMLResult.assumeRoleWithSAMLResult;
    }

    private ProfileSAMLResult doRequest(Instant startInstant) throws IOException, InterruptedException {
        String samlResponse = oktaSaml.getSamlResponse();
        AssumeRoleWithSamlRequest assumeRequest = roleHelper.chooseAwsRoleToAssume(samlResponse);
        Instant sessionExpiry = startInstant.plus((long) assumeRequest.durationSeconds() - (long) 30, ChronoUnit.SECONDS);
        AssumeRoleWithSamlResponse assumeResult = roleHelper.assumeChosenAwsRole(assumeRequest);

        String profileName = profileHelper.getProfileName(assumeResult);
        if (!environment.oktaEnvMode) {
            profileHelper.createAwsProfile(assumeResult, profileName);
            updateConfig(assumeRequest, sessionExpiry, profileName);
        }

        return new ProfileSAMLResult(assumeResult, profileName);
    }

    private void updateConfig(AssumeRoleWithSamlRequest assumeRequest, Instant sessionExpiry, String profileName) throws IOException {
        environment.oktaProfile = profileName;
        environment.awsRoleToAssume = assumeRequest.roleArn();
        sessionHelper.addOrUpdateProfile(sessionExpiry);
        sessionHelper.updateCurrentSession(sessionExpiry, profileName);
    }

    // Holds the values for the profile name and SAML result shared by CLI and SDK implementations
    static private class ProfileSAMLResult {
        String profileName;
        AssumeRoleWithSamlResponse assumeRoleWithSAMLResult;

        ProfileSAMLResult(AssumeRoleWithSamlResponse pAssumeRoleWithSAMLResult, String pProfileName) {
            assumeRoleWithSAMLResult = pAssumeRoleWithSAMLResult;
            profileName = pProfileName;
        }
    }

    void logoutSession() throws IOException {
        init();

        sessionHelper.logoutCurrentSession();
    }
}
