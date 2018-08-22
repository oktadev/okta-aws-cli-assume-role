package com.okta.tools;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

public class WithOkta {
    public static void main(String[] args) throws Exception {
        if (LogoutHandler.handleLogout(args)) return;
        OktaAwsCliEnvironment environment = OktaAwsConfig.loadEnvironment();
        OktaAwsCliAssumeRole.RunResult runResult = OktaAwsCliAssumeRole.withEnvironment(environment).run(Instant.now());
        ProcessBuilder awsProcessBuilder = new ProcessBuilder().inheritIO();
        if (environment.oktaEnvMode) {
            Map<String, String> awsEnvironment = awsProcessBuilder.environment();
            awsEnvironment.put("AWS_ACCESS_KEY_ID", runResult.accessKeyId);
            awsEnvironment.put("AWS_SECRET_ACCESS_KEY", runResult.secretAccessKey);
            awsEnvironment.put("AWS_SESSION_TOKEN", runResult.sessionToken);
            args = removeProfileArguments(args);
        }
        awsProcessBuilder.command(args);
        Process awsSubProcess = awsProcessBuilder.start();
        int exitCode = awsSubProcess.waitFor();
        System.exit(exitCode);
    }

    private static String[] removeProfileArguments(String[] args) {
        List<String> argsList = new ArrayList<>(args.length);
        boolean profileArg = false;
        for (String arg : args) {
            if ("--profile".equals(arg)) {
                // skip the profile flag and note to skip its argument
                profileArg = true;
            }
            else if (profileArg) {
                // skip the profile argument
                profileArg = false;
            } else {
                argsList.add(arg);
            }
        }
        return argsList.toArray(new String[] {});
    }
}
