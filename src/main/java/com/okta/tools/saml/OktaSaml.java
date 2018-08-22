package com.okta.tools.saml;

import com.okta.tools.authentication.BrowserAuthentication;
import com.okta.tools.OktaAwsCliEnvironment;
import com.okta.tools.authentication.OktaAuthentication;
import com.okta.tools.helpers.CookieHelper;
import org.apache.http.HttpStatus;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class OktaSaml {

    private final OktaAwsCliEnvironment environment;
    private final CookieHelper cookieHelper;

    public OktaSaml(OktaAwsCliEnvironment environment, CookieHelper cookieHelper) {
        this.environment = environment;
        this.cookieHelper = cookieHelper;
    }

    public String getSamlResponse() throws IOException {
        OktaAuthentication authentication = new OktaAuthentication(environment);

        if (reuseSession()) {
            return getSamlResponseForAwsRefresh();
        } else if (environment.browserAuth) {
            try {
                return BrowserAuthentication.login(environment);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            String oktaSessionToken = authentication.getOktaSessionToken();
            return getSamlResponseForAws(oktaSessionToken);
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
                throw new IllegalStateException("Unsupported App sign on rule: 'Prompt for re-authentication'. \nPlease contact your administrator.");
            } else if (isPromptForFactorChallenge(document)) {
                throw new IllegalStateException("Unsupported App sign on rule: 'Prompt for factor'. \nPlease contact your administrator.");
            } else {
                Elements errorContent = document.getElementsByClass("error-content");
                Elements errorHeadline = errorContent.select("h1");
                if (errorHeadline.hasText()) {
                    throw new RuntimeException(errorHeadline.text());
                } else {
                    throw new RuntimeException("You do not have access to AWS through Okta. \nPlease contact your administrator.");
                }
            }
        }
        return samlResponseInputElement.attr("value");
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

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultCookieStore(cookieStore).useSystemProperties().build();
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

    private boolean reuseSession() throws JSONException, IOException {
        CookieStore cookieStore = cookieHelper.loadCookies();

        Optional<String> sidCookie = cookieStore.getCookies().stream().filter(cookie -> "sid".equals(cookie.getName())).findFirst().map(Cookie::getValue);

        if (!sidCookie.isPresent()) {
            return false;
        }

        HttpPost httpPost = new HttpPost("https://" + environment.oktaOrg + "/api/v1/sessions/me/lifecycle/refresh");
        httpPost.addHeader("Accept", "application/json");
        httpPost.addHeader("Content-Type", "application/json");
        httpPost.addHeader("Cookie", "sid=" + sidCookie.get());

        try (CloseableHttpClient httpClient = HttpClients.createSystem()) {
            CloseableHttpResponse authnResponse = httpClient.execute(httpPost);

            return authnResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
        }
    }

}
