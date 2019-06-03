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

import com.okta.tools.OktaAwsCliEnvironment;
import com.okta.tools.authentication.BrowserAuthentication;
import com.okta.tools.authentication.OktaAuthentication;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.logging.Logger;

public class OktaSaml {
    private static final Logger LOGGER = Logger.getLogger(OktaSaml.class.getName());

    private final OktaAwsCliEnvironment environment;
    private final OktaAuthentication authentication;
    private final OktaAppClient oktaAppClient;

    public OktaSaml(OktaAwsCliEnvironment environment, OktaAuthentication oktaAuthentication, OktaAppClient oktaAppClient) {
        this.environment = environment;
        this.authentication = oktaAuthentication;
        this.oktaAppClient = oktaAppClient;
    }

    public String getSamlResponse() throws IOException, InterruptedException {
        if (environment.browserAuth) {
            return BrowserAuthentication.login(environment);
        } else {
            try {
                return getSamlResponseForAwsRefresh();
            } catch (PromptForReAuthenticationException | PromptForFactorException | PromptForCredentialsException e) {
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
        Document document = oktaAppClient.launchApp(environment.oktaAwsAppUrl);
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
                    throw new IllegalStateException(errorHeadline.text());
                } else {
                    LOGGER.fine(document::toString);
                    throw new IllegalStateException("An unhandled error occurred. Please consult the server response above.");
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

    public static final class PromptForCredentialsException extends IllegalStateException {
        public PromptForCredentialsException(String message) {
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
        return oktaAppClient.launchApp(appUrl + "?onetimetoken=" + oktaSessionToken);
    }
}
