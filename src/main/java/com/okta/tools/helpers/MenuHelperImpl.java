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

import java.util.InputMismatchException;
import java.util.Scanner;
import java.util.logging.Logger;

public class MenuHelperImpl implements MenuHelper {

    private static final Logger logger = Logger.getLogger(MenuHelperImpl.class.getName());

    public int promptForMenuSelection(int max) {
        if (max == 1) return 0;
        Scanner scanner = new Scanner(System.in);

        int selection = -1;
        while (selection == -1) {
            //prompt user for selection
            System.err.print("Selection: ");
            String selectInput = scanner.nextLine();
            try {
                selection = Integer.parseInt(selectInput) - 1;
                if (selection < 0 || selection >= max) {
                    throw new InputMismatchException();
                }
            } catch (InputMismatchException e) {
                logger.severe("Invalid input: Please enter a valid selection\n");
                selection = -1;
            } catch (NumberFormatException e) {
                logger.severe("Invalid input: Please enter in a number \n");
                selection = -1;
            }
        }

        return selection;
    }
}
