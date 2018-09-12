package com.okta.tools.helpers;

import com.okta.tools.OktaAwsCliEnvironment;
import com.okta.tools.aws.settings.Configuration;

import java.io.IOException;

public final class ConfigHelper {

    private OktaAwsCliEnvironment environment;

    public ConfigHelper(OktaAwsCliEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Updates the configuration file
     *
     * @throws IOException if a file system or permissions error occurs
     */
    public void updateConfigFile() throws IOException {
        FileHelper.usingPath(FileHelper.getAwsDirectory().resolve("config"), reader -> {
            Configuration configuration = new Configuration(reader, environment);
            configuration.addOrUpdateProfile(environment.oktaProfile, environment.awsRoleToAssume, environment.awsRegion);
            return configuration;
        }, Configuration::save);
    }
}
