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

    private OktaAwsCliEnvironment environment;

    public SessionHelper(OktaAwsCliEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Gets the current session file's path
     *
     * @return A {@link Path} for the current session file
     * @throws IOException
     */
    private Path getSessionPath() throws IOException {
        return FileHelper.resolveFilePath(FileHelper.getOktaDirectory(), ".current-session");
    }

    /**
     * Gets the current session, if it exists
     *
     * @return The current {@link Session}
     * @throws IOException
     */
    public Optional<Session> getCurrentSession() throws IOException {
        if (Files.exists(getSessionPath())) {
            FileReader fileReader = new FileReader(getSessionPath().toString());

            Properties properties = new Properties();
            properties.load(fileReader);
            String expiry = properties.getProperty(OKTA_AWS_CLI_EXPIRY_PROPERTY);
            String profileName = properties.getProperty(OKTA_AWS_CLI_PROFILE_PROPERTY);
            Instant expiryInstant = Instant.parse(expiry);

            fileReader.close();

            return Optional.of(new Session(profileName, expiryInstant));
        }
        return Optional.empty();
    }

    /**
     * Deletes the current session, if it exists
     *
     * @throws IOException
     */
    public void logoutCurrentSession() throws IOException {
        if (StringUtils.isNotBlank(environment.oktaProfile)) {
            logoutMultipleAccounts(environment.oktaProfile);
        }
        if (Files.exists(getSessionPath())) {
            Files.delete(getSessionPath());
        }
    }

    public void updateCurrentSession(Instant expiryInstant, String profileName) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(OKTA_AWS_CLI_PROFILE_PROPERTY, profileName);
        properties.setProperty(OKTA_AWS_CLI_EXPIRY_PROPERTY, expiryInstant.toString());
        properties.store(new FileWriter(getSessionPath().toString()), "Saved at: " + Instant.now().toString());
    }

    public Optional<Profile> getFromMultipleProfiles() throws IOException {
        return getMultipleProfile().getProfile(environment.oktaProfile, getMultipleProfilesPath());
    }

    public void addOrUpdateProfile(Instant start) throws IOException {
        MultipleProfile multipleProfile = getMultipleProfile();
        multipleProfile.addOrUpdateProfile(environment.oktaProfile, environment.awsRoleToAssume, start);

        try (final FileWriter fileWriter = FileHelper.getWriter(FileHelper.getOktaDirectory(), "profiles")) {
            multipleProfile.save(fileWriter);
        }
    }

    public boolean sessionIsActive(Instant startInstant, Session session) {
        return startInstant.isBefore(session.expiry);
    }

    private void logoutMultipleAccounts(String profileName) throws IOException {
        File cookieStore = CookieHelper.getCookies().toFile();
        cookieStore.deleteOnExit();

        getMultipleProfile().deleteProfile(getMultipleProfilesPath().toString(), profileName);
    }

    private Path getMultipleProfilesPath() throws IOException {
        return FileHelper.getFilePath(FileHelper.getOktaDirectory(), "profiles");
    }

    private MultipleProfile getMultipleProfile() throws IOException {
        Reader reader = FileHelper.getReader(FileHelper.getOktaDirectory(), "profiles");

        return new MultipleProfile(reader);
    }
}
