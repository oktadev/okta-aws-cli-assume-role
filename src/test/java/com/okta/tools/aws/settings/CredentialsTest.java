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

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class CredentialsTest {

    private String existingCredentials = "[default]\n"
            + Credentials.ACCES_KEY_ID + " = " + "defaultaccesskey" + "\n"
            + Credentials.SECRET_ACCESS_KEY + " = " + "defaultsecretkey" + "\n"
            + Credentials.SESSION_TOKEN + " = " + "defaultsessiontoken";

    private String roleName = "newrole";
    private String accessKey = "accesskey";
    private String secretKey = "secretkey";
    private String sessionToken = "sessiontoken";
    private String manualRole = "[" + roleName + "_source]\n"
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

        String given = org.apache.commons.lang.StringUtils.remove(credentialsWriter.toString().trim(), '\r');

        assertEquals(manualRole, given);
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

        String expected = existingCredentials + "\n\n" + manualRole;
        String given = org.apache.commons.lang.StringUtils.remove(credentialsWriter.toString().trim(), '\r');

        assertEquals(expected, given);
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
                + "[" + roleName + "_source]\n"
                + Credentials.ACCES_KEY_ID + " = " + updatedPrefix + accessKey + "\n"
                + Credentials.SECRET_ACCESS_KEY + " = " + updatedPrefix + secretKey + "\n"
                + Credentials.SESSION_TOKEN + " = " + updatedPrefix + sessionToken;

        credentials.addOrUpdateProfile(roleName, updatedPrefix + accessKey, updatedPrefix + secretKey, updatedPrefix + sessionToken);
        credentials.save(credentialsWriter);

        String given = org.apache.commons.lang.StringUtils.remove(credentialsWriter.toString().trim(), '\r');

        assertEquals(expected, given);
    }

    /*
     * Tests whether the Reader given to the Credentials constructor is properly closed.
     */
    @Test
    public void constructorClosesReader() throws Exception {
        final String simpleIniDocument = "[ini]\nfoo=bar";
        final StringReader reader = new StringReader(simpleIniDocument);

        // This should consume reader
        new Credentials(reader);
        // Causing this to throw an exception
        assertThrows(IOException.class, () -> reader.ready(), "Stream closed");
    }
}
