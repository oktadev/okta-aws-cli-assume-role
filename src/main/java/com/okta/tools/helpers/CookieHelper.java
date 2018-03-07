package com.okta.tools.helpers;

import java.io.IOException;
import java.nio.file.Path;

public class CookieHelper {

    public static Path getCookies() throws IOException {
        return FileHelper.getFilePath(FileHelper.getOktaDirectory().toString(), "cookies.properties");
    }
}
