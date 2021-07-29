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

import com.okta.tools.OktaAwsCliEnvironment;
import org.apache.commons.lang.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithSamlResponse;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfileHelper {

    private final CredentialsHelper credentialsHelper;
    private OktaAwsCliEnvironment environment;
    private final Pattern assumedRoleUserPattern = Pattern.compile(
            "^arn:aws(|-gov):sts::(?<account>\\d{12}):assumed-role/(?<roleName>[^/]*)/(?<userName>.*$)");

    public ProfileHelper(CredentialsHelper credentialsHelper, OktaAwsCliEnvironment environment) {
        this.credentialsHelper = credentialsHelper;
        this.environment = environment;
    }

    public void createAwsProfile(AssumeRoleWithSamlResponse assumeResult, String credentialsProfileName) throws IOException {
        AwsSessionCredentials temporaryCredentials =
                AwsSessionCredentials.create(
                        assumeResult.credentials().accessKeyId(),
                        assumeResult.credentials().secretAccessKey(),
                        assumeResult.credentials().sessionToken());

        String awsAccessKey = temporaryCredentials.accessKeyId();
        String awsSecretKey = temporaryCredentials.secretAccessKey();
        String awsSessionToken = temporaryCredentials.sessionToken();

        Region awsRegion = environment.awsRegion;

        credentialsHelper.updateCredentialsFile(credentialsProfileName, awsAccessKey, awsSecretKey, awsRegion,
                awsSessionToken);
    }

    public String getProfileName(AssumeRoleWithSamlResponse assumeResult) {
        if (StringUtils.isNotBlank(environment.oktaProfile)) {
            return environment.oktaProfile;
        }

        String credentialsProfileName = assumeResult.assumedRoleUser().arn();
        Matcher matcher = assumedRoleUserPattern.matcher(credentialsProfileName);
        if (matcher.matches()) {
            return matcher.group("roleName") + "_" + matcher.group("account");
        }

        return "temp";
    }
}
