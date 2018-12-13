package com.okta.tools.authentication;

import com.okta.tools.helpers.CookieHelper;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Adaptor for {@link com.sun.webkit.network.CookieManager} that stores cookies
 */
public final class CookieManager extends CookieHandler {
    // internal class use is intentional: we need RFC6265 cookies which this OpenJFX class handles
    private final CookieHandler cookieHandler = new com.sun.webkit.network.CookieManager();
    private final CookieHelper cookieHelper;

    public CookieManager(CookieHelper cookieHelper) {
        this.cookieHelper = cookieHelper;
    }

    @Override
    public Map<String, List<String>> get(URI uri, Map<String,List<String>> requestHeaders) throws IOException
    {
        return cookieHandler.get(uri, requestHeaders);
    }

    @Override
    public void put(URI uri, Map<String,List<String>> responseHeaders) throws IOException {
        cookieHelper.storeCookies(responseHeaders);
        cookieHandler.put(uri, responseHeaders);
    }
}
