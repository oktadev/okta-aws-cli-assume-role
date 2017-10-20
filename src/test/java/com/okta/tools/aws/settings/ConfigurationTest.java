package com.okta.tools.aws.settings;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigurationTest {

    private String existingProfile = "[profile default]\n"
            + Configuration.ROLE_ARN + " = " + "arn:aws:iam:12345:role/foo" + "\n"
            + Configuration.SOURCE_PROFILE + " = " + "default-source_profile" + "\n"
            + Configuration.REGION + " = " + "default-region";

    private String profileName = "new-profilename";
    private String role_arn = "arn:aws:iam:67890:role/bar";
    private String region = "us-east-1";
    private String manualRole = "[profile " + profileName + "]\n"
            + Configuration.ROLE_ARN + " = " + role_arn + "\n"
            + Configuration.SOURCE_PROFILE + " = " + profileName + "\n"
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
        final StringReader configurationReader = new StringReader("");
        final StringWriter configurationWriter = new StringWriter();
        final Configuration configuration = new Configuration(configurationReader);

        configuration.addOrUpdateProfile(profileName, role_arn);
        configuration.save(configurationWriter);

        assertEquals(manualRole, configurationWriter.toString().trim());
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
                + Configuration.SOURCE_PROFILE + " = " + profileName + "\n"
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