package com.okta.tools;

import java.io.Console;
import java.util.Scanner;

class CredRetriever  {
    private PasswordRetriever pass;
    private UsernameRetriever user;
    Creds getCreds(){
        return new Creds(user.getUsername(), pass.getPassword());
    }

    public CredRetriever(PasswordRetriever pass, UsernameRetriever user) {
        this.pass = pass;
        this.user = user;
    }

    public CredRetriever() {
        this(new stdinPasswordRetriever(),new stdinUsernameRetiever());
    }
}


class Creds {
    String username;
    String password;
    public Creds(String username, String password) {
        this.username = username;
        this.password = password;
    }
}


interface PasswordRetriever {
    public String getPassword();
}

interface UsernameRetriever {
    public String getUsername();
}

class stdinUsernameRetiever implements UsernameRetriever {

    @Override
    public String getUsername() {
        System.err.print("Username: ");
        Scanner scanner = new Scanner(System.in);
        return scanner.next();
    }
}

class stdinPasswordRetriever implements PasswordRetriever {

    @Override
    public String getPassword() {
        Console console = System.console();
        Scanner scanner = new Scanner(System.in);

        String password = null;
        if (console != null) {
            password = new String(console.readPassword("Password: "));
        } else { // hack to be able to debug in an IDE
            System.err.print("Password: ");
            password = scanner.next();
        }
        return password;
    }
}


