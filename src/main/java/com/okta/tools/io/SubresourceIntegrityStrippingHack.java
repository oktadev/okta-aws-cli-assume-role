package com.okta.tools.io;

import com.okta.tools.OktaAwsCliEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.logging.Logger;

public class SubresourceIntegrityStrippingHack {
    private static final Logger LOGGER = Logger.getLogger(SubresourceIntegrityStrippingHack.class.getName());

    private SubresourceIntegrityStrippingHack() {}

    public static void overrideHttpsProtocolHandler(OktaAwsCliEnvironment environment) {
        try {
            URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ?
                    new LoginPageInterceptingProtocolHandler(environment,
                            SubresourceIntegrityStrippingURLConnection::new) :
                    null
            );
            LOGGER.finest("Successfully registered custom protocol handler");
        } catch (Exception e) {
            LOGGER.warning(() -> {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                e.printStackTrace(new PrintWriter(outputStream));
                return String.format("Unable to register custom protocol handler:%n%s", outputStream.toString());
            });
        }
    }
}
