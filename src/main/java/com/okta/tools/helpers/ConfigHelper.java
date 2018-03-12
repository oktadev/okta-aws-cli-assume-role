package com.okta.tools.helpers;

import com.okta.tools.OktaAwsCliEnvironment;
import com.okta.tools.aws.settings.Configuration;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;

public final class ConfigHelper {

    private OktaAwsCliEnvironment environment;

    public ConfigHelper(OktaAwsCliEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Gets a reader for the config file. If the file doesn't exist, it creates it
     *
     * @return A {@link Reader} for the config file
     * @throws IOException
     */
    public Reader getConfigReader() throws IOException {
        return FileHelper.getReader(FileHelper.getAwsDirectory(), "config");
    }

    /**
     * Gets a FileWriter for the config file
     *
     * @return A {@link FileWriter} for the config file
     * @throws IOException
     */
    public FileWriter getConfigWriter() throws IOException {
        return FileHelper.getWriter(FileHelper.getAwsDirectory(), "config");
    }

    /**
     * Updates the configuration file
     *
     * @throws IOException
     */
    public void updateConfigFile() throws IOException {
        try (Reader reader = getConfigReader()) {
            // Create the configuration object with the data from the config file
            Configuration configuration = new Configuration(reader);

            // Write the given profile data
            configuration.addOrUpdateProfile(environment.oktaProfile, environment.awsRoleToAssume);

            // Write the updated profile
            try (FileWriter fileWriter = getConfigWriter()) {
                configuration.save(fileWriter);
            }
        }
    }
}
