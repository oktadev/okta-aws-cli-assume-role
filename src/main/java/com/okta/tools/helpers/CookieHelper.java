package com.okta.tools.helpers;

import com.okta.tools.OktaAwsCliEnvironment;
import org.apache.http.client.CookieStore;
import org.apache.http.cookie.*;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.cookie.DefaultCookieSpec;
import org.apache.http.message.BasicHeader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
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

    // SOURCE: https://stackoverflow.com/a/13104873/154527
    // SOURCE: https://github.com/droidparts/droidparts/blob/master/droidparts/src/org/droidparts/net/http/CookieJar.java
    public static CookieStore parseCookies(URI uri, List<String> cookieHeaders) {
        CookieSpec cookieSpec = new DefaultCookieSpec();
        CookieStore cookieStore = new BasicCookieStore();
        int port = (uri.getPort() < 0) ? 80 : uri.getPort();
        boolean secure = "https".equals(uri.getScheme());
        CookieOrigin origin = new CookieOrigin(uri.getHost(), port,
                uri.getPath(), secure);
        for (String cookieHeader : cookieHeaders) {
            BasicHeader header = new BasicHeader(SM.SET_COOKIE, cookieHeader);
            try {
                cookieSpec.parse(header, origin).forEach(cookieStore::addCookie);
            } catch (MalformedCookieException e) {
                throw new RuntimeException(e);
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
