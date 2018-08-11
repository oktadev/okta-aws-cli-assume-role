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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public final class CookieHelper {

    private final OktaAwsCliEnvironment environment;

    public CookieHelper(OktaAwsCliEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Get the path for the cookies file
     *
     * @return A {@link Path} to the cookies.properties file
     * @throws IOException if the cookies file cannot be loaded or created
     */
    private Path getCookiesFilePath() throws IOException {
        Path filePath;

        if (environment.oktaCookiesPath == null) {
            filePath = FileHelper.getFilePath(FileHelper.getOktaDirectory(), "cookies.properties");
        } else {
            filePath = FileHelper.getFilePath(
                    FileHelper.getUserDirectory(environment.oktaCookiesPath), "cookies.properties"
            );
        }

        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
        }

        return filePath;
    }

    public CookieStore parseCookies(URI uri, List<String> cookieHeaders) {
        CookieStore cookieStore = new BasicCookieStore();
        for (String cookieHeader : cookieHeaders) {
            for (String cookie : Splitter.on(";").trimResults().omitEmptyStrings().split(cookieHeader)) {
                int indexOfEquals = cookie.indexOf('=');
                String name = cookie.substring(0, indexOfEquals);
                String value = cookie.substring(indexOfEquals + 1);
                BasicClientCookie clientCookie = new BasicClientCookie(name, value);
                clientCookie.setDomain(uri.getHost());
                cookieStore.addCookie(clientCookie);
            }
        }
        return cookieStore;
    }

    public CookieStore loadCookies() throws IOException {
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

    public void storeCookies(CookieStore cookieStore) throws IOException {
        Properties properties = new Properties();
        cookieStore.getCookies().stream()
                .filter(c -> environment.oktaOrg.equals(c.getDomain()))
                .collect(Collectors.toMap(Cookie::getName, Cookie::getValue, (x, y) -> y))
                .forEach(properties::setProperty);
        properties.store(new FileWriter(getCookiesFilePath().toFile()), "");
    }

    void clearCookies() throws IOException {
        File cookieStore = getCookiesFilePath().toFile();
        cookieStore.deleteOnExit();
    }
}
