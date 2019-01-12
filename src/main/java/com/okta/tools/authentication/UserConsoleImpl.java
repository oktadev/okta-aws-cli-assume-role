package com.okta.tools.authentication;

import java.util.Scanner;

public class UserConsoleImpl implements UserConsole {

    @Override
    public String promptForUsername() {
        System.err.print("Username: ");
        return new Scanner(System.in).next();
    }

    @Override
    public String promptForPassword() {
        if (System.console() == null) { // hack to be able to debug in an IDE
            System.err.print("Password: ");
            return new Scanner(System.in).nextLine();
        } else {
            return new String(System.console().readPassword("Password: "));
        }
    }
}
