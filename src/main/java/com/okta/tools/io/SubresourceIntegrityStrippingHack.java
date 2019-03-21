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
