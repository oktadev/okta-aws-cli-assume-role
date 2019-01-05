/*
 * Copyright 2018 Okta
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
package com.okta.tools.saml;

import com.okta.tools.OktaAwsCliEnvironment;
import com.okta.tools.authentication.BrowserAuthentication;
import com.okta.tools.authentication.OktaAuthentication;
import com.okta.tools.helpers.CookieHelper;
import com.okta.tools.helpers.HttpHelper;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class OktaSaml {

    private final OktaAwsCliEnvironment environment;
    private final CookieHelper cookieHelper;
    private final OktaAuthentication authentication;

    public OktaSaml(OktaAwsCliEnvironment environment, CookieHelper cookieHelper, OktaAuthentication oktaAuthentication) {
        this.environment = environment;
        this.cookieHelper = cookieHelper;
        this.authentication = oktaAuthentication;
    }

    public String getSamlResponse() throws IOException {
        if (environment.browserAuth) {
            try {
                return BrowserAuthentication.login(environment);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                return getSamlResponseForAwsRefresh();
            } catch (PromptForReAuthenticationException | PromptForFactorException e) {
                String oktaSessionToken = authentication.getOktaSessionToken();
                return getSamlResponseForAws(oktaSessionToken);
            }
        }
    }

    private String getSamlResponseForAws(String oktaSessionToken) throws IOException {
        Document document = launchOktaAwsAppWithSessionToken(environment.oktaAwsAppUrl, oktaSessionToken);
        return getSamlResponseForAwsFromDocument(document);
    }

    private String getSamlResponseForAwsRefresh() throws IOException {
        Document document = launchOktaAwsApp(environment.oktaAwsAppUrl);
        return getSamlResponseForAwsFromDocument(document);
    }

    private String getSamlResponseForAwsFromDocument(Document document) {
        Elements samlResponseInputElement = document.select("form input[name=SAMLResponse]");
        if (samlResponseInputElement.isEmpty()) {
            if (isPasswordAuthenticationChallenge(document)) {
                throw new PromptForReAuthenticationException("Unsupported App sign on rule: 'Prompt for re-authentication'. \nPlease contact your administrator.");
            } else if (isPromptForFactorChallenge(document)) {
                throw new PromptForFactorException("Unsupported App sign on rule: 'Prompt for factor'. \nPlease contact your administrator.");
            } else {
                Elements errorContent = document.getElementsByClass("error-content");
                Elements errorHeadline = errorContent.select("h1");
                if (errorHeadline.hasText()) {
                    throw new RuntimeException(errorHeadline.text());
                } else {
                    System.err.println(document.toString());
                    throw new RuntimeException("An unhandled error occurred. Please consult the server response above.");
                }
            }
        }
        return samlResponseInputElement.attr("value");
    }

    public static final class PromptForReAuthenticationException extends IllegalStateException {
        public PromptForReAuthenticationException(String message) {
            super(message);
        }
    }

    public static final class PromptForFactorException extends IllegalStateException {
        public PromptForFactorException(String message) {
            super(message);
        }
    }

    // Heuristic based on undocumented behavior observed experimentally
    // This condition may be missed if Okta significantly changes the app-level re-auth page
    private boolean isPasswordAuthenticationChallenge(Document document) {
        return document.getElementById("password-verification-challenge") != null;
    }

    // Heuristic based on undocumented behavior observed experimentally
    // This condition may be missed if Okta significantly changes the app-level MFA page
    private boolean isPromptForFactorChallenge(Document document) {
        return document.getElementById("okta-sign-in") != null;
    }

    private Document launchOktaAwsAppWithSessionToken(String appUrl, String oktaSessionToken) throws IOException {
        return launchOktaAwsApp(appUrl + "?onetimetoken=" + oktaSessionToken);
    }

    private Document launchOktaAwsApp(String appUrl) throws IOException {
        HttpGet httpget = new HttpGet(appUrl);
        CookieStore cookieStore = cookieHelper.loadCookies();

        try (CloseableHttpClient httpClient = HttpHelper.createClient(HttpClients.custom().setDefaultCookieStore(cookieStore));
             CloseableHttpResponse oktaAwsAppResponse = httpClient.execute(httpget)) {

            if (oktaAwsAppResponse.getStatusLine().getStatusCode() >= 500) {
                throw new RuntimeException("Server error when loading Okta AWS App: "
                        + oktaAwsAppResponse.getStatusLine().getStatusCode());
            } else if (oktaAwsAppResponse.getStatusLine().getStatusCode() >= 400) {
                throw new RuntimeException("Client error when loading Okta AWS App: "
                        + oktaAwsAppResponse.getStatusLine().getStatusCode());
            }

            cookieHelper.storeCookies(cookieStore);

            return Jsoup.parse(
                    oktaAwsAppResponse.getEntity().getContent(),
                    StandardCharsets.UTF_8.name(),
                    appUrl
            );
        }
    }
}
