package com.okta.tools.helpers;

import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLResult;
import com.okta.tools.OktaAwsCliEnvironment;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;

public class ProfileHelper {

    public static String createAwsProfile(AssumeRoleWithSAMLResult assumeResult) throws IOException {
        BasicSessionCredentials temporaryCredentials =
                new BasicSessionCredentials(
                        assumeResult.getCredentials().getAccessKeyId(),
                        assumeResult.getCredentials().getSecretAccessKey(),
                        assumeResult.getCredentials().getSessionToken());

        String awsAccessKey = temporaryCredentials.getAWSAccessKeyId();
        String awsSecretKey = temporaryCredentials.getAWSSecretKey();
        String awsSessionToken = temporaryCredentials.getSessionToken();

        String credentialsProfileName = getProfileName(assumeResult, OktaAwsCliEnvironment.oktaProfile);
        CredentialsHelper.updateCredentialsFile(credentialsProfileName, awsAccessKey, awsSecretKey, awsSessionToken);
        return credentialsProfileName;
    }

    private static String getProfileName(AssumeRoleWithSAMLResult assumeResult, String oktaProfile) {
        String credentialsProfileName;
        if (StringUtils.isNotBlank(oktaProfile)) {
            credentialsProfileName = oktaProfile;
        } else {
            credentialsProfileName = assumeResult.getAssumedRoleUser().getArn();
            if (credentialsProfileName.startsWith("arn:aws:sts::")) {
                credentialsProfileName = credentialsProfileName.substring(13);
            }
            if (credentialsProfileName.contains(":assumed-role")) {
                credentialsProfileName = credentialsProfileName.replaceAll(":assumed-role", "");
            }
        }
        return credentialsProfileName;
    }
}
