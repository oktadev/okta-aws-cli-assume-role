package com.okta.tools.helpers;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;

public final class AwsCredentialsHelper {
    /**
     * Gets a reader for the credentials file. If the file doesn't exist, it creates it
     * @author Andrei Hava
     * @since 02/14/2018
     * @return The file reader for the credentials file
     * @throws FileNotFoundException
     */
    public static Reader getCredsReader() throws FileNotFoundException
    {
        return AwsFileHelper.getReader(AwsFileHelper.getAwsDirectory().toString() + "/credentials");
    }

    /**
     * Gets a FileWriter for the credentials file
     * @author Andrei Hava
     * @since 02/14/2018
     * @return The FileWriter for the credentials file
     * @throws IOException
     */
    public static FileWriter getCredsWriter() throws IOException
    {
        return AwsFileHelper.getWriter(AwsFileHelper.getAwsDirectory().toString() + "/credentials");
    }
}
