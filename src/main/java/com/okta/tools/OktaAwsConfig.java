package com.okta.tools;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

final class OktaAwsConfig {

    private static final String CONFIG_FILENAME = "config.properties";

    static OktaAwsCliEnvironment loadEnvironment() {
        return loadEnvironment(null);
    }

    static OktaAwsCliEnvironment loadEnvironment(String profile) {        
        Properties properties = new Properties();
        getConfigFile().ifPresent(configFile -> {
            try (InputStream config = new FileInputStream(configFile.toFile())) {
                properties.load(new InputStreamReader(config));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        return new OktaAwsCliEnvironment(
                Boolean.valueOf(getEnvOrConfig(properties, "OKTA_BROWSER_AUTH")),
                getEnvOrConfig(properties, "OKTA_ORG"),
                getEnvOrConfig(properties, "OKTA_USERNAME"),
                getEnvOrConfig(properties, "OKTA_PASSWORD"),
                getProfile(profile, getEnvOrConfig(properties, "OKTA_PROFILE")),
                getEnvOrConfig(properties, "OKTA_AWS_APP_URL"),
                getEnvOrConfig(properties, "OKTA_AWS_ROLE_TO_ASSUME"),
                getStsDurationOrDefault(getEnvOrConfig(properties, "OKTA_STS_DURATION")),
                getAwsRegionOrDefault(getEnvOrConfig(properties, "OKTA_AWS_REGION"))
        );
    }

    private static Optional<Path> getConfigFile() {
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

    private static String getProfile(String profileFromCmdLine, String profileFromEnvOrConfig) {
        return profileFromCmdLine != null ?
                profileFromCmdLine : profileFromEnvOrConfig;
    }

    private static Integer getStsDurationOrDefault(String stsDuration) {
        return (stsDuration == null) ? 3600 : Integer.parseInt(stsDuration);
    }

    private static String getAwsRegionOrDefault(String region) {
        return (region == null) ? "us-east-1" : region;
    }
}
