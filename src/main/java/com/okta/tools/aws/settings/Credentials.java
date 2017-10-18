package com.okta.tools.aws.settings;

import org.ini4j.Profile;

import java.io.IOException;
import java.io.Reader;

public class Credentials extends Settings {

    // Keys used in aws credentials files
    public static final String ACCES_KEY_ID = "aws_access_key_id";
    public static final String SECRET_ACCESS_KEY = "aws_secret_access_key";
    public static final String SESSION_TOKEN = "aws_session_token";

    public Credentials(Reader reader) throws IOException {
        super(reader);
    }

    public void addOrUpdateProfile(String name, String awsAccessKey, String awsSecretKey, String awsSessionToken) {
        final Profile.Section awsProfile = settings.get(name) != null ? settings.get(name) : settings.add(name);
        writeCredentialsProfile(awsProfile, awsAccessKey, awsSecretKey, awsSessionToken);
    }

    /**
     * Create a new profile in this credentials object.
     * @param awsProfile A reference to the profile in the credentials.
     * @param awsAccessKey The AWS access key to use in the profile.
     * @param awsSecretKey The AWS secret access key to use in the profile.
     * @param awsSessionToken The AWS session token to use in the profile.
     */
    private void writeCredentialsProfile(Profile.Section awsProfile, String awsAccessKey, String awsSecretKey, String awsSessionToken) {
        awsProfile.put(ACCES_KEY_ID, awsAccessKey);
        awsProfile.put(SECRET_ACCESS_KEY, awsSecretKey);
        awsProfile.put(SESSION_TOKEN, awsSessionToken);
    }
}
