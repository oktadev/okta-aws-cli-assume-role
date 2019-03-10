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

import com.okta.tools.OktaAwsCliEnvironment;
import com.okta.tools.aws.settings.MultipleProfile;
import com.okta.tools.models.Profile;
import com.okta.tools.models.Session;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public final class SessionHelper {

    private static final String OKTA_AWS_CLI_EXPIRY_PROPERTY = "OKTA_AWS_CLI_EXPIRY";
    private static final String OKTA_AWS_CLI_PROFILE_PROPERTY = "OKTA_AWS_CLI_PROFILE";
    private static final String OKTA_ORG_PROPERTY = "OKTA_ORG";

    private final OktaAwsCliEnvironment environment;
    private final CookieHelper cookieHelper;
    private final CredentialsHelper credentialsHelper;

    public SessionHelper(OktaAwsCliEnvironment environment, CookieHelper cookieHelper, CredentialsHelper credentialsHelper) {
        this.environment = environment;
        this.cookieHelper = cookieHelper;
        this.credentialsHelper = credentialsHelper;
    }

    /**
     * Gets the current session, if it exists
     *
     * @return The current {@link Session}
     * @throws IOException if file system or permissions errors are encountered
     */
    public Optional<Session> getCurrentSession() throws IOException {
        if (environment.oktaEnvMode) return Optional.empty();
        if (getSessionPath().toFile().exists()) {
            try (FileReader fileReader = new FileReader(getSessionPath().toFile())) {
                Properties properties = new Properties();
                properties.load(fileReader);
                String oktaOrg = properties.getProperty(OKTA_ORG_PROPERTY);
                if (oktaOrg != null && !oktaOrg.equals(environment.oktaOrg)) return Optional.empty();
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
        if (getSessionPath().toFile().exists()) {
            Files.delete(getSessionPath());
        }
    }

    private void logoutMultipleAccounts(String profileName) throws IOException {
        cookieHelper.clearCookies();
        FileHelper.usingPath(getProfilesFilePath(), reader -> {
            MultipleProfile multipleProfile = new MultipleProfile(reader);
            multipleProfile.deleteProfile(profileName);
            return multipleProfile;
        }, MultipleProfile::save);
        credentialsHelper.removeCredentialsFromProfile(profileName);
    }

    private Path getProfilesFilePath() throws IOException {
        return FileHelper.getOktaDirectory().resolve("profiles");
    }

    public void updateCurrentSession(Instant expiryInstant, String profileName) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(OKTA_ORG_PROPERTY, environment.oktaOrg);
        properties.setProperty(OKTA_AWS_CLI_PROFILE_PROPERTY, profileName);
        properties.setProperty(OKTA_AWS_CLI_EXPIRY_PROPERTY, expiryInstant.toString());
        properties.store(new FileWriter(getSessionPath().toString()), "Saved at: " + Instant.now().toString());
    }

    public Optional<Profile> getFromMultipleProfiles() throws IOException {
        return FileHelper.readingPath(getProfilesFilePath(), reader ->
            new MultipleProfile(reader).getProfile(environment.oktaProfile)
        );
    }

    public void addOrUpdateProfile(Instant sessionExpiry) throws IOException {
        FileHelper.usingPath(getProfilesFilePath(), reader -> {
            MultipleProfile multipleProfile = new MultipleProfile(reader);
            multipleProfile.addOrUpdateProfile(environment.oktaProfile, environment.awsRoleToAssume, environment.awsRegion,sessionExpiry);
            return multipleProfile;
        }, MultipleProfile::save);
    }

    public boolean sessionIsActive(Instant startInstant, Session session) {
        return startInstant.isBefore(session.expiry) && Objects.equals(session.profileName, environment.oktaProfile);
    }
}
