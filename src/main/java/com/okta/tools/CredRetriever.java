package com.okta.tools;

import java.io.Console;
import java.util.Scanner;

public interface CredRetriever  {
    Creds getCreds();
}
class Creds {
    String username;
    String password;
    public Creds(String username, String password) {
        this.username = username;
        this.password = password;
    }
}

class StdinCredRetriever implements CredRetriever {
    public Creds getCreds() {
        // Prompt for user credentials
        System.err.print("Username: ");
        Scanner scanner = new Scanner(System.in);

        String oktaUsername = scanner.next();

        Console console = System.console();
        String oktaPassword = null;
        if (console != null) {
            oktaPassword = new String(console.readPassword("Password: "));
        } else { // hack to be able to debug in an IDE
            System.err.print("Password: ");
            oktaPassword = scanner.next();
        }
        return new Creds(oktaUsername,oktaPassword);
    }
}