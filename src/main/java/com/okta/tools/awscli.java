package com.okta.tools;

import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

public class awscli {
    public static void main(String[] args) throws Exception {
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
        properties.load(new FileReader("config.properties"));

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
