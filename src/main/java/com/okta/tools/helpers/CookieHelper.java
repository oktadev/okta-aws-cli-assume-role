package com.okta.tools.helpers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CookieHelper {

    /**
     * Get the path for the cookies file
     *
     * @return A {@link Path} to the cookies.properties file
     * @throws IOException
     */
    public static Path getCookies() throws IOException {
        Path filePath = FileHelper.getFilePath(FileHelper.getOktaDirectory(), "cookies.properties");

        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
        }

        return filePath;
    }
}
