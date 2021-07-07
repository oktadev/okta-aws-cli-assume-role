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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OktaFactorSelectorTest {

    private OktaFactorSelector oktaFactorSelector;
    private JSONArray factors;
    private JSONObject primaryAuthResponse;

    @BeforeEach
    void setUp() {
        String oktaMfaChoice = "OKTA.push";
        OktaAwsCliEnvironment environment = new OktaAwsCliEnvironment(false, null, null, null, null, null, null, null, 0, null, oktaMfaChoice, false, null);
        MenuHelper menuHelper = mock(MenuHelper.class);
        oktaFactorSelector = new OktaFactorSelectorImpl(environment, menuHelper);
        primaryAuthResponse = mock(JSONObject.class);
        JSONObject embedded = mock(JSONObject.class);
        factors = mock(JSONArray.class);

        when(menuHelper.promptForMenuSelection(anyInt())).thenReturn(0);
        when(primaryAuthResponse.getJSONObject("_embedded")).thenReturn(embedded);
        when(embedded.getJSONArray("factors")).thenReturn(factors);
    }

    @Test
    void noFactorsEnrolled() {
        when(factors.length()).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> oktaFactorSelector.selectFactor(primaryAuthResponse),
                "You have no factors enrolled."
        );
    }

    @Test
    void noSupportedFactors() {
        JSONObject webFactor = mock(JSONObject.class);
        when(webFactor.getString("provider")).thenReturn("DUO");
        when(webFactor.getString("factorType")).thenReturn("web");
        when(factors.length()).thenReturn(1);
        when(factors.getJSONObject(eq(0))).thenReturn(webFactor);

        assertThrows(
                IllegalStateException.class,
                () -> oktaFactorSelector.selectFactor(primaryAuthResponse),
                "None of your factors are supported."
        );
    }

    @Test
    void singleFactorMatchesOktaMfaChoice() {
        JSONObject oktaPushFactor = mock(JSONObject.class);
        when(oktaPushFactor.getString("provider")).thenReturn("OKTA");
        when(oktaPushFactor.getString("factorType")).thenReturn("push");
        when(factors.length()).thenReturn(1);
        when(factors.getJSONObject(eq(0))).thenReturn(oktaPushFactor);

        assertEquals(oktaPushFactor, oktaFactorSelector.selectFactor(primaryAuthResponse));
    }

    @Test
    void singleFactorDoesntMatchOktaMfaChoice() {
        JSONObject totpFactor = mock(JSONObject.class);
        when(totpFactor.getString("provider")).thenReturn("OKTA");
        when(totpFactor.getString("factorType")).thenReturn("software:token:totp");
        when(factors.length()).thenReturn(1);
        when(factors.getJSONObject(eq(0))).thenReturn(totpFactor);

        assertEquals(totpFactor, oktaFactorSelector.selectFactor(primaryAuthResponse));
    }

    @Test
    void multipleFactorsOneMatchesOktaMfaChoice() {
        JSONObject oktaPushFactor = mock(JSONObject.class);
        when(oktaPushFactor.getString("provider")).thenReturn("OKTA");
        when(oktaPushFactor.getString("factorType")).thenReturn("push");
        JSONObject totpFactor = mock(JSONObject.class);
        when(totpFactor.getString("provider")).thenReturn("OKTA");
        when(totpFactor.getString("factorType")).thenReturn("software:token:totp");
        when(factors.length()).thenReturn(2);
        when(factors.getJSONObject(eq(0))).thenReturn(oktaPushFactor);
        when(factors.getJSONObject(eq(1))).thenReturn(totpFactor);

        assertEquals(oktaPushFactor, oktaFactorSelector.selectFactor(primaryAuthResponse));
    }
}
