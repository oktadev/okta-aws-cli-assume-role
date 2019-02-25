package com.okta.tools.io;

import com.okta.tools.OktaAwsCliEnvironment;

import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.logging.Logger;

final class LoginPageInterceptingProtocolHandler extends sun.net.www.protocol.https.Handler {
    private static final Logger LOGGER = Logger.getLogger(LoginPageInterceptingProtocolHandler.class.getName());
    private final OktaAwsCliEnvironment environment;
    private final BiFunction<URL, URLConnection, URLConnection> filteringUrlConnectionFactory;

    LoginPageInterceptingProtocolHandler(OktaAwsCliEnvironment environment, BiFunction<URL, URLConnection, URLConnection> filteringUrlConnectionFactory) {
        this.environment = environment;
        this.filteringUrlConnectionFactory = filteringUrlConnectionFactory;
    }

    @Override
    protected URLConnection openConnection(URL url, Proxy proxy) throws IOException {
        URLConnection urlConnection = super.openConnection(url, proxy);
        if (environment.oktaOrg.equals(url.getHost()) &&
            Arrays.asList(
                    URI.create(environment.oktaAwsAppUrl).getPath(),
                    "/login/login.htm",
                    "/auth/services/devicefingerprint"
            ).contains(url.getPath())
        ) {
            LOGGER.finest(() -> String.format("[%s] Using filtering URLConnection", url));
            return filteringUrlConnectionFactory.apply(url, urlConnection);
        } else {
            LOGGER.finest(() -> String.format("[%s] Using unmodified URLConnection", url));
            return urlConnection;
        }
    }
}
