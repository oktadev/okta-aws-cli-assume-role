package com.okta.tools;

public class OktaAwsCliEnvironment {
    public final boolean browserAuth;
    public final String oktaOrg;
    public final String oktaUsername;
    public final String oktaPassword;
    public String oktaProfile;

    public final String oktaAwsAppUrl;

    public String awsRoleToAssume;

    public OktaAwsCliEnvironment(boolean browserAuth, String oktaOrg, String oktaUsername, String oktaPassword, String oktaProfile,
                                 String oktaAwsAppUrl, String awsRoleToAssume) {
        this.browserAuth = browserAuth;
        this.oktaOrg = oktaOrg;
        this.oktaUsername = oktaUsername;
        this.oktaPassword = oktaPassword;
        this.oktaProfile = oktaProfile;
        this.oktaAwsAppUrl = oktaAwsAppUrl;
        this.awsRoleToAssume = awsRoleToAssume;
    }
}
