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

import com.okta.tools.OktaAwsCliEnvironment;
import com.okta.tools.helpers.MenuHelper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OktaFactorSelectorImpl implements OktaFactorSelector {
    public static final String FACTOR_TYPE = "factorType";
    private final OktaAwsCliEnvironment environment;
    private final MenuHelper menuHelper;

    public OktaFactorSelectorImpl(OktaAwsCliEnvironment environment, MenuHelper menuHelper) {
        this.environment = environment;
        this.menuHelper = menuHelper;
    }

    @Override
    public JSONObject selectFactor(JSONObject primaryAuthResponse) {
        JSONArray factors = primaryAuthResponse.getJSONObject("_embedded").getJSONArray("factors");

        List<JSONObject> supportedFactors = getUsableFactors(factors);
        ensureUsableFactors(factors, supportedFactors);

        JSONObject oktaMfaChoice = presentMfaMenu(supportedFactors);
        if (oktaMfaChoice != null) return oktaMfaChoice;

        // Handles user factor selection
        int selection = menuHelper.promptForMenuSelection(supportedFactors.size());
        return supportedFactors.get(selection);
    }

    private JSONObject presentMfaMenu(List<JSONObject> supportedFactors) {
        if (supportedFactors.size() > 1) {
            if (environment.oktaMfaChoice != null) {
                for (JSONObject factor : supportedFactors) {
                    String provider = factor.getString("provider");
                    String factorType = factor.getString(FACTOR_TYPE);
                    if ((provider + "." + factorType).equals(environment.oktaMfaChoice)) {
                        return factor;
                    }
                }
            }
            System.err.println("\nMulti-Factor authentication is required. Please select a factor to use.");
            System.err.println("Factors:");
            for (int i = 0; i < supportedFactors.size(); i++) {
                JSONObject factor = supportedFactors.get(i);
                String factorType = factor.getString(FACTOR_TYPE);
                String factorDescription = getFactorDescription(factorType, factor);
                System.err.println("[ " + (i + 1) + " ] : " + factorDescription);
            }
        }
        return null;
    }

    private void ensureUsableFactors(JSONArray factors, List<JSONObject> supportedFactors) {
        if (supportedFactors.isEmpty()) {
            if (factors.length() > 0) {
                throw new IllegalStateException("None of your factors are supported.");
            } else {
                throw new IllegalStateException("You have no factors enrolled.");
            }
        }
    }

    /**
     * Selects the supported factors from a list of factors
     *
     * @param factors The list of factors
     * @return A {@link List<JSONObject>} of supported factors
     */
    private static List<JSONObject> getUsableFactors(JSONArray factors) {
        List<JSONObject> eligibleFactors = new ArrayList<>();

        for (int i = 0; i < factors.length(); i++) {
            JSONObject factor = factors.getJSONObject(i);

            String factorType = factor.getString(FACTOR_TYPE);
            if (!Arrays.asList(
                    "web", // Factors that only work on the web cannot be verified via the CLI
                    "call", // Call factor support isn't implemented yet
                    "email"  // Email factor support isn't implemented yet
            ).contains(factorType)) {
                eligibleFactors.add(factor);
            }
        }

        return eligibleFactors;
    }

    private static String getFactorDescription(String factorType, JSONObject factor) {
        String provider = factor.getString("provider");
        switch (factorType) {
            case "push":
                return "Okta Verify (Push)";
            case "question":
                return "Security Question";
            case "sms":
                return "SMS Verification";
            case "call":
                return "Phone Verification"; // Unsupported
            case "token:software:totp":
                switch (provider) {
                    case "OKTA":
                        return "Okta Verify (TOTP)";
                    case "GOOGLE":
                        return "Google Authenticator";
                    default:
                        return provider + " " + factorType;
                }
            case "email":
                return "Email Verification";  // Unsupported
            case "token":
                switch (provider) {
                    case "SYMANTEC":
                        return "Symantec VIP";
                    case "RSA":
                        return "RSA SecurID";
                    default:
                        return provider + " " + factorType;
                }
            case "web":
                return "Duo Push"; // Unsupported
            case "token:hardware":
                return "Yubikey";
            default:
                return provider + " " + factorType;
        }
    }
}
