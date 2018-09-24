package com.okta.tools;

final class LogoutHandler {
    static boolean handleLogout(String[] args) throws Exception {
        if (args.length > 0 && "logout".equals(args[0])) {
            OktaAwsCliAssumeRole.withEnvironment(OktaAwsConfig.loadEnvironment()).logoutSession();
            System.err.println("You have been logged out");
            System.exit(0);
            return true;
        }
        return false;
    }
}
