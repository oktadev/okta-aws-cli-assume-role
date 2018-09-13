package com.okta.tools.helpers;

import com.okta.tools.OktaAwsCliEnvironment;
import com.okta.tools.aws.settings.MultipleProfile;
import com.okta.tools.models.Profile;
import com.okta.tools.models.Session;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

public final class SessionHelper {

    private static final String OKTA_AWS_CLI_EXPIRY_PROPERTY = "OKTA_AWS_CLI_EXPIRY";
    private static final String OKTA_AWS_CLI_PROFILE_PROPERTY = "OKTA_AWS_CLI_PROFILE";

    private final OktaAwsCliEnvironment environment;
    private final CookieHelper cookieHelper;

    public SessionHelper(OktaAwsCliEnvironment environment, CookieHelper cookieHelper) {
        this.environment = environment;
        this.cookieHelper = cookieHelper;
    }

    /**
     * Gets the current session, if it exists
     *
     * @return The current {@link Session}
     * @throws IOException if file system or permissions errors are encountered
     */
    public Optional<Session> getCurrentSession() throws IOException {
        if (Files.exists(getSessionPath())) {
            try (FileReader fileReader = new FileReader(getSessionPath().toFile())) {
                Properties properties = new Properties();
                properties.load(fileReader);
                String expiry = properties.getProperty(OKTA_AWS_CLI_EXPIRY_PROPERTY);
                String profileName = properties.getProperty(OKTA_AWS_CLI_PROFILE_PROPERTY);
                Instant expiryInstant = Instant.parse(expiry);

                return Optional.of(new Session(profileName, expiryInstant));
            }
        }
        return Optional.empty();
    }

    /**
     * Gets the current session file's path
     *
     * @return A {@link Path} for the current session file
     * @throws IOException if file system or permissions errors are encountered
     */
    private Path getSessionPath() throws IOException {
        return FileHelper.getOktaDirectory().resolve(".current-session");
    }

    /**
     * Deletes the current session, if it exists
     *
     * @throws IOException if file system or permissions errors are encountered
     */
    public void logoutCurrentSession() throws IOException {
        if (StringUtils.isNotBlank(environment.oktaProfile)) {
            logoutMultipleAccounts(environment.oktaProfile);
        }
        if (Files.exists(getSessionPath())) {
            Files.delete(getSessionPath());
        }
    }

    private void logoutMultipleAccounts(String profileName) throws IOException {
        cookieHelper.clearCookies();
        FileHelper.usingPath(FileHelper.getOktaDirectory().resolve("profiles"), reader -> {
            MultipleProfile multipleProfile = new MultipleProfile(reader);
            multipleProfile.deleteProfile(profileName);
            return multipleProfile;
        }, MultipleProfile::save);
    }

    public void updateCurrentSession(Instant expiryInstant, String profileName) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(OKTA_AWS_CLI_PROFILE_PROPERTY, profileName);
        properties.setProperty(OKTA_AWS_CLI_EXPIRY_PROPERTY, expiryInstant.toString());
        properties.store(new FileWriter(getSessionPath().toString()), "Saved at: " + Instant.now().toString());
    }

    public Optional<Profile> getFromMultipleProfiles() throws IOException {
        return FileHelper.readingPath(FileHelper.getOktaDirectory().resolve("profiles"), reader ->
            new MultipleProfile(reader).getProfile(environment.oktaProfile)
        );
    }

    public void addOrUpdateProfile(Instant start) throws IOException {
        FileHelper.usingPath(FileHelper.getOktaDirectory().resolve("profiles"), reader -> {
            MultipleProfile multipleProfile = new MultipleProfile(reader);
            multipleProfile.addOrUpdateProfile(environment.oktaProfile, environment.awsRoleToAssume, start);
            return multipleProfile;
        }, MultipleProfile::save);
    }

    public boolean sessionIsActive(Instant startInstant, Session session) {
        return startInstant.isBefore(session.expiry);
    }
}
