package com.okta.tools.models;

import org.apache.http.StatusLine;

public class AuthResult {
    public final StatusLine statusLine;
    public final String responseContent;

    public AuthResult(StatusLine statusLine, String responseContent) {
        this.statusLine = statusLine;
        this.responseContent = responseContent;
    }
}
