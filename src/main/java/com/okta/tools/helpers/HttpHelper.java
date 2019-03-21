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
package com.okta.tools.helpers;

import org.apache.http.HttpHost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.*;

// Inspired by https://stackoverflow.com/a/22960881/154527
public final class HttpHelper {
    public static CloseableHttpClient createClient(HttpClientBuilder httpClientBuilder)
    {
        Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", ProxySelectorPlainConnectionSocketFactory.INSTANCE)
                .register("https", new ProxySelectorSSLConnectionSocketFactory(SSLContexts.createSystemDefault()))
                .build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(reg);
        return httpClientBuilder
                .useSystemProperties()
                .setConnectionManager(cm)
                .build();
    }

    public static CloseableHttpClient createClient()
    {
        return createClient(HttpClients.custom());
    }

    private enum ProxySelectorPlainConnectionSocketFactory implements ConnectionSocketFactory {
        INSTANCE;

        @Override
        public Socket createSocket(HttpContext context) throws IOException {
            return HttpHelper.createSocket(context, PlainConnectionSocketFactory.INSTANCE::createSocket);
        }

        @Override
        public Socket connectSocket(int connectTimeout, Socket sock, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException {
            return PlainConnectionSocketFactory.INSTANCE.connectSocket(connectTimeout, sock, host, remoteAddress, localAddress, context);
        }
    }

    private static final class ProxySelectorSSLConnectionSocketFactory extends SSLConnectionSocketFactory {
        ProxySelectorSSLConnectionSocketFactory(SSLContext sslContext) {
            super(sslContext);
        }

        @Override
        public Socket createSocket(HttpContext context) throws IOException {
            return HttpHelper.createSocket(context, super::createSocket);
        }
    }

    private interface SocketCreator {
        Socket createSocket(HttpContext context) throws IOException;
    }

    private static Socket createSocket(HttpContext context, SocketCreator socketCreator) throws IOException {
        HttpHost httpTargetHost = (HttpHost) context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST);
        URI uri = URI.create(httpTargetHost.toURI());
        Proxy proxy = ProxySelector.getDefault().select(uri).iterator().next();
        return proxy.type().equals(Proxy.Type.SOCKS) ? new Socket(proxy) : socketCreator.createSocket(context);
    }
}
