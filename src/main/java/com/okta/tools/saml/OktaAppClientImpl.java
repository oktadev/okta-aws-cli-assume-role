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
package com.okta.tools.saml;

import com.okta.tools.helpers.CookieHelper;
import com.okta.tools.helpers.HttpHelper;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class OktaAppClientImpl implements OktaAppClient {
    private final CookieHelper cookieHelper;

    public OktaAppClientImpl(CookieHelper cookieHelper) {
        this.cookieHelper = cookieHelper;
    }

    @Override
    public Document launchApp(String appUrl) throws IOException {
        HttpGet httpget = new HttpGet(appUrl);
        CookieStore cookieStore = cookieHelper.loadCookies();

        try (CloseableHttpClient httpClient = HttpHelper.createClient(HttpClients.custom().setDefaultCookieStore(cookieStore));
             CloseableHttpResponse oktaAwsAppResponse = httpClient.execute(httpget)) {

            if (oktaAwsAppResponse.getStatusLine().getStatusCode() >= 500) {
                throw new IllegalStateException("Server error when loading Okta AWS App: "
                        + oktaAwsAppResponse.getStatusLine().getStatusCode());
            } else if (oktaAwsAppResponse.getStatusLine().getStatusCode() >= 400) {
                throw new IllegalStateException("Client error when loading Okta AWS App: "
                        + oktaAwsAppResponse.getStatusLine().getStatusCode());
            }

            // Fix: previous logic was assuming that always was refreshing a previous session and cookies were
            // in place.
            // This condition throws an exception to trigger authentication with user credentials if no
            // cookies from a previous session was present to avoid crash when the tool is installed fresh
            // and no previous session cookies are present
            if(cookieStore.getCookies().isEmpty()) {
                throw new OktaSaml.PromptForCredentialsException("No cookies found, need to create a new okta session");
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
