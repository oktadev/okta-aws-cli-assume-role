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
package com.okta.tools;

import software.amazon.awssdk.regions.Region;

public class OktaAwsCliEnvironment {
    public final boolean browserAuth;
    public final String oktaOrg;
    public final String oktaUsername;
    public final InterruptibleSupplier<String> oktaPassword;
    public final String oktaCookiesPath;
    public String oktaProfile;

    public final String oktaAwsAppUrl;

    public String awsRoleToAssume;

    public int stsDuration;
    public final Region awsRegion;
    public final String oktaMfaChoice;
    public boolean oktaEnvMode;

    public String oktaIgnoreSaml;

    public OktaAwsCliEnvironment()
    {
        this(false, null, null, null, null, null, null, null, 0, null, null, false, null);
    }

    public OktaAwsCliEnvironment(boolean browserAuth, String oktaOrg,
                                 String oktaUsername, InterruptibleSupplier<String> oktaPassword, String oktaCookiesPath,
                                 String oktaProfile, String oktaAwsAppUrl, String awsRoleToAssume,
                                 int stsDuration, Region awsRegion,
                                 String oktaMfaChoice, boolean oktaEnvMode, String oktaIgnoreSaml) {
        this.browserAuth = browserAuth;
        this.oktaOrg = oktaOrg;
        this.oktaUsername = oktaUsername;
        this.oktaPassword = oktaPassword;
        this.oktaCookiesPath = oktaCookiesPath;
        this.oktaProfile = oktaProfile;
        this.oktaAwsAppUrl = oktaAwsAppUrl;
        this.awsRoleToAssume = awsRoleToAssume;
        this.stsDuration = stsDuration;
        this.awsRegion = awsRegion;
        this.oktaMfaChoice = oktaMfaChoice;
        this.oktaEnvMode = oktaEnvMode;
        this.oktaIgnoreSaml = oktaIgnoreSaml;
    }

}
