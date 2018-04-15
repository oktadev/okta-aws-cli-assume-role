package com.okta.tools;

import javafx.application.Application;
import javafx.concurrent.Worker.State;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.apache.commons.lang.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class BrowserAuthentication extends Application {
    private static final String CONFIG_FILENAME = "config.properties";

    @Override
    public void start(final Stage stage) throws IOException {
        stage.setWidth(802);
        stage.setHeight(650);
        Scene scene = new Scene(new Group());


        final WebView browser = new WebView();
        final WebEngine webEngine = browser.getEngine();

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(browser);
        Properties properties = new Properties();
        getConfigFile().ifPresent(configFile -> {
            try (InputStream config = new FileInputStream(configFile.toFile())) {
                properties.load(new InputStreamReader(config));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        URI uri = URI.create(getEnvOrConfig(properties, "OKTA_AWS_APP_URL"));
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
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            } finally {
                                stage.close();
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

    private static Optional<Path> getConfigFile() {
        Path configInWorkingDir = Paths.get(CONFIG_FILENAME);
        if (Files.isRegularFile(configInWorkingDir)) {
            return Optional.of(configInWorkingDir);
        }
        Path userHome = Paths.get(System.getProperty("user.home"));
        Path oktaDir = userHome.resolve(".okta");
        Path configInOktaDir = oktaDir.resolve(CONFIG_FILENAME);
        if (Files.isRegularFile(configInOktaDir)) {
            return Optional.of(configInOktaDir);
        }
        return Optional.empty();
    }

    private static String getEnvOrConfig(Properties properties, String propertyName) {
        String envValue = System.getenv(propertyName);
        return envValue != null ?
                envValue : properties.getProperty(propertyName);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
