package com.okta.tools.saml;

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
    public static String getSamlResponseForAws(String appUrl, String oktaSessionToken) throws IOException {
        Document document = launchOktaAwsApp(appUrl, oktaSessionToken);
        Elements samlResponseInputElement = document.select("form input[name=SAMLResponse]");
        if (samlResponseInputElement.isEmpty()) {
            throw new RuntimeException("You do not have access to AWS through Okta. \nPlease contact your administrator.");
        }
        return samlResponseInputElement.attr("value");
    }

    private static Document launchOktaAwsApp(String appUrl, String oktaSessionToken) throws IOException {

        HttpGet httpget = new HttpGet(appUrl + "?onetimetoken=" + oktaSessionToken);
        try (CloseableHttpClient httpClient = HttpClients.createSystem();
             CloseableHttpResponse responseSAML = httpClient.execute(httpget)) {

            if (responseSAML.getStatusLine().getStatusCode() >= 500) {
                throw new RuntimeException("Server error when loading Okta AWS App: "
                        + responseSAML.getStatusLine().getStatusCode());
            } else if (responseSAML.getStatusLine().getStatusCode() >= 400) {
                throw new RuntimeException("Client error when loading Okta AWS App: "
                        + responseSAML.getStatusLine().getStatusCode());
            }

            return Jsoup.parse(
                    responseSAML.getEntity().getContent(),
                    StandardCharsets.UTF_8.name(),
                    appUrl
            );
        }
    }
}
