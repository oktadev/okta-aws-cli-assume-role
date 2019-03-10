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

        String awsRegion = environment.awsRegion;
        
        credentialsHelper.updateCredentialsFile(credentialsProfileName, awsAccessKey, awsSecretKey, awsRegion,
                awsSessionToken);
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
