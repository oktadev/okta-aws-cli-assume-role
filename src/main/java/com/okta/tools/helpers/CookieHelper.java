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

import com.google.common.collect.Iterables;
import com.okta.tools.OktaAwsCliEnvironment;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.RFC6265StrictSpec;
import org.apache.http.message.BasicHeader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CookieHelper {

    private static final String SET_COOKIE_HEADER_NAME = "Set-Cookie";
    private final OktaAwsCliEnvironment environment;
    private Map<String, String> cookieHeaders = new LinkedHashMap<>();

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

        if (!filePath.toFile().exists()) {
            Files.createFile(filePath);
        }

        return filePath;
    }

    public CookieStore loadCookies() throws IOException {
        Map<String, List<String>> multiValueCookieHeaders = loadCookieHeaders();
        List<String> setCookies = multiValueCookieHeaders.getOrDefault(SET_COOKIE_HEADER_NAME, Collections.emptyList());
        CookieStore cookieStore = new BasicCookieStore();
        for (String setCookie : setCookies) {
            Header header = new BasicHeader(SET_COOKIE_HEADER_NAME, setCookie);
            CookieOrigin cookieOrigin = new CookieOrigin(environment.oktaOrg, 443, "", false);
            try {
                List<Cookie> cookies = new RFC6265StrictSpec().parse(header, cookieOrigin);
                cookies.forEach(cookieStore::addCookie);
            } catch (MalformedCookieException e) {
                throw new IllegalStateException(e);
            }
        }
        return cookieStore;
    }

    public void storeCookies(CookieStore cookieStore) throws IOException {
        List<Header> headers = new RFC6265StrictSpec().formatCookies(cookieStore.getCookies());
        List<String> cookies = headers.stream()
                .flatMap(header -> Stream.of(header.getElements()))
                .flatMap(headerElement -> Stream.of(headerElement.getParameters()))
                .map(NameValuePair::toString).collect(Collectors.toList());
        Files.write(getCookiesFilePath(), cookies, StandardCharsets.UTF_8);
    }

    void clearCookies() throws IOException {
        File cookieStore = getCookiesFilePath().toFile();
        cookieStore.deleteOnExit();
    }

    public Map<String, List<String>> loadCookieHeaders() throws IOException {
        List<String> cookiesFileLines = Files.readAllLines(getCookiesFilePath());
        this.cookieHeaders = getCookieHeaders(cookiesFileLines)
                .collect(Collectors.toMap(
                        cookie -> cookie.substring(0, cookie.indexOf('=')),
                        Function.identity(),
                        (u,v) -> v,
                        LinkedHashMap::new
                ));
        return Collections.singletonMap(SET_COOKIE_HEADER_NAME,
                getCookieHeaders(cookiesFileLines).collect(Collectors.toList()));
    }

    private Stream<String> getCookieHeaders(List<String> cookiesFileLines) {
        return cookiesFileLines.stream()
                    .filter(line -> !line.trim().isEmpty() && !isComment(line));
    }

    private boolean isComment(String cookie) {
        return cookie.startsWith("#");
    }

    public void storeCookies(Map<String, List<String>> responseHeaders) throws IOException {
        Iterables.concat(
                responseHeaders.getOrDefault(SET_COOKIE_HEADER_NAME.toLowerCase(), Collections.emptyList()),
                responseHeaders.getOrDefault(SET_COOKIE_HEADER_NAME, Collections.emptyList()))
                .forEach(cookie ->
                cookieHeaders.put(cookie.substring(0, cookie.indexOf('=')), cookie)
        );
        Files.write(
                getCookiesFilePath(),
                cookieHeaders.values(),
                StandardCharsets.UTF_8
        );
    }
}
