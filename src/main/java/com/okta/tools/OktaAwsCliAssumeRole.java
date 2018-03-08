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
import com.okta.tools.helpers.ConfigHelper;
import com.okta.tools.helpers.ProfileHelper;
import com.okta.tools.helpers.RoleHelper;
import com.okta.tools.helpers.SessionHelper;
import com.okta.tools.models.Profile;
import com.okta.tools.models.Session;
import com.okta.tools.saml.OktaSaml;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

final class OktaAwsCliAssumeRole {

    private static final Logger logger = LogManager.getLogger(OktaAwsCliAssumeRole.class);

    static OktaAwsCliAssumeRole createOktaAwsCliAssumeRole(String oktaOrg, String oktaAWSAppURL, String oktaAWSUsername, String oktaAWSPassword, String oktaProfile, String oktaAWSRoleToAssume) {
        return new OktaAwsCliAssumeRole(oktaOrg, oktaAWSAppURL, oktaAWSUsername, oktaAWSPassword, oktaProfile, oktaAWSRoleToAssume);
    }

    private OktaAwsCliAssumeRole(String oktaOrg, String oktaAWSAppURL, String oktaUsername, String oktaAWSPassword, String oktaProfile, String awsRoleToAssume) {
        OktaAwsCliEnvironment.oktaOrg = oktaOrg;
        OktaAwsCliEnvironment.oktaAwsAppUrl = oktaAWSAppURL;
        OktaAwsCliEnvironment.oktaUsername = oktaUsername;
        OktaAwsCliEnvironment.oktaPassword = oktaAWSPassword;
        OktaAwsCliEnvironment.oktaProfile = oktaProfile;
        OktaAwsCliEnvironment.awsRoleToAssume = awsRoleToAssume;
    }

    String run(Instant startInstant) throws Exception {
        Optional<com.okta.tools.models.Session> session = SessionHelper.getCurrentSession();
        Optional<Profile> profile = SessionHelper.getFromMultipleProfiles();
        OktaAwsCliEnvironment.awsRoleToAssume = profile.map(profile1 -> profile1.roleArn).orElse(null);

        if (session.isPresent() && SessionHelper.sessionIsActive(startInstant, session.get()) &&
                StringUtils.isBlank(OktaAwsCliEnvironment.oktaProfile)) {
            return session.get().profileName;
        }

        String samlResponse = OktaSaml.getSamlResponse();
        AssumeRoleWithSAMLRequest assumeRequest = RoleHelper.chooseAwsRoleToAssume(samlResponse);
        Instant sessionExpiry = startInstant.plus(assumeRequest.getDurationSeconds() - 30, ChronoUnit.SECONDS);
        AssumeRoleWithSAMLResult assumeResult = RoleHelper.assumeChosenAwsRole(assumeRequest);
        String profileName = ProfileHelper.createAwsProfile(assumeResult);
        ConfigHelper.updateConfigFile(profileName, assumeRequest.getRoleArn());
        SessionHelper.addOrUpdateProfile(profileName, assumeRequest.getRoleArn(), sessionExpiry);
        SessionHelper.updateCurrentSession(sessionExpiry, profileName);

        return profileName;
    }

    public void logoutSession() throws IOException {
        SessionHelper.logoutCurrentSession(OktaAwsCliEnvironment.oktaProfile);
    }
}
