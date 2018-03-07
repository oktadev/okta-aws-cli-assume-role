package com.okta.tools.helpers;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class FileHelper {
    private static final String USER_HOME = System.getProperty("user.home");

    /**
     * Gets a directory with the given path, creating it if it doesn't exist
     *
     * @param path The path of the directory to be returned
     * @return The path of the directory
     * @author Andrei Hava
     * @since 02/14/2018
     */
    private static Path getDirectory(String path) {
        File dir = new File(path);
        dir.mkdir();

        return dir.toPath();
    }

    /**
     * Gets the path of the AWS directory (USER_HOME/.aws)
     *
     * @return The path of the AWS directory
     * @author Andrei Hava
     * @since 02/14/2018
     */
    public static Path getAwsDirectory() {
        return getDirectory(USER_HOME + "/.aws/");
    }

    /**
     * Gets the path of the Okta directory within AWS (USER_HOME/.aws/.okta)
     *
     * @return The path of the Okta directory
     * @author Andrei Hava
     * @since 03/02/2018
     */
    public static Path getOktaDirectory() {
        return getDirectory(getAwsDirectory().toString() + "/.okta/");
    }

    /**
     * Gets a reader for the given file. Creates a StringReader if the file is not found
     *
     * @param path The path of the file to get the reader for
     * @return The reader for the given file
     * @throws FileNotFoundException
     * @author Andrei Hava
     * @since 02/14/2018
     */
    public static Reader getReader(String path) throws FileNotFoundException {
        String configPath = path;
        if (!new File(configPath).isFile()) {
            return new StringReader("");
        }
        return new FileReader(configPath);
    }

    /**
     * Get a FileWriter for a given path
     *
     * @param path The path for the file
     * @return The FileReader for the given path
     * @throws IOException
     * @author Andrei Hava
     * @since 02/14/2018
     */
    public static FileWriter getWriter(String path) throws IOException {
        String configPath = path;

        return new FileWriter(configPath);
    }

    /**
     * Gets the Path of a specified file
     *
     * @param parentDirectory The parent directory path of the file
     * @param fileName        The file name
     * @return The Path of the file
     */
    public static Path getFilePath(String parentDirectory, String fileName) {
        return Paths.get(parentDirectory, fileName);
    }
}
