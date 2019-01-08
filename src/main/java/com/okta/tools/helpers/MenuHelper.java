package com.okta.tools.helpers;

public interface MenuHelper {
    /**
     * Prompt the user to select an option from a menu of options
     *
     * @param max The maximum number of options
     * @return The selected option
     */
    int promptForMenuSelection(int max);
}
