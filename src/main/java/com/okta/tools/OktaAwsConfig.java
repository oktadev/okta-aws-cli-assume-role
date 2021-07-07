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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import software.amazon.awssdk.regions.Region;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;

final class OktaAwsConfig {

    private static final Logger logger = Logger.getLogger(OktaAwsConfig.class.getName());

    private static final String CONFIG_FILENAME = "config.properties";
    private static final String CONFIG_PROFILE_FILENAME_FORMAT = "config.%s.properties";

    static OktaAwsCliEnvironment loadEnvironment() {
        return loadEnvironment(System.getenv("OKTA_PROFILE"));
    }

    static OktaAwsCliEnvironment loadEnvironment(String profile) {
        Properties properties = new Properties();
        Optional<ArrayList<Path>> paths = getConfigFile(profile);
        if (paths.isPresent()) {
            for (Path path : paths.get()) {
                try (InputStream config = new FileInputStream(path.toFile())) {
                    logger.finer(() -> "Reading config settings from file: " + path.toAbsolutePath().toString());
                    properties.load(new InputStreamReader(config));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        } else {
            try (InputStream config = properties.getClass().getResourceAsStream("/config.properties")) {
                if (config != null) {
                    properties.load(new InputStreamReader(config));
                } else {
                    // Don't fail if no config.properties found in classpath as we will fallback to env variables
                    logger.finer(() -> "No config.properties file found in working directory, ~/.okta, or the class loader");
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        return new OktaAwsCliEnvironment(
                Boolean.parseBoolean(getEnvOrConfig(properties, "OKTA_BROWSER_AUTH")),
                getEnvOrConfig(properties, "OKTA_ORG"),
                getEnvOrConfig(properties, "OKTA_USERNAME"),
                deferProgram(getEnvOrConfig(properties, "OKTA_PASSWORD_CMD")),
                getEnvOrConfig(properties, "OKTA_COOKIES_PATH"),
                getProfile(profile, getEnvOrConfig(properties, "OKTA_PROFILE")),
                getEnvOrConfig(properties, "OKTA_AWS_APP_URL"),
                getEnvOrConfig(properties, "OKTA_AWS_ROLE_TO_ASSUME"),
                getStsDurationOrDefault(getEnvOrConfig(properties, "OKTA_STS_DURATION")),
                getAwsRegionOrDefault(getEnvOrConfig(properties, "OKTA_AWS_REGION")),
                getEnvOrConfig(properties, "OKTA_MFA_CHOICE"),
                Boolean.parseBoolean(getEnvOrConfig(properties, "OKTA_ENV_MODE")),
                getEnvOrConfig(properties, "OKTA_IGNORE_SAML_REQ_CONTAIN")
        );
    }

    private static InterruptibleSupplier<String> deferProgram(String oktaPasswordCommand) {
        if (oktaPasswordCommand == null) return null;
        return () -> runProgram(oktaPasswordCommand);
    }

    private static String runProgram(String oktaPasswordCommand) throws InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        if (SystemUtils.IS_OS_WINDOWS) {
            processBuilder.command("cmd", "/C", oktaPasswordCommand);
        } else if (SystemUtils.IS_OS_UNIX) {
            processBuilder.command("sh", "-c", oktaPasswordCommand);
        }
        processBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        try {
            Process passwordCommandProcess = processBuilder.start();
            String password = getOutput(passwordCommandProcess);
            int exitCode = passwordCommandProcess.waitFor();
            if (exitCode == 0) return password;
            throw new IllegalStateException("password command failed with exit code " + exitCode);
        } catch (IOException e) {
            throw new IllegalStateException("password command failed", e);
        }
    }

    private static String getOutput(Process process) throws IOException {
        try (InputStream inputStream = process.getInputStream();
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            return bufferedReader.lines().collect(Collectors.joining("\n"));
        }
    }

    private static Optional<ArrayList<Path>> getConfigFile(String profile) {
        ArrayList<String> configFiles = new ArrayList<>();
        configFiles.add(CONFIG_FILENAME);
        if (StringUtils.isNotBlank(profile)) {
            configFiles.add(String.format(CONFIG_PROFILE_FILENAME_FORMAT, profile));
        }

        ArrayList<Path> paths = new ArrayList<>();
        for (String configFile : configFiles) {
            Path configInWorkingDir = Paths.get(configFile);
            if (configInWorkingDir.toFile().isFile()) {
                paths.add(configInWorkingDir);
            }
            Path userHome = Paths.get(System.getProperty("user.home"));
            Path oktaDir = userHome.resolve(".okta");
            Path configInOktaDir = oktaDir.resolve(configFile);
            if (configInOktaDir.toFile().isFile()) {
                paths.add(configInOktaDir);
            }
        }

        if (!paths.isEmpty()) {
            return Optional.of(paths);
        }

        return Optional.empty();
    }

    private static String getEnvOrConfig(Properties properties, String propertyName) {
        String envValue = System.getenv(propertyName);
        if (envValue != null) {
            logger.finer(() -> "Using " + propertyName + "  value from the environment.");
            return envValue;
        } else {
            logger.finer(() -> "Using " + propertyName + "  value from the config file." );
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

    private static Region getAwsRegionOrDefault(String region) {
        return Region.of((region == null) ? "us-east-1" : region);
    }
}
