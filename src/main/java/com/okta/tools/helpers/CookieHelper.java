package com.okta.tools.helpers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CookieHelper {

    public static Path getCookies() throws IOException {
        Path filePath = FileHelper.getFilePath(FileHelper.getOktaDirectory().toString(), "cookies.properties");

        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
        }

        return filePath;
    }
}
