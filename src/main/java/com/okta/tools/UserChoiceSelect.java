package com.okta.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

/**
 * Simple abstraction on how to process user choices
 */
public interface UserChoiceSelect {

    public <T> T select(String choiceName, String desc,  Function<T, String> namer, List<T> items);

}


interface scanFactory {
    Scanner getScanner();
}


class inputScanner implements scanFactory {

    @Override
    public Scanner getScanner() {
        return new Scanner(System.in);
    }
}

class ConfigChoice implements  UserChoiceSelect {

    @Override
    public <T> T select(String choiceName, String desc, Function<T, String> namer,  List<T> items) {
        return null;
    }
}

class InputChoice implements UserChoiceSelect {

    private static final Logger logger = LogManager.getLogger(awscli.class);
    scanFactory scanFactory = new inputScanner();

    @Override
    public <T> T select(String choiceName, String desc, Function<T, String> namer, List<T> items) {
        logger.debug(">> select %s", choiceName);
        System.out.println(desc);
        int i = 0;
        for (T item : items) {
            System.out.println("[ " + (i + 1) + " ]: " + namer.apply(item));
        }

        return items.get(numSelection(items.size()));
    }

    private  int numSelection(int max) {
        Scanner scanner = scanFactory.getScanner();
        int selection = -1;
        while (selection == -1) {
            //prompt user for selection
            System.out.print("Selection: ");
            String selectInput = scanner.nextLine();
            try {
                selection = Integer.parseInt(selectInput) - 1;
                if (selection >= max) {
                    InputMismatchException e = new InputMismatchException();
                    throw e;
                }
            } catch (InputMismatchException e) {
                //raised by something other than a number entered
                logger.error("Invalid input: Please enter a number corresponding to a role \n");
                selection = -1;
            } catch (NumberFormatException e) {
                //raised by number too high or low selected
                logger.error("Invalid input: Please enter in a number \n");
                selection = -1;
            }
        }
        return selection;

    }
}
