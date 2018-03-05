package com.okta.tools;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

final class OktaAwsConfig {

    private static final String CONFIG_FILENAME = "config.properties";

    static OktaAwsCliAssumeRole createAwscli() {
        Properties properties = new Properties();
        getConfigFile().ifPresent(configFile -> {
            try (InputStream config = new FileInputStream(configFile.toFile())) {
                properties.load(new InputStreamReader(config));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        return OktaAwsCliAssumeRole.createOktaAwsCliAssumeRole(
                getEnvOrConfig(properties, "OKTA_ORG"),
                getEnvOrConfig(properties, "OKTA_AWS_APP_URL"),
                getEnvOrConfig(properties, "OKTA_USERNAME"),
                getEnvOrConfig(properties, "OKTA_PASSWORD"),
                getEnvOrConfig(properties, "OKTA_PROFILE"),
                getEnvOrConfig(properties, "OKTA_AWS_ROLE_TO_ASSUME")

        );
    }

    private static Optional<Path> getConfigFile()
    {
        Path configInWorkingDir = Paths.get(CONFIG_FILENAME);
        if (Files.isRegularFile(configInWorkingDir)) {
            return Optional.of(configInWorkingDir);
        }
        Path userHome = Paths.get(System.getProperty("user.home"));
        Path oktaDir = userHome.resolve(".okta");
        Path configInOktaDir = oktaDir.resolve(CONFIG_FILENAME);
        if (Files.isRegularFile(configInOktaDir)) {
            return Optional.of(configInOktaDir);
        }
        return Optional.empty();
    }

    private static String getEnvOrConfig(Properties properties, String propertyName) {
        String envValue = System.getenv(propertyName);
        return envValue != null ?
                envValue : properties.getProperty(propertyName);
    }
}
