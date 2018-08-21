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

import com.okta.tools.OktaAwsCliEnvironment;

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
    private final OktaAwsCliEnvironment environment;

    /**
     * Create a Credentials object from a given {@link Reader}. The data given by this {@link Reader} should
     * be INI-formatted.
     *
     * @param reader The settings we want to work with. N.B.: The reader is consumed by the constructor.
     * @throws IOException Thrown when we cannot read or load from the given {@param reader}.
     */
    public Credentials(Reader reader, OktaAwsCliEnvironment environment) throws IOException {
        super(reader);
        this.environment = environment;
    }

    Credentials(Reader reader) throws IOException {
        this(reader, new OktaAwsCliEnvironment());
    }

    /**
     * Add or update a profile to an AWS credentials file based on {@code name}.
     *
     * @param name            The name of the profile.
     * @param awsAccessKey    The access key to use for the profile.
     * @param awsSecretKey    The secret key to use for the profile.
     * @param awsSessionToken The session token to use for the profile.
     */
    public void addOrUpdateProfile(String name, String awsAccessKey, String awsSecretKey, String awsSessionToken) {
        name = DEFAULT_PROFILE_NAME.equals(name) ? DEFAULT_PROFILE_NAME : name + environment.credentialsSuffix;
        setProperty(name, ACCES_KEY_ID, awsAccessKey);
        setProperty(name, SECRET_ACCESS_KEY, awsSecretKey);
        setProperty(name, SESSION_TOKEN, awsSessionToken);
    }
}
