package com.okta.tools.helpers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.InputMismatchException;
import java.util.Scanner;

public class MenuHelper {

    private static final Logger logger = LogManager.getLogger(MenuHelper.class);

    /**
     * Prompt the user to select an option from a menu of options
     *
     * @param max The maximum number of options
     * @return The selected option
     */
    public static int promptForMenuSelection(int max) {
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
                logger.error("Invalid input: Please enter a valid selection\n");
                selection = -1;
            } catch (NumberFormatException e) {
                logger.error("Invalid input: Please enter in a number \n");
                selection = -1;
            }
        }

        return selection;
    }
}
