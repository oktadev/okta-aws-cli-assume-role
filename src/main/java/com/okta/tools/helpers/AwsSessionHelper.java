package com.okta.tools.helpers;

import com.okta.tools.models.Session;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

public final class AwsSessionHelper
{
    private static final String OKTA_AWS_CLI_EXPIRY_PROPERTY = "OKTA_AWS_CLI_EXPIRY";
    private static final String OKTA_AWS_CLI_PROFILE_PROPERTY = "OKTA_AWS_CLI_PROFILE";

    /**
     * Get the current session file's Path
     * @author Andrei Hava
     * @since 02/14/2018
     * @return The Path of the current session file
     */
    private static Path getSessionPath()
    {
        return AwsFileHelper.getFilePath(AwsFileHelper.getAwsDirectory().toString(), ".current-session");
    }

    /**
     * Gets the current session, if it exists
     * @return The current session
     * @throws IOException
     */
    public static Optional<Session> getCurrentSession() throws IOException
    {
        if (Files.exists(getSessionPath())) {
            Properties properties = new Properties();
            properties.load(new FileReader(getSessionPath().toString()));
            String expiry = properties.getProperty(OKTA_AWS_CLI_EXPIRY_PROPERTY);
            String profileName = properties.getProperty(OKTA_AWS_CLI_PROFILE_PROPERTY);
            Instant expiryInstant = Instant.parse(expiry);
            return Optional.of(new Session(profileName, expiryInstant));
        }
        return Optional.empty();
    }

    /**
     * Deletes the current session, if it exists
     * @throws IOException
     */
    public static void logoutCurrentSession() throws IOException
    {
        if (Files.exists(getSessionPath()))
        {
            Files.delete(getSessionPath());
        }
    }

    public static void updateCurrentSession(Instant expiryInstant, String profileName) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(OKTA_AWS_CLI_PROFILE_PROPERTY, profileName);
        properties.setProperty(OKTA_AWS_CLI_EXPIRY_PROPERTY, expiryInstant.toString());
        properties.store(new FileWriter(getSessionPath().toString()), "Saved at: " + Instant.now().toString());
    }

    public static boolean sessionIsActive(Instant startInstant, Session session) {
        return startInstant.isBefore(session.expiry);
    }
}
