package com.okta.tools.models;

import java.time.Instant;

public class Session {
    public final String profileName;
    public final Instant expiry;

    public Session(String profileName, Instant expiry) {
        this.profileName = profileName;
        this.expiry = expiry;
    }
}