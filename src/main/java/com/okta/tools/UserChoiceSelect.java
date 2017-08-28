package com.okta.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Simple abstraction on how to process user choices
 */
public abstract class UserChoiceSelect {
    protected static final Logger logger = LogManager.getLogger(awscli.class);
    public abstract <T> T select(String choiceName, String desc, List<T> items, Function<T, String> namer);
}

/**
 * Explicitly not using ScannerFactory which is a real thing
 * becuase we just want to be able to unit test out something that
 * grabs from stdin
 */
interface ScanFactory {
    Scanner getScanner();
}


class inputScanner implements ScanFactory {

    @Override
    public Scanner getScanner() {
        return new Scanner(System.in);
    }
}

class ConfigChoice extends  UserChoiceSelect {
    private Map<String,String> config;

    public ConfigChoice(Map<String, String> config) {
        this.config = config;
    }

    @Override
    public <T> T select(String choiceName, String desc, List<T> items, Function<T, String> namer) {
        if(config.containsKey(choiceName)) {
            String matchPattern = config.get(choiceName);
            Pattern p = Pattern.compile(matchPattern);
            for (Iterator<T> iter = items.iterator(); iter.hasNext(); ) {
                T item = iter.next();
                if (p.matcher(namer.apply(item)).matches()) {
                    return item;
                }
            }
            return null;
        } else {
            logger.debug("No config entry for %s",choiceName);
            return null;
        }
    }
}

class ConfigThenInput extends UserChoiceSelect{

    private ConfigChoice config;
    private InputChoice input;

    public ConfigThenInput(ConfigChoice config) {
        this(config,new InputChoice());
    }

    public ConfigThenInput(ConfigChoice config, InputChoice input) {
        this.config = config;
        this.input = input;
    }

    @Override
    public <T> T select(String choiceName, String desc, List<T> items, Function<T, String> namer) {
        T result = config.select(choiceName,desc,items,namer);
        if (result == null) {
            logger.info("No config entry %s, asking for input",choiceName);
            result = input.select(choiceName,desc,items,namer);
        }
        return result;
    }
}

class InputChoice extends UserChoiceSelect {

    ScanFactory ScanFactory = new inputScanner();

    InputChoice() {
        this(new ScanFactory() {
            public Scanner getScanner() {
                return new Scanner(System.in);
            }
        });
    }
    InputChoice(ScanFactory ScanFactory) {
        this.ScanFactory = ScanFactory;
    }

    @Override
    public <T> T select(String choiceName, String desc, List<T> items, Function<T, String> namer) {
        logger.debug(">> select %s", choiceName);
        int i = 0;
        for (T item : items) {
            System.err.println("[ " + (i++ + 1) + " ]: " + namer.apply(item));
        }

        if (items.size() > 1) {
            return items.get(numSelection(items.size()));
        } else if (items.size() == 1) {
            T item = items.get(0);
            logger.info("Only one choice for %s choosing %s",choiceName,namer.apply(item));
            return item;
        } else {
            System.err.println(String.format("No choices for %s", choiceName));
            return null;
        }
    }

    private  int numSelection(int max) {
        Scanner scanner = ScanFactory.getScanner();
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
