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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class awscli {
    public static void main(String[] args) throws Exception {
        if (LogoutHandler.handleLogout(args)) return;
      
        // support named profiles from the CLI
        // https://docs.aws.amazon.com/cli/latest/userguide/cli-multiple-profiles.html
        String profile = null;
        boolean hasProfile = false;
        for (String arg : args) {
            if (hasProfile) {
                profile = arg;
                break;
            }
            hasProfile = "--profile".equals(arg);
        }

        OktaAwsCliEnvironment environment = OktaAwsConfig.loadEnvironment(profile);
        OktaAwsCliAssumeRole.RunResult runResult = OktaAwsCliAssumeRole.withEnvironment(environment).run(Instant.now());
        ProcessBuilder awsProcessBuilder = new ProcessBuilder().inheritIO();

        List<String> awsCommand = new ArrayList<>();
        awsCommand.add("aws");
        if (environment.oktaEnvMode) {
            Map<String, String> awsEnvironment = awsProcessBuilder.environment();
            awsEnvironment.put("AWS_ACCESS_KEY_ID", runResult.accessKeyId);
            awsEnvironment.put("AWS_SECRET_ACCESS_KEY", runResult.secretAccessKey);
            awsEnvironment.put("AWS_SESSION_TOKEN", runResult.sessionToken);
        } else {
            awsCommand.add("--profile");
            awsCommand.add(runResult.profileName);
        }
        awsCommand.addAll(Arrays.asList(args));
        awsProcessBuilder.command(awsCommand);
        Process awsSubProcess = awsProcessBuilder.start();
        int exitCode = awsSubProcess.waitFor();
        System.exit(exitCode);
    }
}
