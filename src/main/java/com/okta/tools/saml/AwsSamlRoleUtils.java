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
package com.okta.tools.saml;

import com.okta.tools.helpers.HttpHelper;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.saml2.core.Assertion;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AwsSamlRoleUtils {
    private static final String AWS_ROLE_SAML_ATTRIBUTE = "https://aws.amazon.com/SAML/Attributes/Role";

    private AwsSamlRoleUtils() {}

    public static Map<String, String> getRoles(String samlResponse) {
        Map<String, String> roles = new LinkedHashMap<>();
        for (String roleIdpPair : getRoleIdpPairs(samlResponse)) {
            String[] parts = roleIdpPair.split(",");
            String principalArn = parts[0];
            String roleArn = parts[1];
            roles.put(roleArn, principalArn);
        }
        return roles;
    }

    private static Collection<String> getRoleIdpPairs(String samlResponse) {
        try {
            Assertion assertion = SamlResponseUtils.getAssertion(samlResponse);
            return AssertionUtils.getAttributeValues(assertion, AWS_ROLE_SAML_ATTRIBUTE);
        } catch (ParserConfigurationException | UnmarshallingException | SAXException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String getDestination(String samlResponse) {
        try {
            String destination = SamlResponseUtils.getDestination(samlResponse);
            return destination;
        } catch (ParserConfigurationException | UnmarshallingException | SAXException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Document getSigninPageDocument(String samlResponse) throws IOException {
        String destination = getDestination(samlResponse);
        HttpPost httpPost = new HttpPost(destination);
        UrlEncodedFormEntity samlForm = new UrlEncodedFormEntity(Arrays.asList(
                new BasicNameValuePair("SAMLResponse", samlResponse),
                new BasicNameValuePair("RelayState", "")
        ), StandardCharsets.UTF_8);
        httpPost.setEntity(samlForm);
        try (CloseableHttpClient httpClient = HttpHelper.createClient();
             CloseableHttpResponse samlSigninResponse = httpClient.execute(httpPost)) {
            return Jsoup.parse(
                    samlSigninResponse.getEntity().getContent(),
                    StandardCharsets.UTF_8.name(),
                    destination
            );
        }
    }
}
