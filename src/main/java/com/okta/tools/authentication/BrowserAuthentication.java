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
package com.okta.tools.authentication;

import com.okta.tools.OktaAwsCliEnvironment;
import com.okta.tools.helpers.CookieHelper;
import com.okta.tools.io.SubresourceIntegrityStrippingHack;
import com.okta.tools.util.NodeListIterable;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class BrowserAuthentication extends Application {
    private static final Logger LOGGER = Logger.getLogger(BrowserAuthentication.class.getName());

    // Trade-off: JavaFX app model makes interacting with UI state challenging
    // Experienced JavaFX devs welcomed to suggest solutions to this
    private static final CountDownLatch USER_AUTH_COMPLETE = new CountDownLatch(1);

    // Trade-off: JavaFX app model makes passing parameters to UI challenging
    // Experienced JavaFX devs welcomed to suggest solutions to this
    private static OktaAwsCliEnvironment environment;
    private static CookieHelper cookieHelper;

    // The value of samlResponse is only valid if USER_AUTH_COMPLETE has counted down to zero
    private static final AtomicReference<String> samlResponse = new AtomicReference<>();

    public static String login(OktaAwsCliEnvironment environment) throws InterruptedException {
        BrowserAuthentication.environment = environment;
        cookieHelper = new CookieHelper(BrowserAuthentication.environment);
        launch();
        USER_AUTH_COMPLETE.await();
        return samlResponse.get();
    }

    @Override
    public void start(final Stage stage) throws IOException {
        stage.setWidth(802);
        stage.setHeight(650);
        stage.setOnCloseRequest(event -> System.exit(1));
        Scene scene = new Scene(new Group());

        final WebView browser = new WebView();
        final WebEngine webEngine = browser.getEngine();

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(browser);

        URI uri = URI.create(environment.oktaAwsAppUrl);
        initializeCookies(uri);

        SubresourceIntegrityStrippingHack.overrideHttpsProtocolHandler(environment);
        webEngine.getLoadWorker().stateProperty()
                .addListener((ov, oldState, newState) -> {
                    if (webEngine.getDocument() != null) {
                        checkForAwsSamlSignon(stage, webEngine);
                        stage.setTitle(webEngine.getLocation());
                    }
                });

        webEngine.getLoadWorker().exceptionProperty()
            .addListener((observable, oldValue, newValue) ->
                LOGGER.severe(() -> String.format("exception(%s => %s)%n%s%n", oldValue, newValue, webEngine.getLoadWorker().getException()))
            );

        webEngine.load(uri.toASCIIString());

        scene.setRoot(scrollPane);

        stage.setScene(scene);
        stage.show();
    }

    private void initializeCookies(URI uri) throws IOException {
        Map<String, List<String>> headers = cookieHelper.loadCookieHeaders();
        java.net.CookieHandler.setDefault(new CookieManager(cookieHelper));
        java.net.CookieHandler.getDefault().put(uri, headers);
    }

    private void checkForAwsSamlSignon(Stage stage, WebEngine webEngine) {
        String samlResponseForAws = getSamlResponseForAws(webEngine.getDocument());
        if (samlResponseForAws != null) {
            finishAuthentication(stage, samlResponseForAws);
        }
    }

    private String getSamlResponseForAws(Document document) {
        Node awsStsSamlForm = getAwsStsSamlForm(document);
        if (awsStsSamlForm == null) return null;
        return getSamlResponseFromForm(awsStsSamlForm);
    }

    private Node getAwsStsSamlForm(Document document) {
        NodeList formNodes = document.getElementsByTagName("form");
        for (Node form : new NodeListIterable(formNodes)) {
            NamedNodeMap formAttributes = form.getAttributes();
            if (formAttributes != null) {
                Node formActionAttribute = formAttributes.getNamedItem("action");
                if (formActionAttribute != null) {
                    String formAction = formActionAttribute.getTextContent();
                    if (formAction.endsWith("/saml")) {
                        return form;
                    }
                }
            }
        }
        return null;
    }

    private String getSamlResponseFromForm(@Nonnull Node awsStsSamlForm) {
        Node samlResponseInput = getSamlResponseInput(awsStsSamlForm);
        if (samlResponseInput == null)
            throw new IllegalStateException("Request to AWS STS SAML endpoint missing SAMLResponse");
        NamedNodeMap attributes = samlResponseInput.getAttributes();
        Node value = attributes.getNamedItem("value");
        return value.getTextContent();
    }

    private Node getSamlResponseInput(@Nonnull Node parent) {
        for (Node child : new NodeListIterable(parent.getChildNodes())) {
            if (isSamlResponseInput(child)) {
                return child;
            } else {
                Node samlResponseInput = getSamlResponseInput(child);
                if (samlResponseInput != null) return samlResponseInput;
            }
        }
        return null;
    }

    private boolean isSamlResponseInput(@Nonnull Node child) {
        boolean isInput = "input".equals(child.getLocalName());
        if (!isInput) return false;
        NamedNodeMap attributes = child.getAttributes();
        if (attributes == null) return false;
        Node nameAttribute = attributes.getNamedItem("name");
        if (nameAttribute == null) return false;
        String name = nameAttribute.getTextContent();
        return "SAMLResponse".equals(name);
    }

    private void finishAuthentication(Stage stage, String samlResponseForAws) {
        samlResponse.set(samlResponseForAws);
        stage.close();
        USER_AUTH_COMPLETE.countDown();
    }
}
