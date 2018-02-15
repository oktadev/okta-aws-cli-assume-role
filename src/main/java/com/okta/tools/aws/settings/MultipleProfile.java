/*
 * Copyright 2017 Okta
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
package com.okta.tools.aws.settings;

import org.ini4j.Profile;

import java.io.IOException;
import java.io.Reader;
import java.time.Instant;

public class MultipleProfile extends Settings {


    static final String SOURCE_PROFILE = "source_profile";
    static final String PROFILE_EXPIRY = "profile_expiry";
    static final String OKTA_SESSION = "okta_expiry";

    /**
     * Create a Credentials object from a given {@link Reader}. The data given by this {@link Reader} should
     * be INI-formatted.
     *
     * @param reader The settings we want to work with. N.B.: The reader is consumed by the constructor.
     * @throws IOException Thrown when we cannot read or load from the given {@param reader}.
     */
    public MultipleProfile(Reader reader) throws IOException {
        super(reader);

    }

    /**
     * Add or update a profile to an Okta Profile file based on {@code name}. This will be linked to a okta profile
     * of the same {@code name}, which should already be present in the profile expiry file.
     * The region for this new profile will be {@link Configuration#REGION_DEFAULT}.
     * @param name The name of the profile.
     * @param expiry the expiry time of the profile session.
     * @param okta_session the expiry time of the okta session
     */
    public void addOrUpdateProfile(String name, String okta_session,Instant expiry) {

        final Profile.Section awsProfile = settings.get(name) != null  ? settings.get(name) : settings.add(name);
        writeSessionProfile(awsProfile,name, okta_session , expiry);
    }

    private void writeSessionProfile(Profile.Section awsProfile, String name, String okta_session, Instant expiry) {
        awsProfile.put(SOURCE_PROFILE, name);
        awsProfile.put(OKTA_SESSION, okta_session);
        awsProfile.put(PROFILE_EXPIRY, expiry);
    }
}