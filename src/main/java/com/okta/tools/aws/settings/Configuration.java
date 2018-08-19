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
package com.okta.tools.aws.settings;

import com.okta.tools.OktaAwsCliEnvironment;

import java.io.IOException;
import java.io.Reader;

public class Configuration extends Settings {

    static final String ROLE_ARN = "role_arn";
    static final String SOURCE_PROFILE = "source_profile";
    static final String REGION = "region";
    private final OktaAwsCliEnvironment environment;

    /**
     * Create a Configuration object from a given {@link Reader}. The data given by this {@link Reader} should
     * be INI-formatted.
     *
     * @param reader The settings we want to work with. N.B.: The reader is consumed by the constructor.
     * @throws IOException Thrown when we cannot read or load from the given {@code reader}.
     */
    public Configuration(Reader reader, OktaAwsCliEnvironment environment) throws IOException {
        super(reader);
        this.environment = environment;
    }

    Configuration(Reader reader) throws IOException {
        this(reader, new OktaAwsCliEnvironment());
    }

    /**
     * Add or update a profile to an AWS config file based on {@code name}. This will be linked to a credential profile
     * of the same {@code name}, which should already be present in the credentials file.
     * The region for this new profile will be {@link OktaAwsCliEnvironment#awsRegion}.
     *
     * @param name         The name of the profile.
     * @param roleToAssume The ARN of the role to assume in this profile.
     * @param region       The AWS to use if not already present in the profile.
     */
    public void addOrUpdateProfile(String name, String roleToAssume, String region) {
        // profileName is the string used for the section in the AWS config file.
        // This should be prefixed with "profile ".
        String profileName = DEFAULT_PROFILE_NAME.equals(name) ? DEFAULT_PROFILE_NAME : environment.profilePrefix + name;

        // Determine whether this is a new AWS configuration file. If it is, we'll set the default
        // profile to this profile.
        if (isEmpty()) {
            writeConfigurationProfile(DEFAULT_PROFILE_NAME, name, roleToAssume, region);
        }

        // Write the new profile data
        writeConfigurationProfile(profileName, name, roleToAssume, region);
    }

    private void writeConfigurationProfile(String profile, String name, String roleToAssume, String region) {
        setProperty(profile, ROLE_ARN, roleToAssume);
        setProperty(profile, SOURCE_PROFILE, DEFAULT_PROFILE_NAME.equals(name) ? DEFAULT_PROFILE_NAME : name + environment.credentialsSuffix);
        if (!containsProperty(profile, REGION)) {
            setProperty(profile, REGION, region);
        }
    }
}
