/*
 * Copyright 2017 Okta
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
package com.okta.tools.saml;

import com.okta.tools.models.AccountOption;
import com.okta.tools.models.RoleOption;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public interface AwsSamlSigninParser {
    static List<AccountOption> parseAccountOptions(Document document) {
        List<AccountOption> accountOptions = new ArrayList<>();
        Elements accountElements = document.select("fieldset > div.saml-account");
        for (Element accountElement : accountElements) {
            Elements accountNameElements = accountElement.select("div.saml-account-name");
            Element accountNameElement = accountNameElements.get(0);
            String accountName = accountNameElement.text();
            Elements roleOptionElements = accountElement.select("label.saml-role-description");
            List<RoleOption> roleOptions = new ArrayList<>();
            for (Element roleOptionElement : roleOptionElements) {
                String roleName = roleOptionElement.text();
                String roleArn = roleOptionElement.attr("for");
                roleOptions.add(new RoleOption(roleName, roleArn));
            }
            accountOptions.add(new AccountOption(accountName, roleOptions));
        }
        return accountOptions;
    }
}
