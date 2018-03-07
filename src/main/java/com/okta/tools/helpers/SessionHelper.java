package com.okta.tools.helpers;

import com.okta.tools.OktaAwsCliEnvironment;
import com.okta.tools.aws.settings.MultipleProfile;
import com.okta.tools.models.Profile;
import com.okta.tools.models.Session;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

public final class SessionHelper {
    private static final String OKTA_AWS_CLI_EXPIRY_PROPERTY = "OKTA_AWS_CLI_EXPIRY";
    private static final String OKTA_AWS_CLI_PROFILE_PROPERTY = "OKTA_AWS_CLI_PROFILE";

    /**
     * Get the current session file's Path
     *
     * @return The Path of the current session file
     * @author Andrei Hava
     * @since 02/14/2018
     */
    private static Path getSessionPath() throws IOException {
        return FileHelper.getFilePath(FileHelper.getOktaDirectory().toString(), "/.current-session");
    }

    /**
     * Gets the current session, if it exists
     *
     * @return The current session
     * @throws IOException
     */
    public static Optional<Session> getCurrentSession() throws IOException {
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
     *
     * @throws IOException
     */
    public static void logoutCurrentSession(String oktaProfile) throws IOException {
        if (oktaProfile != null) {
            logoutMultipleAccounts(oktaProfile);
        }
        if (Files.exists(getSessionPath())) {
            Files.delete(getSessionPath());
        }
    }

    public static void updateCurrentSession(Instant expiryInstant, String profileName) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(OKTA_AWS_CLI_PROFILE_PROPERTY, profileName);
        properties.setProperty(OKTA_AWS_CLI_EXPIRY_PROPERTY, expiryInstant.toString());
        properties.store(new FileWriter(getSessionPath().toString()), "Saved at: " + Instant.now().toString());
    }

    public static Optional<Profile> getFromMultipleProfiles() throws IOException {
        return getMultipleProfile().getProfile(OktaAwsCliEnvironment.oktaProfile, getMultipleProfilesPath());
    }

    public static void addOrUpdateProfile(String profileName, String oktaSession, Instant start) throws IOException {
        MultipleProfile multipleProfile = getMultipleProfile();
        multipleProfile.addOrUpdateProfile(profileName, oktaSession, start);

        try (final FileWriter fileWriter = new FileWriter(getMultipleProfilesPath().toFile())) {
            multipleProfile.save(fileWriter);
        }
    }

    public static boolean sessionIsActive(Instant startInstant, Session session) {
        return startInstant.isBefore(session.expiry);
    }

    private static void logoutMultipleAccounts(String profileName) throws IOException {
        File cookieStore = CookieHelper.getCookies().toFile();
        cookieStore.deleteOnExit();

        getMultipleProfile().deleteProfile(getMultipleProfilesPath().toString(), profileName);
    }

    private static Path getMultipleProfilesPath() throws IOException {
        Path oktaDir = FileHelper.getOktaDirectory();
        Path profileIni = oktaDir.resolve("profiles");

        if (!Files.exists(profileIni)) {
            Files.createFile(profileIni);
        }

        return profileIni;
    }

    private static MultipleProfile getMultipleProfile() throws IOException {
        Path multiProfile = getMultipleProfilesPath();
        Reader reader = FileHelper.getReader(multiProfile.toString());

        return new MultipleProfile(reader);
    }
}
