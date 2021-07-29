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
package com.okta.tools.helpers;

import com.okta.tools.aws.settings.Credentials;
import software.amazon.awssdk.regions.Region;

import java.io.IOException;

public class CredentialsHelper {

    /**
     * Updates the credentials file
     *
     * @param profileName     The profile to use
     * @param awsAccessKey    The access key to use
     * @param awsSecretKey    The secret key to use
     * @param awsRegion       The region to use
     * @param awsSessionToken The session token to use
     * @throws IOException    if a file system or permissions error occurs
     */
    void updateCredentialsFile(String profileName, String awsAccessKey, String awsSecretKey, Region awsRegion, String awsSessionToken)
            throws IOException {
        FileHelper.usingPath(FileHelper.getAwsDirectory().resolve("credentials"), reader -> {
            Credentials credentials = new Credentials(reader);
            credentials.addOrUpdateProfile(profileName, awsAccessKey, awsSecretKey, awsRegion, awsSessionToken);
            return credentials;
        }, Credentials::save);
    }

    /**
     * Remove credentials from profile in credentials file
     *
     * @param profileName     The profile to remove credentials from
     * @throws IOException    if a file system or permissions error occurs
     */
    void removeCredentialsFromProfile(String profileName)
            throws IOException {
        FileHelper.usingPath(FileHelper.getAwsDirectory().resolve("credentials"), reader -> {
            Credentials credentials = new Credentials(reader);
            credentials.removeCredentialsFromProfile(profileName);
            return credentials;
        }, Credentials::save);
    }
}
