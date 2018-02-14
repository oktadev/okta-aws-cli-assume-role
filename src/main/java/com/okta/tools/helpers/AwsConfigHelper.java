package com.okta.tools.helpers;

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
        return AwsFileHelper.getReader(AwsFileHelper.getAwsDirectory().toString() + "config");
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
        return AwsFileHelper.getWriter(AwsFileHelper.getAwsDirectory().toString() + "config");
    }
}
