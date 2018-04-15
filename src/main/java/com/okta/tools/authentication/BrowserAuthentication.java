package com.okta.tools.authentication;

import com.okta.tools.OktaAwsCliEnvironment;
import com.okta.tools.helpers.CookieHelper;
import javafx.application.Application;
import javafx.concurrent.Worker.State;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.net.CookieHandler;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class BrowserAuthentication extends Application {
    // Trade-off: JavaFX app model makes interacting with UI state challenging
    // Experienced JavaFX devs welcomed to suggest solutions to this
    private static final CountDownLatch USER_AUTH_COMPLETE = new CountDownLatch(1);

    // Trade-off: JavaFX app model makes passing parameters to UI challenging
    // Experienced JavaFX devs welcomed to suggest solutions to this
    private static OktaAwsCliEnvironment ENVIRONMENT;

    // The value of samlResponse is only valid if USER_AUTH_COMPLETE has counted down to zero
    private static final AtomicReference<String> samlResponse = new AtomicReference<>();

    public static String login(OktaAwsCliEnvironment environment) throws InterruptedException {
        ENVIRONMENT = environment;
        launch();
        USER_AUTH_COMPLETE.await();
        return samlResponse.get();
    }

    @Override
    public void start(final Stage stage) throws IOException {
        stage.setWidth(802);
        stage.setHeight(650);
        Scene scene = new Scene(new Group());


        final WebView browser = new WebView();
        final WebEngine webEngine = browser.getEngine();

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(browser);

        URI uri = URI.create(ENVIRONMENT.oktaAwsAppUrl);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        java.net.CookieHandler.getDefault().put(uri, headers);

        webEngine.getLoadWorker().stateProperty()
                .addListener((ov, oldState, newState) -> {
                    if (newState == State.SUCCEEDED) {
                        if (webEngine.getLocation().endsWith("/sso/saml")) {
                            samlResponse.set(webEngine.getDocument().getElementsByTagName("input").item(0)
                                    .getAttributes().getNamedItem("value").getTextContent());
                            try {
                                String cookie = CookieHandler.getDefault().get(uri, headers).get("Cookie").get(0);
                                String sid = StringUtils.substringBefore(
                                        StringUtils.substringAfter(cookie, "sid="),
                                        ";"
                                );
                                Properties properties = new Properties();
                                properties.setProperty("sid", sid);
                                properties.store(new FileWriter(CookieHelper.getCookies().toFile()), "");
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            } finally {
                                stage.close();
                                USER_AUTH_COMPLETE.countDown();
                            }
                        }
                        stage.setTitle(webEngine.getLocation());
                    }
                });
        webEngine.load(uri.toASCIIString());

        scene.setRoot(scrollPane);

        stage.setScene(scene);
        stage.show();
    }
}
