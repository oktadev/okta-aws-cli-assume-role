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

import java.io.IOException;
import java.time.Instant;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithSamlResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

public interface AWSCredentialsUtil {

    static AwsSessionCredentials getAWSCredential() throws IOException, InterruptedException {
        AssumeRoleWithSamlResponse samlResult = OktaAwsCliAssumeRole.withEnvironment(OktaAwsConfig.loadEnvironment()).getAssumeRoleWithSAMLResult(Instant.now());

        Credentials credentials = samlResult.credentials();

        return AwsSessionCredentials.create(credentials.accessKeyId(), credentials.secretAccessKey(), credentials.sessionToken());
    }

    static AwsSessionCredentials getAWSCredential (OktaAwsCliEnvironment environment) throws IOException, InterruptedException {
        AssumeRoleWithSamlResponse samlResult = OktaAwsCliAssumeRole.withEnvironment(environment).getAssumeRoleWithSAMLResult(Instant.now());

        Credentials credentials = samlResult.credentials();

        return AwsSessionCredentials.create(credentials.accessKeyId(), credentials.secretAccessKey(), credentials.sessionToken());
    }

}
