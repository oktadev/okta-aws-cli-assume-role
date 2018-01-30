package com.okta.tools;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

final class OktaAwsConfig {
    static OktaAwsCliAssumeRole createAwscli() throws IOException {
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
                getEnvOrConfig(properties, "OKTA_PROFILE"),
                getEnvOrConfig(properties, "OKTA_AWS_ROLE_TO_ASSUME")

        );
    }

    private static String getEnvOrConfig(Properties properties, String propertyName) {
        String envValue = System.getenv(propertyName);
        return envValue != null ?
                envValue : properties.getProperty(propertyName);
    }
}
