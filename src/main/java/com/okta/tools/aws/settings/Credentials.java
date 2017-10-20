package com.okta.tools.aws.settings;

import org.ini4j.Profile;

import java.io.IOException;
import java.io.Reader;

/**
 * This class abstracts writing changes to AWS credentials files.
 * It's use case right now is very narrow: writing new credentials profiles consisting of
 * an acces key, a secret key and a session token.
 */
public class Credentials extends Settings {

    // Keys used in aws credentials files
    static final String ACCES_KEY_ID = "aws_access_key_id";
    static final String SECRET_ACCESS_KEY = "aws_secret_access_key";
    static final String SESSION_TOKEN = "aws_session_token";

    /**
     * Create a Credentials object from a given {@link Reader}. The data given by this {@link Reader} should
     * be INI-formatted.
     *
     * @param reader The settings we want to work with. N.B.: The reader is consumed by the constructor.
     * @throws IOException Thrown when we cannot read or load from the given {@param reader}.
     */
    public Credentials(Reader reader) throws IOException {
        super(reader);
    }

    /**
     * Add or update a profile to an AWS credentials file based on {@code name}.
     * @param name The name of the profile.
     * @param awsAccessKey The access key to use for the profile.
     * @param awsSecretKey The secret key to use for the profile.
     * @param awsSessionToken The session token to use for the profile.
     */
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
