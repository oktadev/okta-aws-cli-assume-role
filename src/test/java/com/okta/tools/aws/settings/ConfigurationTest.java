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
import org.ini4j.Profile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.okta.tools.aws.settings.Configuration.ROLE_ARN;
import static com.okta.tools.aws.settings.Configuration.SOURCE_PROFILE;
import static com.okta.tools.aws.settings.Settings.DEFAULTPROFILENAME;
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
                    0, null, "custom ", null);

    /*
     * Test instantiating a Credentials object with invalid INI.
     */
    @Test
    void instantiateInvalidConfiguration() {
        assertThrows(IOException.class, () -> new Configuration(new StringReader("someinvalidini")));
    }

    /*
     * Test writing a new profile to a blank configuration file.
     */
    @Test
    void addOrUpdateProfileToNewConfigFile() throws IOException {
        Configuration initiallyEmpty = new Configuration(new StringReader(""));

        // Small function to copy a section to a map so we can easily compare it
        Function<Profile.Section, Map<String, String>> sectionToMap = section ->
                section.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Make sure the INI object is empty.
        assertTrue(initiallyEmpty.settings.isEmpty());

        // Write an initial profile. This should create a default profile as well.
        initiallyEmpty.addOrUpdateProfile(profileName, role_arn, region);
        assertEquals(2, initiallyEmpty.settings.size());
        assertEquals(profileName + "_source", initiallyEmpty.settings.get(DEFAULTPROFILENAME, SOURCE_PROFILE));
        assertEquals(role_arn, initiallyEmpty.settings.get(DEFAULTPROFILENAME, ROLE_ARN));
        // State of the default profile after creating an initial profile.
        final Map<String, String> defaultProfileBefore = sectionToMap.apply(initiallyEmpty.settings.get(DEFAULTPROFILENAME));

        // Write another profile. Make sure the default profile is left alone.
        final String postfix = "_2";
        initiallyEmpty.addOrUpdateProfile(profileName + postfix, role_arn + postfix, region);
        assertTrue(initiallyEmpty.settings.containsKey("profile " + profileName + postfix));
        assertEquals(3, initiallyEmpty.settings.size());
        assertEquals(defaultProfileBefore, sectionToMap.apply(initiallyEmpty.settings.get(DEFAULTPROFILENAME)));
    }

    /*
     * Test writing a new profile to a blank configuration file with custom prefix.
     */
    @Test
    void addOrUpdateProfileToNewConfigFileWithCustomPrefix() throws IOException {
        Configuration initiallyEmpty = new Configuration(new StringReader(""), environmentWithCustomPrefix);

        // Small function to copy a section to a map so we can easily compare it
        Function<Profile.Section, Map<String, String>> sectionToMap = section ->
                section.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Make sure the INI object is empty.
        assertTrue(initiallyEmpty.settings.isEmpty());

        // Write an initial profile. This should create a default profile as well.
        initiallyEmpty.addOrUpdateProfile(profileName, role_arn, region);
        assertEquals(2, initiallyEmpty.settings.size());
        assertEquals(profileName + "_source", initiallyEmpty.settings.get(DEFAULTPROFILENAME, SOURCE_PROFILE));
        assertEquals(role_arn, initiallyEmpty.settings.get(DEFAULTPROFILENAME, ROLE_ARN));
        // State of the default profile after creating an initial profile.
        final Map<String, String> defaultProfileBefore = sectionToMap.apply(initiallyEmpty.settings.get(DEFAULTPROFILENAME));

        // Write another profile. Make sure the default profile is left alone.
        final String postfix = "_2";
        initiallyEmpty.addOrUpdateProfile(profileName + postfix, role_arn + postfix, region);
        assertTrue(initiallyEmpty.settings.containsKey("custom " + profileName + postfix));
        assertEquals(3, initiallyEmpty.settings.size());
        assertEquals(defaultProfileBefore, sectionToMap.apply(initiallyEmpty.settings.get(DEFAULTPROFILENAME)));
    }

    /*
     * Test writing a new default profile to a blank configuration file.
     */
    @Test
    void addOrUpdateDefaultProfileToNewConfigFile() throws IOException {
        Configuration initiallyEmpty = new Configuration(new StringReader(""));

        // Small function to copy a section to a map so we can easily compare it
        Function<Profile.Section, Map<String, String>> sectionToMap = section ->
                section.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Make sure the INI object is empty.
        assertTrue(initiallyEmpty.settings.isEmpty());

        // Write the default profile only.
        initiallyEmpty.addOrUpdateProfile("default", role_arn, region);
        assertEquals(1, initiallyEmpty.settings.size());
        assertEquals("default", initiallyEmpty.settings.get(DEFAULTPROFILENAME, SOURCE_PROFILE));
        assertEquals(role_arn, initiallyEmpty.settings.get(DEFAULTPROFILENAME, ROLE_ARN));
        // State of the default profile after creating an initial profile.
        final Map<String, String> defaultProfileBefore = sectionToMap.apply(initiallyEmpty.settings.get(DEFAULTPROFILENAME));

        // Write another profile. Make sure the default profile is left alone.
        final String postfix = "_2";
        initiallyEmpty.addOrUpdateProfile(profileName + postfix, role_arn + postfix, region);
        assertTrue(initiallyEmpty.settings.containsKey("profile " + profileName + postfix));
        assertEquals(2, initiallyEmpty.settings.size());
        assertEquals(defaultProfileBefore, sectionToMap.apply(initiallyEmpty.settings.get(DEFAULTPROFILENAME)));
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

    /*
     * Tests whether the Reader given to the Configuration constructor is properly closed.
     */
    @Test
    public void constructorClosesReader() throws Exception {
        final String simpleIniDocument = "[ini]\nfoo=bar";
        final StringReader reader = new StringReader(simpleIniDocument);

        // This should consume reader
        new Configuration(reader);
        // Causing this to throw an exception
        assertThrows(IOException.class, () -> reader.ready(), "Stream closed");
    }

    private static int indexOfDifference(String str1, String str2)
    {
        if (str1 == str2) {
            return -1;
        }

        if (str1 == null || str2 == null)
        {
            return 0;
        }

        int i;
        for (i = 0; i < str1.length() && i < str2.length(); ++i)
        {
            if (str1.charAt(i) != str2.charAt(i))
            {
                break;
            }
        }

        if (i < str2.length() || i < str1.length())
        {
            return i;
        }

        return -1;
    }
}
