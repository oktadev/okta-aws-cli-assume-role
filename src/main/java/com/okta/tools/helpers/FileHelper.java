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
package com.okta.tools.helpers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class FileHelper {

    private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));
    private static final Path USER_DIR = Paths.get(System.getProperty("user.dir"));

    /**
     * Gets the path of a directory in the user home directory
     *
     * @param name The name of the directory
     * @param dirContext base directory to load relative to
     * @return The {@link Path} of the directory
     * @throws IOException if a file system or permissions error is encountered
     */
    private static Path getDirectory(String name, Path dirContext) throws IOException {
        Path directory = dirContext.resolve(name);

        if (!directory.toFile().exists()) {
            Files.createDirectory(directory);
        } else if (!directory.toFile().isDirectory()) {
            throw new IllegalStateException(directory + " exists, but is not a directory. Please rename it!");
        }

        return directory;
    }

    /**
     * Gets the path of the AWS directory (USER_HOME/.aws)
     *
     * @return The path of the AWS directory
     */
    static Path getAwsDirectory() throws IOException {
        return getDirectory(".aws", USER_HOME);
    }

    /**
     * Gets the path of the Okta directory within AWS (USER_HOME/.aws/.okta)
     *
     * @return The path of the Okta directory
     */
    static Path getOktaDirectory() throws IOException {
        return getDirectory(".okta", USER_HOME);
    }

    /**
     * Gets the path of the User provided directory
     *
     * @param name The name of the directory
     * @return The path of the User provided directory
     */
    static Path getUserDirectory(String name) throws IOException {
        return getDirectory(name, USER_DIR);
    }

    private static Reader getReader(Path path) throws IOException {
        return getReader(path.getParent(), path.toFile().getName());
    }

    /**
     * Gets a reader for the given file. Creates a StringReader if the file is not found
     *
     * @param directoryPath The {@link Path} of the file's parent directory
     * @param fileName      The name of the file
     * @return The reader for the given file
     * @throws IOException if a file system or permissions error is encountered
     */
    private static Reader getReader(Path directoryPath, String fileName) throws IOException {
        return new FileReader(getFilePath(directoryPath, fileName).toFile());
    }

    private static Writer getWriter(Path path) throws IOException {
        return getWriter(path.getParent(), path.toFile().getName());
    }

    /**
     * Get a FileWriter for a given path
     *
     * @param directoryPath The {@link Path} of the file's parent directory
     * @param fileName      The name of the file
     * @return The FileReader for the given path
     * @throws IOException if a file system or permissions error is encountered
     */
    private static FileWriter getWriter(Path directoryPath, String fileName) throws IOException {
        return new FileWriter(getFilePath(directoryPath, fileName).toFile());
    }

    /**
     * Gets the Path of a specified file
     *
     * @param directoryPath The {@link Path} of the file's parent directory
     * @param fileName      The name of the file
     * @return The Path of the file
     * @throws IOException if a file system or permissions error is encountered
     */
    static Path getFilePath(Path directoryPath, String fileName) throws IOException {
        Path filePath = directoryPath.resolve(fileName);

        if (!filePath.toFile().exists()) {
            Files.createFile(filePath);
        } else if (!filePath.toFile().isFile()) {
            throw new IllegalStateException(filePath + " exists, but is not a regular file. Please rename it!");
        }

        return filePath;
    }

    static <T> void usingPath(Path path, PathR<T> pathR, PathTW<T> pathTW) throws IOException {
        T t;
        try (Reader reader = getReader(path)) {
            t = pathR.useFile(reader);
        }
        try (Writer writer = getWriter(path)) {
            pathTW.useFile(t, writer);
        }
    }

    public interface PathTW<T> {
        void useFile(T t, Writer writer) throws IOException;
    }

    static <T> T readingPath(Path path, PathR<T> pathR) throws IOException {
        try (Reader reader = getReader(path)) {
            return pathR.useFile(reader);
        }
    }

    public interface PathR<T> {
        T useFile(Reader reader) throws IOException;
    }

    static void writingPath(Path path, PathW pathW) throws IOException {
        try (Writer writer = getWriter(path)) {
            pathW.useFile(writer);
        }
    }

    public interface PathW {
        void useFile(Writer writer) throws IOException;
    }
}
