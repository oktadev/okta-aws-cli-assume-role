package com.okta.tools.helpers;

import com.okta.tools.aws.settings.Credentials;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;

public final class CredentialsHelper {

    /**
     * Gets a reader for the credentials file. If the file doesn't exist, it creates it
     *
     * @return A {@link Reader} for the credentials file
     * @throws IOException
     */
    public static Reader getCredsReader() throws IOException {
        return FileHelper.getReader(FileHelper.getAwsDirectory(), "credentials");
    }

    /**
     * Gets a FileWriter for the credentials file
     *
     * @return A {@link FileWriter} for the credentials file
     * @throws IOException
     */
    public static FileWriter getCredsWriter() throws IOException {
        return FileHelper.getWriter(FileHelper.getAwsDirectory(), "credentials");
    }

    /**
     * Updates the credentials file
     *
     * @param profileName     The profile to use
     * @param awsAccessKey    The access key to use
     * @param awsSecretKey    The secret key to use
     * @param awsSessionToken The session token to use
     * @throws IOException
     */
    public static void updateCredentialsFile(String profileName, String awsAccessKey, String awsSecretKey, String awsSessionToken)
            throws IOException {
        try (Reader reader = CredentialsHelper.getCredsReader()) {
            // Create the credentials object with the data read from the credentials file
            Credentials credentials = new Credentials(reader);

            // Write the given profile data
            credentials.addOrUpdateProfile(profileName, awsAccessKey, awsSecretKey, awsSessionToken);

            // Write the updated profile
            try (FileWriter fileWriter = CredentialsHelper.getCredsWriter()) {
                credentials.save(fileWriter);
            }
        }
    }
}
