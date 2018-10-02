package com.okta.tools.helpers;

import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLResult;
import com.okta.tools.OktaAwsCliEnvironment;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfileHelper {

    private final CredentialsHelper credentialsHelper;
    private OktaAwsCliEnvironment environment;
    private final Pattern assumedRoleUserPattern = Pattern.compile(
            "^arn:aws:sts::(?<account>\\d{12}):assumed-role/(?<roleName>[^/]*)/(?<userName>.*$)");

    public ProfileHelper(CredentialsHelper credentialsHelper, OktaAwsCliEnvironment environment) {
        this.credentialsHelper = credentialsHelper;
        this.environment = environment;
    }

    public void createAwsProfile(AssumeRoleWithSAMLResult assumeResult, String credentialsProfileName) throws IOException {
        BasicSessionCredentials temporaryCredentials =
                new BasicSessionCredentials(
                        assumeResult.getCredentials().getAccessKeyId(),
                        assumeResult.getCredentials().getSecretAccessKey(),
                        assumeResult.getCredentials().getSessionToken());

        String awsAccessKey = temporaryCredentials.getAWSAccessKeyId();
        String awsSecretKey = temporaryCredentials.getAWSSecretKey();
        String awsSessionToken = temporaryCredentials.getSessionToken();

        credentialsHelper.updateCredentialsFile(credentialsProfileName, awsAccessKey, awsSecretKey, awsSessionToken);
    }

    public String getProfileName(AssumeRoleWithSAMLResult assumeResult) {
        if (StringUtils.isNotBlank(environment.oktaProfile)) {
            return environment.oktaProfile;
        }

        String credentialsProfileName = assumeResult.getAssumedRoleUser().getArn();
        Matcher matcher = assumedRoleUserPattern.matcher(credentialsProfileName);
        if (matcher.matches()) {
            return matcher.group("roleName") + "_" + matcher.group("account");
        }

        return "temp";
    }
}
