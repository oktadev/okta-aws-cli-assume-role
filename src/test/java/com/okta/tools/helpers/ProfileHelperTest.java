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
package com.okta.tools.helpers;

import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLResult;
import com.amazonaws.services.securitytoken.model.AssumedRoleUser;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.okta.tools.OktaAwsCliEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProfileHelperTest {

    private static final String fakeAccessKey = "Fake-access-key";
    private static final String fakeSecretKey = "Fake-secret-key";
    private static final String fakeAwsRegion = "Fake-region";
    private static final String fakeSessionToken = "Fake-session-token";
    private static final String fakeCredentialsProfileName = "arn:aws:sts::123456789012:assumed-role/FakeRole/fakey.mcfakerson@fake.example.com";
    private static final String fakeAssumeRoleUserArn = "arn:aws:sts::123456789012:assumed-role/FakeRole/fakey.mcfakerson@fake.example.com";
    private static final String expectedGeneratedProfileName = "FakeRole_123456789012";
    private static final Date fakeExpiryDate = Date.from(Instant.EPOCH);
    private static final String specifiedOktaProfile = "test";
    private static final String tempProfileNameFallback = "temp";

    private ProfileHelper profileHelper;
    private CredentialsHelper credentialsHelper;
    private AssumeRoleWithSAMLResult assumeRoleWithSAMLResult;
    private OktaAwsCliEnvironment environment;

    @BeforeEach
    void setUp() {
        credentialsHelper = mock(CredentialsHelper.class);
        environment = new OktaAwsCliEnvironment(false, null, null, null, null, null, null, null, 0, fakeAwsRegion, null, false);
        profileHelper = new ProfileHelper(credentialsHelper, environment);
        assumeRoleWithSAMLResult = new AssumeRoleWithSAMLResult();
        Credentials credentials = new Credentials(fakeAccessKey, fakeSecretKey, fakeSessionToken, fakeExpiryDate);
        assumeRoleWithSAMLResult.setCredentials(credentials);
        AssumedRoleUser assumedRoleUser = new AssumedRoleUser();
        assumedRoleUser.setArn(fakeAssumeRoleUserArn);
        assumeRoleWithSAMLResult.setAssumedRoleUser(assumedRoleUser);
    }

    @Test
    void createAwsProfile() throws IOException {
        profileHelper.createAwsProfile(assumeRoleWithSAMLResult, fakeCredentialsProfileName);

        verify(credentialsHelper).updateCredentialsFile(fakeCredentialsProfileName, fakeAccessKey, fakeSecretKey, fakeAwsRegion, fakeSessionToken);
    }

    @Test
    void getProfileName() {
        String profileName = profileHelper.getProfileName(assumeRoleWithSAMLResult);

        assertEquals(expectedGeneratedProfileName, profileName);
    }

    @Test
    void getProfileNameWithSpecifiedOktaProfile() {
        environment.oktaProfile = specifiedOktaProfile;

        String profileName = profileHelper.getProfileName(assumeRoleWithSAMLResult);

        assertEquals(specifiedOktaProfile, profileName);
    }

    @Test
    void getProfileNameWithBrokenAssumedUserArnUsesTemp() {
        assumeRoleWithSAMLResult.getAssumedRoleUser().setArn("brokenARN");

        String profileName = profileHelper.getProfileName(assumeRoleWithSAMLResult);

        assertEquals(tempProfileNameFallback, profileName);
    }
}
