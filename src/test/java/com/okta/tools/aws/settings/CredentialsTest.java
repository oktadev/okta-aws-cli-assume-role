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

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CredentialsTest {

    private String existingCredentials = "[default]\n"
            + Credentials.ACCESS_KEY_ID + " = " + "defaultaccesskey" + "\n"
            + Credentials.SECRET_ACCESS_KEY + " = " + "defaultsecretkey" + "\n"
            + Credentials.AWS_DEFAULT_REGION + " = " + "defaultregion" + "\n"
            + Credentials.SESSION_TOKEN + " = " + "defaultsessiontoken";

    private String roleName = "newrole";
    private String accessKey = "accesskey";
    private String secretKey = "secretkey";
    private Region awsRegion = Region.of("region");
    private String sessionToken = "sessiontoken";
    private String manualRole = "[" + roleName + "]\n"
            + Credentials.ACCESS_KEY_ID + " = " + accessKey + "\n"
            + Credentials.SECRET_ACCESS_KEY + " = " + secretKey + "\n"
            + Credentials.AWS_DEFAULT_REGION + " = " + awsRegion + "\n"
            + Credentials.SESSION_TOKEN + " = " + sessionToken;

    /*
     * Test writing a new credentials profile to a blank credentials file.
     */
    @Test
    void addOrUpdateProfileToNewCredentialsFile() throws IOException {
        final StringReader credentialsReader = new StringReader("");
        final StringWriter credentialsWriter = new StringWriter();
        final Credentials credentials = new Credentials(credentialsReader);

        credentials.addOrUpdateProfile(roleName, accessKey, secretKey, awsRegion, sessionToken);
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

        credentials.addOrUpdateProfile(roleName, accessKey, secretKey, awsRegion, sessionToken);
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
                + "[" + roleName + "]\n"
                + Credentials.ACCESS_KEY_ID + " = " + updatedPrefix + accessKey + "\n"
                + Credentials.SECRET_ACCESS_KEY + " = " + updatedPrefix + secretKey + "\n"
                + Credentials.AWS_DEFAULT_REGION + " = " + awsRegion + "\n"
                + Credentials.SESSION_TOKEN + " = " + updatedPrefix + sessionToken;

        credentials.addOrUpdateProfile(roleName, updatedPrefix + accessKey, updatedPrefix + secretKey, awsRegion, updatedPrefix + sessionToken);
        credentials.save(credentialsWriter);

        String given = org.apache.commons.lang.StringUtils.remove(credentialsWriter.toString().trim(), '\r');

        assertEquals(expected, given);
    }

    /*
     * Test updating default profile.
     */
    @Test
    void addOrUpdateDefaultProfileToExistingProfile() throws IOException {
        final StringReader credentialsReader = new StringReader(existingCredentials + "\n\n" + manualRole);
        final StringWriter credentialsWriter = new StringWriter();
        final Credentials credentials = new Credentials(credentialsReader);

        final String updatedPrefix = "updated_";
        final String expected =
                "[default]\n"
                + Credentials.ACCESS_KEY_ID + " = " + updatedPrefix + accessKey + "\n"
                + Credentials.SECRET_ACCESS_KEY + " = " + updatedPrefix + secretKey + "\n"
                + Credentials.AWS_DEFAULT_REGION + " = " + awsRegion + "\n"
                + Credentials.SESSION_TOKEN + " = " + updatedPrefix + sessionToken + "\n\n"
                + manualRole;

        credentials.addOrUpdateProfile("default", updatedPrefix + accessKey, updatedPrefix + secretKey, awsRegion, updatedPrefix + sessionToken);
        credentials.save(credentialsWriter);

        String given = org.apache.commons.lang.StringUtils.remove(credentialsWriter.toString().trim(), '\r');

        assertEquals(expected, given);
    }
}
