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

import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.saml2.core.Assertion;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AwsSamlRoleUtils {
    private static final String AWS_ROLE_SAML_ATTRIBUTE = "https://aws.amazon.com/SAML/Attributes/Role";

    public static Map<RoleArn, PrincipalArn> getRoles(String samlResponse) {
        Map<RoleArn, PrincipalArn> roles = new LinkedHashMap<>();
        for (String roleIdpPair: getRoleIdpPairs(samlResponse)) {
            String[] parts = roleIdpPair.split(",");
            String principalArn = parts[0];
            String roleArn  = parts[1];
            roles.put(() -> roleArn, () -> principalArn);
        }
        return roles;
    }

    private static Collection<String> getRoleIdpPairs(String samlResponse) {
        try {
            Assertion assertion = SamlResponseUtils.getAssertion(samlResponse);
            return AssertionUtils.getAttributeValues(assertion, AWS_ROLE_SAML_ATTRIBUTE);
        } catch (ParserConfigurationException | UnmarshallingException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
