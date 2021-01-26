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

import java.time.Instant;
import java.util.Map;
import java.util.logging.Logger;

public class WithOkta {
    private static final Logger logger = Logger.getLogger(WithOkta.class.getName());

    public static void main(final String[] args) throws Exception {
        if (LogoutHandler.handleLogout(args)) return;
        OktaAwsCliEnvironment environment = OktaAwsConfig.loadEnvironment();
        OktaAwsCliAssumeRole.RunResult runResult = OktaAwsCliAssumeRole.withEnvironment(environment).run(Instant.now());
        ProcessBuilder awsProcessBuilder = new ProcessBuilder().inheritIO();
        if (environment.oktaEnvMode) {
            Map<String, String> awsEnvironment = awsProcessBuilder.environment();
            awsEnvironment.put("AWS_ACCESS_KEY_ID", runResult.accessKeyId);
            awsEnvironment.put("AWS_SECRET_ACCESS_KEY", runResult.secretAccessKey);
            awsEnvironment.put("AWS_SESSION_TOKEN", runResult.sessionToken);
            awsEnvironment.put("AWS_DEFAULT_REGION", environment.awsRegion.id());
        }

        if(args.length == 0) {
            logger.info("No additional command line arguments provided. Hint: okta-aws <aws cli command>");
            System.exit(0);
            return;
        }
        awsProcessBuilder.command(args);
        logger.fine(() -> "AWS CLI command line: " + awsProcessBuilder.command().toString());
        Process awsSubProcess = awsProcessBuilder.start();
        int exitCode = awsSubProcess.waitFor();
        System.exit(exitCode);
    }
}
