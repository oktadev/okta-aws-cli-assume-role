/*
 * Copyright 2018 Okta
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
package com.okta.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okta.tools.authentication.OktaAuthentication;
import com.okta.tools.authentication.OktaMFA;
import com.okta.tools.helpers.CookieHelper;
import com.okta.tools.helpers.RoleHelper;
import com.okta.tools.models.AccountOption;
import com.okta.tools.saml.OktaSaml;

import java.util.List;

public class ListRoles {
    public static void main(String[] args) throws Exception {
        OktaAwsCliEnvironment environment = OktaAwsConfig.loadEnvironment();
        CookieHelper cookieHelper = new CookieHelper(environment);
        OktaMFA oktaMFA = new OktaMFA(environment);
        OktaAuthentication oktaAuthentication = new OktaAuthentication(environment, oktaMFA);
        OktaSaml oktaSaml = new OktaSaml(environment, cookieHelper, oktaAuthentication);
        String samlResponse = oktaSaml.getSamlResponse();
        RoleHelper roleHelper = new RoleHelper(environment);
        List<AccountOption> availableRoles = roleHelper.getAvailableRoles(samlResponse);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(System.out, availableRoles);
    }
}
