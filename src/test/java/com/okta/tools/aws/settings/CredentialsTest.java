package com.okta.tools.aws.settings;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

class CredentialsTest {

    final String existingCredentials = "[default]\n"
            + Credentials.ACCES_KEY_ID + " = " + "defaultaccesskey" + "\n"
            + Credentials.SECRET_ACCESS_KEY + " = " + "defaultsecretkey" + "\n"
            + Credentials.SESSION_TOKEN + " = " + "defaultsessiontoken";

    final String roleName = "newrole";
    final String accessKey = "accesskey";
    final String secretKey = "secretkey";
    final String sessionToken = "sessiontoken";
    final String manualRole = "[" + roleName + "]\n"
            + Credentials.ACCES_KEY_ID + " = " + accessKey + "\n"
            + Credentials.SECRET_ACCESS_KEY + " = " + secretKey + "\n"
            + Credentials.SESSION_TOKEN + " = " + sessionToken;

    /*
     * Test instantiating a Credentials object with invalid INI.
     */
    @Test
    void instantiateInvalidCredentials() {
        assertThrows(IOException.class, () -> new Credentials(new StringReader("someinvalidini")));
    }

    /*
     * Test writing a new credentials profile to a blank credentials file.
     */
    @Test
    void addOrUpdateProfileToNewCredentialsFile() throws IOException {
        final StringReader credentialsReader = new StringReader("");
        final StringWriter credentialsWriter = new StringWriter();
        final Credentials credentials = new Credentials(credentialsReader);

        credentials.addOrUpdateProfile(roleName, accessKey, secretKey, sessionToken);
        credentials.save(credentialsWriter);

        assertEquals(manualRole, credentialsWriter.toString().trim());
    }

    /*
     * Test writing a new credentials profile to an existing credentials file.
     */
    @Test
    void addOrUpdateProfileToExistingCredentialsFile() throws IOException {
        final StringReader credentialsReader = new StringReader(existingCredentials);
        final StringWriter credentialsWriter = new StringWriter();
        final Credentials credentials = new Credentials(credentialsReader);

        credentials.addOrUpdateProfile(roleName, accessKey, secretKey, sessionToken);
        credentials.save(credentialsWriter);

        assertEquals(existingCredentials + "\n\n" + manualRole, credentialsWriter.toString().trim());
    }

    /*
     * Test updating an existing profile.
     */
    @Test
    void addOrUpdateProfileToExistingProfile() throws IOException {
        final StringReader credentialsReader = new StringReader(existingCredentials + "\n\n" + manualRole);
        final StringWriter credentialsWriter = new StringWriter();
        final Credentials credentials = new Credentials(credentialsReader);

        final String updatedPrefix = "updated_";
        final String expected = existingCredentials + "\n\n"
                + "[" + roleName + "]\n"
                + Credentials.ACCES_KEY_ID + " = " + updatedPrefix + accessKey + "\n"
                + Credentials.SECRET_ACCESS_KEY + " = " + updatedPrefix + secretKey + "\n"
                + Credentials.SESSION_TOKEN + " = " + updatedPrefix + sessionToken;

        credentials.addOrUpdateProfile(roleName, updatedPrefix + accessKey, updatedPrefix + secretKey, updatedPrefix + sessionToken);
        credentials.save(credentialsWriter);

        assertEquals(expected, credentialsWriter.toString().trim());
    }

}