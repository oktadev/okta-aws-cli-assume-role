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
import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import static com.okta.tools.aws.settings.Configuration.ROLE_ARN;
import static com.okta.tools.aws.settings.Configuration.SOURCE_PROFILE;
import static com.okta.tools.aws.settings.Settings.DEFAULT_PROFILE_NAME;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTest {

    private String existingProfile = "[profile existing]\n"
            + Configuration.ROLE_ARN + " = " + "arn:aws:iam:12345:role/foo" + "\n"
            + SOURCE_PROFILE + " = " + "default-source_profile" + "\n"
            + Configuration.REGION + " = " + "default-region";

    private String profileName = "new-profilename";
    private String role_arn = "arn:aws:iam:67890:role/bar";
    private String region = "us-east-1";
    private String manualRole = "[profile " + profileName + "]\n"
            + Configuration.ROLE_ARN + " = " + role_arn + "\n"
            + SOURCE_PROFILE + " = " + profileName + "_source\n"
            + Configuration.REGION + " = " + region;
    private String manualRoleCustomPrefix = "[custom " + profileName + "]\n"
            + Configuration.ROLE_ARN + " = " + role_arn + "\n"
            + SOURCE_PROFILE + " = " + profileName + "_source\n"
            + Configuration.REGION + " = " + region;
    private String defaultRole = "[default]\n"
            + Configuration.ROLE_ARN + " = " + role_arn + "\n"
            + SOURCE_PROFILE + " = " + "default\n"
            + Configuration.REGION + " = " + region;
    private OktaAwsCliEnvironment environmentWithCustomPrefix =
            new OktaAwsCliEnvironment(false, null, null,
                    null, null, null, null, null,
                    0, null, "custom ", false, null);

    /*
     * Test writing a new profile to a blank configuration file.
     */
    @Test
    void addOrUpdateProfileToNewConfigFile() throws IOException {
        Configuration initiallyEmpty = new Configuration(new StringReader(""));

        // Make sure the INI object is empty.
        assertTrue(initiallyEmpty.isEmpty());

        // Write an initial profile. This should create a default profile as well.
        initiallyEmpty.addOrUpdateProfile(profileName, role_arn, region);
        assertEquals(2, initiallyEmpty.getSections().size());
        assertEquals(profileName + "_source", initiallyEmpty.getProperty(DEFAULT_PROFILE_NAME, SOURCE_PROFILE));
        assertEquals(role_arn, initiallyEmpty.getProperty(DEFAULT_PROFILE_NAME, ROLE_ARN));
        // State of the default profile after creating an initial profile.
        final Map<String, Object> defaultProfileBefore = initiallyEmpty.sectionToMap(DEFAULT_PROFILE_NAME);

        // Write another profile. Make sure the default profile is left alone.
        final String postfix = "_2";
        initiallyEmpty.addOrUpdateProfile(profileName + postfix, role_arn + postfix, region);
        assertTrue(initiallyEmpty.getSections().contains("profile " + profileName + postfix));
        assertEquals(3, initiallyEmpty.getSections().size());
        assertEquals(defaultProfileBefore, initiallyEmpty.sectionToMap(DEFAULT_PROFILE_NAME));
    }

    /*
     * Test writing a new profile to a blank configuration file with custom prefix.
     */
    @Test
    void addOrUpdateProfileToNewConfigFileWithCustomPrefix() throws IOException {
        Configuration initiallyEmpty = new Configuration(new StringReader(""), environmentWithCustomPrefix);

        // Make sure the INI object is empty.
        assertTrue(initiallyEmpty.isEmpty());

        // Write an initial profile. This should create a default profile as well.
        initiallyEmpty.addOrUpdateProfile(profileName, role_arn, region);
        assertEquals(2, initiallyEmpty.getSections().size());
        assertEquals(profileName + "_source", initiallyEmpty.getProperty(DEFAULT_PROFILE_NAME, SOURCE_PROFILE));
        assertEquals(role_arn, initiallyEmpty.getProperty(DEFAULT_PROFILE_NAME, ROLE_ARN));
        // State of the default profile after creating an initial profile.
        final Map<String, Object> defaultProfileBefore = initiallyEmpty.sectionToMap(DEFAULT_PROFILE_NAME);

        // Write another profile. Make sure the default profile is left alone.
        final String postfix = "_2";
        initiallyEmpty.addOrUpdateProfile(profileName + postfix, role_arn + postfix, region);
        assertTrue(initiallyEmpty.getSections().contains("custom " + profileName + postfix));
        assertEquals(3, initiallyEmpty.getSections().size());
        assertEquals(defaultProfileBefore, initiallyEmpty.sectionToMap(DEFAULT_PROFILE_NAME));
    }

    /*
     * Test updating a profile with nested configuration (Issue #141).
     */
    @Test
    void addOrUpdateProfileToExistingProfileWithNestedConfiguration() throws IOException {
        final String existingCombined = manualRole + "\n"
                + "s3 =\n"
                + "    max_queue_size = 1000" + "\n\n" + existingProfile;

        final StringReader configurationReader = new StringReader(existingCombined);
        final StringWriter configurationWriter = new StringWriter();
        final Configuration configuration = new Configuration(configurationReader);

        final String updatedPrefix = "updated_";
        final String expected = "[profile " + profileName + "]\n"
                + Configuration.ROLE_ARN + " = " + updatedPrefix + role_arn + "\n"
                + SOURCE_PROFILE + " = " + profileName + "_source\n"
                + Configuration.REGION + " = " + region + "\n"
                + "s3 = \n" // additional space here is a tolerable divergence (AWS CLI doesn't care)
                + "    max_queue_size = 1000"
                + "\n\n" + existingProfile;


        configuration.addOrUpdateProfile(profileName, updatedPrefix + role_arn, region);
        configuration.save(configurationWriter);

        String given = StringUtils.remove(configurationWriter.toString().trim(), '\r');

        assertEquals(expected, given);
    }

    /*
     * Test writing a new default profile to a blank configuration file.
     */
    @Test
    void addOrUpdateDefaultProfileToNewConfigFile() throws IOException {
        Configuration initiallyEmpty = new Configuration(new StringReader(""));

        // Make sure the INI object is empty.
        assertTrue(initiallyEmpty.isEmpty());

        // Write the default profile only.
        initiallyEmpty.addOrUpdateProfile("default", role_arn, region);
        assertEquals(1, initiallyEmpty.getSections().size());
        assertEquals("default", initiallyEmpty.getProperty(DEFAULT_PROFILE_NAME, SOURCE_PROFILE));
        assertEquals(role_arn, initiallyEmpty.getProperty(DEFAULT_PROFILE_NAME, ROLE_ARN));
        // State of the default profile after creating an initial profile.
        final Map<String, Object> defaultProfileBefore = initiallyEmpty.sectionToMap(DEFAULT_PROFILE_NAME);

        // Write another profile. Make sure the default profile is left alone.
        final String postfix = "_2";
        initiallyEmpty.addOrUpdateProfile(profileName + postfix, role_arn + postfix, region);
        assertTrue(initiallyEmpty.getSections().contains("profile " + profileName + postfix));
        assertEquals(2, initiallyEmpty.getSections().size());
        assertEquals(defaultProfileBefore, initiallyEmpty.sectionToMap(DEFAULT_PROFILE_NAME));
    }

    /*
     * Test writing a new profile to an existing configuration file.
     */
    @Test
    void addOrUpdateProfileToExistingConfigFile() throws IOException {
        final StringReader configurationReader = new StringReader(existingProfile);
        final StringWriter configurationWriter = new StringWriter();
        final Configuration configuration = new Configuration(configurationReader);

        configuration.addOrUpdateProfile(profileName, role_arn, region);
        configuration.save(configurationWriter);

        String expected = existingProfile + "\n\n" + manualRole;
        String given = StringUtils.remove(configurationWriter.toString().trim(), '\r');

        assertEquals(expected, given);
    }

    /*
     * Test writing a new profile to an existing configuration file with custom prefix.
     */
    @Test
    void addOrUpdateProfileToExistingConfigFileCustomPrexi() throws IOException {
        final StringReader configurationReader = new StringReader(existingProfile);
        final StringWriter configurationWriter = new StringWriter();
        final Configuration configuration = new Configuration(configurationReader, environmentWithCustomPrefix);

        configuration.addOrUpdateProfile(profileName, role_arn, region);
        configuration.save(configurationWriter);

        String expected = existingProfile + "\n\n" + manualRoleCustomPrefix;
        String given = StringUtils.remove(configurationWriter.toString().trim(), '\r');

        assertEquals(expected, given);
    }

    /*
     * Test writing a new default profile to an existing configuration file.
     */
    @Test
    void addOrUpdateDefaultProfileToExistingConfigFile() throws IOException {
        final StringReader configurationReader = new StringReader(existingProfile);
        final StringWriter configurationWriter = new StringWriter();
        final Configuration configuration = new Configuration(configurationReader);

        configuration.addOrUpdateProfile("default", role_arn, region);
        configuration.save(configurationWriter);

        String expected = existingProfile + "\n\n" + defaultRole;
        String given = StringUtils.remove(configurationWriter.toString().trim(), '\r');

        assertEquals(expected, given);
    }

    /*
     *  Test updating an existing profile.
     */
    @Test
    void addOrUpdateProfileToExistingProfile() throws IOException {
        final StringReader configurationReader = new StringReader(manualRole + "\n\n" + existingProfile);
        final StringWriter configurationWriter = new StringWriter();
        final Configuration configuration = new Configuration(configurationReader);

        final String updatedPrefix = "updated_";
        final String expected = "[profile " + profileName + "]\n"
                + Configuration.ROLE_ARN + " = " + updatedPrefix + role_arn + "\n"
                + SOURCE_PROFILE + " = " + profileName + "_source\n"
                + Configuration.REGION + " = " + region
                + "\n\n" + existingProfile;


        configuration.addOrUpdateProfile(profileName, updatedPrefix + role_arn, region);
        configuration.save(configurationWriter);

        String given = StringUtils.remove(configurationWriter.toString().trim(), '\r');

        assertEquals(expected, given);
    }
}
