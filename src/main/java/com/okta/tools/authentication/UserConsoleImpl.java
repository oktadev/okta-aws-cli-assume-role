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
