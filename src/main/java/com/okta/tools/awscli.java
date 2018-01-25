/*
 * Copyright 2017 Okta
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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.*;

public class awscli {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "logout".equals(args[0])) {
            OktaAwsCliAssumeRole.logoutSession();
            System.out.println("You have been logged out");
            System.exit(0);
            return;
        }
        String profileName = createAwscli().run(Instant.now());
        List<String> awsCommand = new ArrayList<>();
        awsCommand.add("aws");
        awsCommand.add("--profile");
        awsCommand.add(profileName);
        awsCommand.addAll(Arrays.asList(args));
        ProcessBuilder awsProcessBuilder = new ProcessBuilder().inheritIO().command(awsCommand);
        Process awsSubProcess = awsProcessBuilder.start();
        int exitCode = awsSubProcess.waitFor();
        System.exit(exitCode);
    }

    private static OktaAwsCliAssumeRole createAwscli() throws IOException {
        Properties properties = new Properties();
        InputStream config;
        try {
            config = new FileInputStream("config.properties");
        } catch (IOException ex) {
            // Fallback to classpath input stream
            config = properties.getClass().getResourceAsStream("/config.properties");
        }
        properties.load(new InputStreamReader(config));

        return OktaAwsCliAssumeRole.createOktaAwsCliAssumeRole(
                getEnvOrConfig(properties, "OKTA_ORG"),
                getEnvOrConfig(properties, "OKTA_AWS_APP_URL"),
                getEnvOrConfig(properties, "OKTA_USERNAME"),
                getEnvOrConfig(properties, "OKTA_PASSWORD"),
                getEnvOrConfig(properties, "OKTA_AWS_ROLE_TO_ASSUME")
        );
    }

    private static String getEnvOrConfig(Properties properties, String propertyName) {
        String envValue = System.getenv(propertyName);
        return envValue != null ?
                envValue : properties.getProperty(propertyName);
    }
}
