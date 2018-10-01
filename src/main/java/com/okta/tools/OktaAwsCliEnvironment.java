package com.okta.tools;

import java.util.function.Supplier;

public class OktaAwsCliEnvironment {
    public final boolean browserAuth;
    public final String oktaOrg;
    public final String oktaUsername;
    public final Supplier<String> oktaPassword;
    public final String oktaCookiesPath;
    public String oktaProfile;

    public final String oktaAwsAppUrl;

    public String awsRoleToAssume;

    public int stsDuration;
    public final boolean oktaEnvMode;

    public OktaAwsCliEnvironment()
    {
        this(false, null, null, null, null, null, null, null, 0, false);
    }

    public OktaAwsCliEnvironment(boolean browserAuth, String oktaOrg,
                                 String oktaUsername, Supplier<String> oktaPassword, String oktaCookiesPath,
                                 String oktaProfile, String oktaAwsAppUrl, String awsRoleToAssume,
                                 int stsDuration,
                                 boolean oktaEnvMode) {
        this.browserAuth = browserAuth;
        this.oktaOrg = oktaOrg;
        this.oktaUsername = oktaUsername;
        this.oktaPassword = oktaPassword;
        this.oktaCookiesPath = oktaCookiesPath;
        this.oktaProfile = oktaProfile;
        this.oktaAwsAppUrl = oktaAwsAppUrl;
        this.awsRoleToAssume = awsRoleToAssume;
        this.stsDuration = stsDuration;
        this.oktaEnvMode = oktaEnvMode;
    }
}
