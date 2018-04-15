package com.okta.tools;

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

public class BrowserAuthentication extends Application {
    // FIXME: this is awful
    private static final CountDownLatch LATCH = new CountDownLatch(1);
    // FIXME: this is worse
    private static OktaAwsCliEnvironment ENVIRONMENT;

    public static void login(OktaAwsCliEnvironment environment) throws InterruptedException {
        ENVIRONMENT = environment;
        launch();
        LATCH.await();
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
                            String samlResponse = webEngine.getDocument().getElementsByTagName("input").item(0)
                                    .getAttributes().getNamedItem("value").getTextContent();
                            System.out.println(samlResponse);
                            try {
                                String cookie = CookieHandler.getDefault().get(uri, headers).get("Cookie").get(0);
                                String sid = StringUtils.substringBefore(
                                        StringUtils.substringAfter(cookie, "sid="),
                                        ";"
                                );
                                System.out.println(sid);
                                Properties properties = new Properties();
                                properties.setProperty("sid", sid);
                                properties.store(new FileWriter(CookieHelper.getCookies().toFile()), "");
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            } finally {
                                stage.close();
                                LATCH.countDown();
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

    public static void main(String[] args) {
        launch(args);
    }
}
