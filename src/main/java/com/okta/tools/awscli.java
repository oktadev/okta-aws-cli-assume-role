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

public class awscli {
    public static void main(String[] args) throws Exception {
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

        if (args.length > 0 && "logout".equals(args[0])) {
            OktaAwsCliAssumeRole.withEnvironment(OktaAwsConfig.loadEnvironment(profile)).logoutSession();
            System.out.println("You have been logged out");
            System.exit(0);
            return;
        }


        List<String> awsCommand = new ArrayList<>();
        String profileName = OktaAwsCliAssumeRole.withEnvironment(OktaAwsConfig.loadEnvironment(profile)).run(Instant.now());
        awsCommand.add("aws");
        awsCommand.add("--profile");
        awsCommand.add(profileName);
        awsCommand.addAll(Arrays.asList(args));
        ProcessBuilder awsProcessBuilder = new ProcessBuilder().inheritIO().command(awsCommand);
        Process awsSubProcess = awsProcessBuilder.start();
        int exitCode = awsSubProcess.waitFor();
        System.exit(exitCode);
    }
}
