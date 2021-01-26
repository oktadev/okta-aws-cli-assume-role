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
package com.okta.tools.aws.settings;

import software.amazon.awssdk.regions.Region;

import java.io.IOException;
import java.io.Reader;
import java.time.Instant;
import java.util.Optional;

public class MultipleProfile extends Settings {

    private static final String SOURCE_PROFILE = "source_profile";
    private static final String PROFILE_EXPIRY = "profile_expiry";
    private static final String OKTA_ROLE_ARN = "okta_roleArn";
    private static final String AWS_DEFAULT_REGION = "region";

    /**
     * Create a Profiles object from a given {@link Reader}. The data given by this {@link Reader} should
     * be INI-formatted.
     *
     * @param reader The settings we want to work with. N.B.: The reader is consumed by the constructor.
     * @throws IOException Thrown when we cannot read or load from the given {@param reader}.
     */
    public MultipleProfile(Reader reader) throws IOException {
        super(reader);
    }

    public Optional<com.okta.tools.models.Profile> getProfile(String profile) {
        if (getSections().contains(profile)) {
            Instant expiry = getExpiry(profile);
            String roleArn = getRoleArn(profile);
            return Optional.of(new com.okta.tools.models.Profile(expiry, roleArn));
        }
        return Optional.empty();
    }

    public void deleteProfile(String profile) {
        clearSection(profile);
    }

    private Instant getExpiry(String profile) {
        String profileExpiry = getProperty(profile, PROFILE_EXPIRY);
        return Instant.parse(profileExpiry);
    }

    private String getRoleArn(String profile) {
        return getProperty(profile, OKTA_ROLE_ARN);
    }

    /**
     * Add or update a profile to an Okta Profile file based on {@code name}. This will be linked to a okta profile
     * of the same {@code name}, which should already be present in the profile expiry file.
     *
     * @param name         The name of the profile.
     * @param roleArn      ARN of the role to assume
     * @param awsRegion    Region to use for assumption
     * @param expiry       expiry time of the profile session.
     */
    public void addOrUpdateProfile(String name, String roleArn, Region awsRegion, Instant expiry) {
        setProperty(name, SOURCE_PROFILE, name);
        setProperty(name, OKTA_ROLE_ARN, roleArn);
        setProperty(name, AWS_DEFAULT_REGION, awsRegion.id());
        setProperty(name, PROFILE_EXPIRY, expiry.toString());
    }
}
