package com.okta.tools.helpers;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class FileHelper {

    private static final String USER_HOME = System.getProperty("user.home");
    private static final String USER_DIR = System.getProperty("user.dir");

    /**
     * Gets the path of a directory in the user home directory
     *
     * @param name The name of the directory
     * @param userHomeContext
     * @return The {@link Path} of the directory
     * @throws IOException
     */
    private static Path getDirectory(String name, Boolean userHomeContext) throws IOException {
        Path directory;

        if (userHomeContext) {
            directory = Paths.get(USER_HOME).resolve(name);
        } else {
            directory = Paths.get(USER_DIR).resolve(name);
        }

        if (!Files.exists(directory)) {
            Files.createDirectory(directory);
        } else if (!Files.isDirectory(directory)) {
            throw new IllegalStateException(directory + " exists, but is not a directory. Please rename it!");
        }

        return directory;
    }

    /**
     * Gets the path of the AWS directory (USER_HOME/.aws)
     *
     * @return The path of the AWS directory
     */
    public static Path getAwsDirectory() throws IOException {
        return getDirectory(".aws", true);
    }

    /**
     * Gets the path of the Okta directory within AWS (USER_HOME/.aws/.okta)
     *
     * @return The path of the Okta directory
     */
    public static Path getOktaDirectory() throws IOException {
        return getDirectory(".okta", true);
    }

    /**
     * Gets the path of the User provided directory
     *
     * @param name The name of the directory
     * @return The path of the User provided directory
     */
    public static Path getUserDirectory(String name) throws IOException {
        return getDirectory(name, false);
    }

    /**
     * Gets a reader for the given file. Creates a StringReader if the file is not found
     *
     * @param directoryPath The {@link Path} of the file's parent directory
     * @param fileName      The name of the file
     * @return The reader for the given file
     * @throws IOException
     */
    public static Reader getReader(Path directoryPath, String fileName) throws IOException {
        return new FileReader(getFilePath(directoryPath, fileName).toFile());
    }

    /**
     * Get a FileWriter for a given path
     *
     * @param directoryPath The {@link Path} of the file's parent directory
     * @param fileName      The name of the file
     * @return The FileReader for the given path
     * @throws IOException
     */
    public static FileWriter getWriter(Path directoryPath, String fileName) throws IOException {
        return new FileWriter(getFilePath(directoryPath, fileName).toFile());
    }

    /**
     * Gets the Path of a specified file
     *
     * @param directoryPath The {@link Path} of the file's parent directory
     * @param fileName      The name of the file
     * @return The Path of the file
     * @throws IOException
     */
    public static Path getFilePath(Path directoryPath, String fileName) throws IOException {
        Path filePath = directoryPath.resolve(fileName);

        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
        } else if (!Files.isRegularFile(filePath)) {
            throw new IllegalStateException(filePath + " exists, but is not a regular file. Please rename it!");
        }

        return filePath;
    }

    /**
     * Gets the Path of a specified file, without creating it
     *
     * @param directoryPath The {@link Path} of the file's parent directory
     * @param fileName      The name of the file
     * @return The Path of the file
     * @throws IOException
     */
    public static Path resolveFilePath(Path directoryPath, String fileName) throws IOException {
        return Paths.get(directoryPath.toString(), fileName);
    }
}
