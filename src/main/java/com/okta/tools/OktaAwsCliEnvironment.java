package com.okta.tools;

import java.util.function.Supplier;

public class OktaAwsCliEnvironment {
    private static final String DEFAULT_PROFILE_PREFIX = "profile ";
    private static final String DEFAULT_CREDENTIALS_SUFFIX = "_source";

    public final boolean browserAuth;
    public final String oktaOrg;
    public final String oktaUsername;
    public final Supplier<String> oktaPassword;
    public final String oktaCookiesPath;
    public String oktaProfile;

    public final String oktaAwsAppUrl;

    public String awsRoleToAssume;

    public int stsDuration;
    public final String awsRegion;
    public final String profilePrefix;
    public final boolean oktaEnvMode;
    public final String credentialsSuffix;

    public OktaAwsCliEnvironment()
    {
        this(false, null, null, null, null, null, null, null, 0, null, null, false, null);
    }

    public OktaAwsCliEnvironment(boolean browserAuth, String oktaOrg,
                                 String oktaUsername, Supplier<String> oktaPassword, String oktaCookiesPath,
                                 String oktaProfile, String oktaAwsAppUrl, String awsRoleToAssume,
                                 int stsDuration, String awsRegion, String profilePrefix,
                                 boolean oktaEnvMode, String credentialsSuffix) {
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
        this.profilePrefix = profilePrefix == null ? DEFAULT_PROFILE_PREFIX : profilePrefix;
        this.oktaEnvMode = oktaEnvMode;
        this.credentialsSuffix = credentialsSuffix == null ? DEFAULT_CREDENTIALS_SUFFIX : credentialsSuffix;
    }
}
