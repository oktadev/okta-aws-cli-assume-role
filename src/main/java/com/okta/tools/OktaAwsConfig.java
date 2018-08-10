package com.okta.tools;

import com.okta.tools.authentication.OktaAuthentication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    private static final Logger logger = LogManager.getLogger(OktaAwsConfig.class);

    private static final String CONFIG_FILENAME = "config.properties";

    static OktaAwsCliEnvironment loadEnvironment() {
        return loadEnvironment(null);
    }

    static OktaAwsCliEnvironment loadEnvironment(String profile) {
        Properties properties = new Properties();
        Optional<Path> path = getConfigFile();
        if (path.isPresent()) {
            try (InputStream config = new FileInputStream(path.get().toFile())) {
                logger.debug("Reading config settings from file: " + path.get().toAbsolutePath().toString());
                properties.load(new InputStreamReader(config));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            try (InputStream config = properties.getClass().getResourceAsStream("/config.properties")) {
                if (config != null) {
                    properties.load(new InputStreamReader(config));
                } else {
                    // Don't fail if no config.properties found in classpath as we will fallback to env variables
                    logger.debug("No config.properties file found in working directory, ~/.okta, or the class loader");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return new OktaAwsCliEnvironment(
                Boolean.valueOf(getEnvOrConfig(properties, "OKTA_BROWSER_AUTH")),
                getEnvOrConfig(properties, "OKTA_ORG"),
                getEnvOrConfig(properties, "OKTA_USERNAME"),
                getEnvOrConfig(properties, "OKTA_PASSWORD"),
                getEnvOrConfig(properties, "OKTA_COOKIES_PATH"),
                getProfile(profile, getEnvOrConfig(properties, "OKTA_PROFILE")),
                getEnvOrConfig(properties, "OKTA_AWS_APP_URL"),
                getEnvOrConfig(properties, "OKTA_AWS_ROLE_TO_ASSUME"),
                getStsDurationOrDefault(getEnvOrConfig(properties, "OKTA_STS_DURATION")),
                getAwsRegionOrDefault(getEnvOrConfig(properties, "OKTA_AWS_REGION")),
                getEnvOrConfig(properties, "OKTA_PROFILE_PREFIX"),
                getEnvOrConfig(properties, "OKTA_CREDENTIALS_SUFFIX")
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
        if (envValue != null) {
            logger.debug("Using " + propertyName + "  value from the environment.");
            return envValue;
        } else {
            logger.debug("Using " + propertyName + "  value from the config file." );
            return properties.getProperty(propertyName);
        }
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
