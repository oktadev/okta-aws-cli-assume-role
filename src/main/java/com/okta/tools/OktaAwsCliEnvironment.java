package com.okta.tools;

public class OktaAwsCliEnvironment {
    public String oktaOrg;
    public String oktaUsername;
    public String oktaPassword;
    public String oktaProfile;

    public String oktaAwsAppUrl;

    public String awsRoleToAssume;

    public OktaAwsCliEnvironment(String oktaOrg, String oktaUsername, String oktaPassword, String oktaProfile,
                                 String oktaAwsAppUrl, String awsRoleToAssume) {
        this.oktaOrg = oktaOrg;
        this.oktaUsername = oktaUsername;
        this.oktaPassword = oktaPassword;
        this.oktaProfile = oktaProfile;
        this.oktaAwsAppUrl = oktaAwsAppUrl;
        this.awsRoleToAssume = awsRoleToAssume;
    }
}
