package com.okta.tools.helpers;

import com.okta.tools.aws.settings.Configuration;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;

public final class AwsConfigHelper {
    /**
     * Gets a reader for the config file. If the file doesn't exist, it creates it
     * @author Andrei Hava
     * @since 02/14/2018
     * @return The file reader for the config file
     * @throws FileNotFoundException
     */
    public static Reader getConfigReader() throws FileNotFoundException
    {
        return AwsFileHelper.getReader(AwsFileHelper.getAwsDirectory().toString() + "/config");
    }

    /**
     * Gets a FileWriter for the config file
     * @author Andrei Hava
     * @since 02/14/2018
     * @return The FileWriter for the config file
     * @throws IOException
     */
    public static FileWriter getConfigWriter() throws IOException
    {
        return AwsFileHelper.getWriter(AwsFileHelper.getAwsDirectory().toString() + "/config");
    }

    /**
     * Updates the configuration file
     * @param profileName The profile name to use
     * @param roleToAssume The role to assume
     * @throws IOException
     */
    public static void updateConfigFile(String profileName, String roleToAssume) throws IOException
    {
        try (Reader reader = getConfigReader()) {
            // Create the configuration object with the data from the config file
            Configuration configuration = new Configuration(reader);

            // Write the given profile data
            configuration.addOrUpdateProfile(profileName, roleToAssume);

            // Write the updated profile
            try (FileWriter fileWriter = getConfigWriter()) {
                configuration.save(fileWriter);
            }
        }
    }
}
