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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

public class CredentialProcess {
    public static void main(String[] args) throws Exception {
        OktaAwsCliEnvironment environment = OktaAwsConfig.loadEnvironment();
        environment.oktaEnvMode = true;
        Instant startInstant = Instant.now();
        Duration sessionLength = Duration.of(environment.stsDuration, ChronoUnit.SECONDS);
        Instant expirationInstant = startInstant.plus(sessionLength);
        OktaAwsCliAssumeRole.RunResult runResult = OktaAwsCliAssumeRole.withEnvironment(environment).run(startInstant);
        Map<String, Object> credential = new HashMap<>(5);
        credential.put("Version", 1);
        credential.put("AccessKeyId", runResult.accessKeyId);
        credential.put("SecretAccessKey", runResult.secretAccessKey);
        credential.put("SessionToken", runResult.sessionToken);
        credential.put("Expiration", expirationInstant.toString());
        ObjectMapper objectMapper = new ObjectMapper();
        String credentialJson = objectMapper.writeValueAsString(credential);
        System.out.println(credentialJson);
    }
}
