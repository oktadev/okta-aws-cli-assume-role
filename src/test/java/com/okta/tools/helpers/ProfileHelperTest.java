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

import com.okta.tools.OktaAwsCliEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithSamlResponse;
import software.amazon.awssdk.services.sts.model.AssumedRoleUser;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProfileHelperTest {

    private static final String fakeAccessKey = "Fake-access-key";
    private static final String fakeSecretKey = "Fake-secret-key";
    private static final Region fakeAwsRegion = Region.of("Fake-region");
    private static final String fakeSessionToken = "Fake-session-token";
    private static final String fakeCredentialsProfileName = "arn:aws:sts::123456789012:assumed-role/FakeRole/fakey.mcfakerson@fake.example.com";
    private static final String fakeGovCloudCredentialsProfileName = "arn:aws-gov:sts::123456789012:assumed-role/FakeRole/fakey.mcfakerson@fake.example.com";
    private static final String fakeAssumeRoleUserArn = "arn:aws:sts::123456789012:assumed-role/FakeRole/fakey.mcfakerson@fake.example.com";
    private static final String expectedGeneratedProfileName = "FakeRole_123456789012";
    private static final Instant fakeExpiryDate = Instant.EPOCH;
    private static final String specifiedOktaProfile = "test";
    private static final String tempProfileNameFallback = "temp";

    private ProfileHelper profileHelper;
    private CredentialsHelper credentialsHelper;
    private AssumeRoleWithSamlResponse assumeRoleWithSamlResult;
    private OktaAwsCliEnvironment environment;

    @BeforeEach
    void setUp() {
        credentialsHelper = mock(CredentialsHelper.class);
        environment = new OktaAwsCliEnvironment(false, null, null, null, null, null, null, null, 0, fakeAwsRegion, null, false, null);
        profileHelper = new ProfileHelper(credentialsHelper, environment);
        Credentials credentials = Credentials.builder()
                .accessKeyId(fakeAccessKey)
                .secretAccessKey(fakeSecretKey)
                .sessionToken(fakeSessionToken)
                .expiration(fakeExpiryDate)
                .build();
        AssumedRoleUser assumedRoleUser = AssumedRoleUser.builder().arn(fakeAssumeRoleUserArn).build();
        assumeRoleWithSamlResult = AssumeRoleWithSamlResponse.builder()
                .credentials(credentials)
                .assumedRoleUser(assumedRoleUser)
                .build();
    }

    @Test
    void createAwsProfile() throws IOException {
        profileHelper.createAwsProfile(assumeRoleWithSamlResult, fakeCredentialsProfileName);

        verify(credentialsHelper).updateCredentialsFile(fakeCredentialsProfileName, fakeAccessKey, fakeSecretKey, fakeAwsRegion, fakeSessionToken);
    }

    @Test
    void getProfileName() {
        String profileName = profileHelper.getProfileName(assumeRoleWithSamlResult);

        assertEquals(expectedGeneratedProfileName, profileName);
    }

    @Test
    void getProfileNameWithSpecifiedOktaProfile() {
        environment.oktaProfile = specifiedOktaProfile;

        String profileName = profileHelper.getProfileName(assumeRoleWithSamlResult);

        assertEquals(specifiedOktaProfile, profileName);
    }

    @Test
    void getProfileNameWithBrokenAssumedUserArnUsesTemp() {
        AssumedRoleUser assumedRoleUser = AssumedRoleUser.builder().arn("brokenARN").build();
        AssumeRoleWithSamlResponse newResult = assumeRoleWithSamlResult.toBuilder()
                .assumedRoleUser(assumedRoleUser)
                .build();

        String profileName = profileHelper.getProfileName(newResult);

        assertEquals(tempProfileNameFallback, profileName);
    }

    @Test
    void getProfileNameWithGovCloudAssumedUserArn() {
        AssumedRoleUser assumedRoleUser = AssumedRoleUser.builder().arn(fakeGovCloudCredentialsProfileName).build();
        AssumeRoleWithSamlResponse newResult = assumeRoleWithSamlResult.toBuilder()
                .assumedRoleUser(assumedRoleUser)
                .build();

        String profileName = profileHelper.getProfileName(newResult);

        assertEquals(expectedGeneratedProfileName, profileName);
    }
}
