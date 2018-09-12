package com.okta.tools.helpers;

import com.okta.tools.OktaAwsCliEnvironment;
import com.okta.tools.aws.settings.Credentials;

import java.io.IOException;

public final class CredentialsHelper {

    private final OktaAwsCliEnvironment environment;

    public CredentialsHelper(OktaAwsCliEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Updates the credentials file
     *
     * @param profileName     The profile to use
     * @param awsAccessKey    The access key to use
     * @param awsSecretKey    The secret key to use
     * @param awsSessionToken The session token to use
     * @throws IOException    if a file system or permissions error occurs
     */
    void updateCredentialsFile(String profileName, String awsAccessKey, String awsSecretKey, String awsSessionToken)
            throws IOException {
        FileHelper.usingPath(FileHelper.getAwsDirectory().resolve("credentials"), reader -> {
            Credentials credentials = new Credentials(reader, environment);
            credentials.addOrUpdateProfile(profileName, awsAccessKey, awsSecretKey, awsSessionToken);
            return credentials;
        }, Credentials::save);
    }
}
