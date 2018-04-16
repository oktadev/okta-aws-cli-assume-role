package com.okta.tools.helpers;

import com.google.common.base.Splitter;
import com.okta.tools.OktaAwsCliEnvironment;
import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public final class CookieHelper {

    /**
     * Get the path for the cookies file
     *
     * @return A {@link Path} to the cookies.properties file
     * @throws IOException
     */
    private static Path getCookiesFilePath() throws IOException {
        Path filePath = FileHelper.getFilePath(FileHelper.getOktaDirectory(), "cookies.properties");

        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
        }

        return filePath;
    }

    public static CookieStore parseCookies(List<String> cookieHeaders) {
        CookieStore cookieStore = new BasicCookieStore();
        for (String cookieHeader : cookieHeaders) {
            for (String cookie : Splitter.on(";").trimResults().omitEmptyStrings().split(cookieHeader)) {
                int indexOfEquals = cookie.indexOf('=');
                String name = cookie.substring(0, indexOfEquals);
                String value = cookie.substring(indexOfEquals + 1);
                cookieStore.addCookie(new BasicClientCookie(name, value));
            }
        }
        return cookieStore;
    }

    public static CookieStore loadCookies(OktaAwsCliEnvironment environment) throws IOException {
        CookieStore cookieStore = new BasicCookieStore();
        Properties loadedProperties = new Properties();
        loadedProperties.load(new FileReader(getCookiesFilePath().toFile()));
        loadedProperties.entrySet().stream().map(entry -> {
            BasicClientCookie basicClientCookie = new BasicClientCookie(entry.getKey().toString(), entry.getValue().toString());
            basicClientCookie.setDomain(environment.oktaOrg);

            return basicClientCookie;
        }).forEach(cookieStore::addCookie);
        return cookieStore;
    }

    public static void storeCookies(CookieStore cookieStore) throws IOException {
        Properties properties = new Properties();
        cookieStore.getCookies().stream().collect(Collectors.toMap(Cookie::getName, Cookie::getValue))
                .forEach(properties::setProperty);
        properties.store(new FileWriter(getCookiesFilePath().toFile()), "");
    }

    static void clearCookies() throws IOException {
        File cookieStore = getCookiesFilePath().toFile();
        cookieStore.deleteOnExit();
    }
}
