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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

final class OktaAwsCliAssumeRole {

    private static final Logger logger = LogManager.getLogger(OktaAwsCliAssumeRole.class);

    private OktaAwsCliEnvironment environment;

    private SessionHelper sessionHelper;
    private ConfigHelper configHelper;
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

    private void init() throws Exception {
        sessionHelper = new SessionHelper(environment);
        configHelper = new ConfigHelper(environment);
        roleHelper = new RoleHelper(environment);
        profileHelper = new ProfileHelper(environment);

        oktaSaml = new OktaSaml(environment);

        currentSession = sessionHelper.getCurrentSession();

        if (StringUtils.isEmpty(environment.oktaProfile)) {
            if (currentSession.isPresent()) {
                environment.oktaProfile = currentSession.get().profileName;
            }
        }

        currentProfile = sessionHelper.getFromMultipleProfiles();
    }

    String run(Instant startInstant) throws Exception {
        init();

        environment.awsRoleToAssume = currentProfile.map(profile1 -> profile1.roleArn).orElse(null);

        if (currentSession.isPresent() && sessionHelper.sessionIsActive(startInstant, currentSession.get()) &&
                StringUtils.isBlank(environment.oktaProfile)) {
            return currentSession.get().profileName;
        }

        String samlResponse = oktaSaml.getSamlResponse();
        AssumeRoleWithSAMLRequest assumeRequest = roleHelper.chooseAwsRoleToAssume(samlResponse);
        Instant sessionExpiry = startInstant.plus(assumeRequest.getDurationSeconds() - 30, ChronoUnit.SECONDS);
        AssumeRoleWithSAMLResult assumeResult = roleHelper.assumeChosenAwsRole(assumeRequest);
        String profileName = profileHelper.createAwsProfile(assumeResult);

        environment.oktaProfile = profileName;
        environment.awsRoleToAssume = assumeRequest.getRoleArn();
        configHelper.updateConfigFile();
        sessionHelper.addOrUpdateProfile(sessionExpiry);
        sessionHelper.updateCurrentSession(sessionExpiry, profileName);

        return profileName;
    }

    public void logoutSession() throws Exception {
        init();

        sessionHelper.logoutCurrentSession();
    }
}
