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
import com.okta.tools.models.Session;
import com.okta.tools.saml.OktaSaml;
import com.okta.tools.aws.settings.Configuration;
import com.okta.tools.aws.settings.Credentials;
import com.okta.tools.aws.settings.MultipleProfile;
import com.okta.tools.aws.settings.Profile;
import com.okta.tools.saml.*;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Scanner;
import java.util.*;
import java.util.stream.Collectors;

final class OktaAwsCliAssumeRole {

    private static final Logger logger = LogManager.getLogger(OktaAwsCliAssumeRole.class);

    private String awsRoleToAssume;
    private final String oktaProfile;

    static OktaAwsCliAssumeRole createOktaAwsCliAssumeRole(String oktaOrg, String oktaAWSAppURL, String oktaAWSUsername, String oktaAWSPassword, String oktaProfile, String oktaAWSRoleToAssume) {
        return new OktaAwsCliAssumeRole(oktaOrg, oktaAWSAppURL, oktaAWSUsername, oktaAWSPassword, oktaProfile, oktaAWSRoleToAssume);
    }

    private OktaAwsCliAssumeRole(String oktaOrg, String oktaAWSAppURL, String oktaUsername, String oktaAWSPassword, String oktaProfile, String awsRoleToAssume) {
        OktaAuthentication.oktaOrg = oktaOrg;
        OktaSaml.awsAppUrl = oktaAWSAppURL;
        OktaAuthentication.oktaUsername = oktaUsername;
        OktaAuthentication.oktaPassword = oktaAWSPassword;
        this.oktaProfile = oktaProfile;
        this.awsRoleToAssume = awsRoleToAssume;
    }

    String run(Instant startInstant) throws Exception {
        Optional<com.okta.tools.models.Session> session = SessionHelper.getCurrentSession();
        Optional<Profile> profile = SessionHelper.getFromMultipleProfiles(oktaProfile);
        awsRoleToAssume = profile.map(profile1 -> profile1.roleArn).orElse(null);

        if (session.isPresent() && SessionHelper.sessionIsActive(startInstant, session.get()) && oktaProfile.isEmpty())
            return session.get().profileName;

        String samlResponse = OktaSaml.getSamlResponse();
        AssumeRoleWithSAMLRequest assumeRequest = RoleHelper.chooseAwsRoleToAssume(samlResponse, awsRoleToAssume);
        Instant sessionExpiry = startInstant.plus(assumeRequest.getDurationSeconds() - 30, ChronoUnit.SECONDS);
        AssumeRoleWithSAMLResult assumeResult = RoleHelper.assumeChosenAwsRole(assumeRequest);
        String profileName = ProfileHelper.createAwsProfile(assumeResult, oktaProfile);
        ConfigHelper.updateConfigFile(profileName, assumeRequest.getRoleArn());
        SessionHelper.addOrUpdateProfile(profileName, assumeRequest.getRoleArn(), sessionExpiry);
        SessionHelper.updateCurrentSession(sessionExpiry, profileName);
        return profileName;
    }

    public void logoutSession() throws IOException {
        Optional<Session> currentSession = SessionHelper.getCurrentSession();
        if (currentSession.isPresent()) {
            SessionHelper.logoutCurrentSession(currentSession.get().profileName);
        } else {
            SessionHelper.logoutCurrentSession(null);
        }
    }
}
