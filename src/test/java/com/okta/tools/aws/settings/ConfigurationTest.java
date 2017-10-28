package com.okta.tools.aws.settings;

import org.ini4j.Profile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.okta.tools.aws.settings.Configuration.PROFILE_PREFIX;
import static com.okta.tools.aws.settings.Configuration.ROLE_ARN;
import static com.okta.tools.aws.settings.Configuration.SOURCE_PROFILE;
import static com.okta.tools.aws.settings.Settings.DEFAULTPROFILENAME;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTest {

    private String existingProfile = "[profile default]\n"
            + Configuration.ROLE_ARN + " = " + "arn:aws:iam:12345:role/foo" + "\n"
            + SOURCE_PROFILE + " = " + "default-source_profile" + "\n"
            + Configuration.REGION + " = " + "default-region";

    private String profileName = "new-profilename";
    private String role_arn = "arn:aws:iam:67890:role/bar";
    private String region = "us-east-1";
    private String manualRole = "[profile " + profileName + "]\n"
            + Configuration.ROLE_ARN + " = " + role_arn + "\n"
            + SOURCE_PROFILE + " = " + profileName + "\n"
            + Configuration.REGION + " = " + region;

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
        initiallyEmpty.addOrUpdateProfile(profileName, role_arn);
        assertEquals(2, initiallyEmpty.settings.size());
        assertEquals(profileName, initiallyEmpty.settings.get(DEFAULTPROFILENAME, SOURCE_PROFILE));
        assertEquals(role_arn, initiallyEmpty.settings.get(DEFAULTPROFILENAME, ROLE_ARN));
        // State of the default profile after creating an initial profile.
        final Map<String, String> defaultProfileBefore = sectionToMap.apply(initiallyEmpty.settings.get(DEFAULTPROFILENAME));

        // Write another profile. Make sure the default profile is left alone.
        final String postfix = "_2";
        initiallyEmpty.addOrUpdateProfile(profileName + postfix, role_arn + postfix);
        assertTrue(initiallyEmpty.settings.containsKey(PROFILE_PREFIX + profileName + postfix));
        assertEquals(3, initiallyEmpty.settings.size());
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

        configuration.addOrUpdateProfile(profileName, role_arn);
        configuration.save(configurationWriter);

        assertEquals(existingProfile + "\n\n" + manualRole, configurationWriter.toString().trim());
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
                + SOURCE_PROFILE + " = " + profileName + "\n"
                + Configuration.REGION + " = " + region
                + "\n\n" + existingProfile;


        configuration.addOrUpdateProfile(profileName, updatedPrefix + role_arn);
        configuration.save(configurationWriter);

        assertEquals(expected, configurationWriter.toString().trim());
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
}