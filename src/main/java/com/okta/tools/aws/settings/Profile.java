package com.okta.tools.aws.settings;

import java.time.Instant;

public class Profile {
    public final Instant expiry;
    public final String roleArn;

    public Profile(Instant expiry, String roleArn) {
        this.expiry = expiry;
        this.roleArn = roleArn;
    }
}
