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

import org.ini4j.Profile;

import java.io.IOException;
import java.io.Reader;

public class Configuration extends Settings {

    static final String ROLE_ARN = "role_arn";
    static final String SOURCE_PROFILE = "source_profile";
    static final String REGION = "region";
    static final String PROFILE_PREFIX = "profile ";
    private static final String REGION_DEFAULT = "us-east-1";

    /**
     * Create a Configuration object from a given {@link Reader}. The data given by this {@link Reader} should
     * be INI-formatted.
     *
     * @param reader The settings we want to work with. N.B.: The reader is consumed by the constructor.
     * @throws IOException Thrown when we cannot read or load from the given {@code reader}.
     */
    public Configuration(Reader reader) throws IOException {
        super(reader);
    }

    /**
     * Add or update a profile to an AWS config file based on {@code name}. This will be linked to a credential profile
     * of the same {@code name}, which should already be present in the credentials file.
     * The region for this new profile will be {@link Configuration#REGION_DEFAULT}.
     *
     * @param name         The name of the profile.
     * @param roleToAssume The ARN of the role to assume in this profile.
     */
    public void addOrUpdateProfile(String name, String roleToAssume) {
        // profileName is the string used for the section in the AWS config file.
        // This should be prefixed with "profile ".
        final String profileName = PROFILE_PREFIX + name;

        // Determine whether this is a new AWS configuration file. If it is, we'll set the default
        // profile to this profile.
        final boolean newConfig = settings.isEmpty();
        if (newConfig) {
            writeConfigurationProfile(settings.add(DEFAULTPROFILENAME), name, roleToAssume);
        }

        // Write the new profile data
        final Profile.Section awsProfile = settings.get(profileName) != null
                ? settings.get(profileName)
                : settings.add(profileName);
        writeConfigurationProfile(awsProfile, name, roleToAssume);
    }

    private void writeConfigurationProfile(Profile.Section awsProfile, String name, String roleToAssume) {
        awsProfile.put(ROLE_ARN, roleToAssume);
        awsProfile.put(SOURCE_PROFILE, name + "_source");
        awsProfile.put(REGION, REGION_DEFAULT);
    }
}
