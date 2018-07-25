package com.okta.tools;

public class OktaAwsCliEnvironment {
    public final boolean browserAuth;
    public final String oktaOrg;
    public final String oktaUsername;
    public final String oktaPassword;
    public String oktaProfile;

    public final String oktaAwsAppUrl;

    public String awsRoleToAssume;

    public int stsDuration;
    public final String awsRegion;

    public OktaAwsCliEnvironment(boolean browserAuth, String oktaOrg, 
                                 String oktaUsername, String oktaPassword, String oktaProfile,
                                 String oktaAwsAppUrl, String awsRoleToAssume, int stsDuration,
                                 String awsRegion) {
        this.browserAuth = browserAuth;
        this.oktaOrg = oktaOrg;
        this.oktaUsername = oktaUsername;
        this.oktaPassword = oktaPassword;
        this.oktaProfile = oktaProfile;
        this.oktaAwsAppUrl = oktaAwsAppUrl;
        this.awsRoleToAssume = awsRoleToAssume;
        this.stsDuration = stsDuration;
        this.awsRegion = awsRegion;
    }
}
