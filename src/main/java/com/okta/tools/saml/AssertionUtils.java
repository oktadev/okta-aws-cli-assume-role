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

import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.core.xml.schema.impl.XSAnyImpl;
import org.opensaml.saml.saml2.core.Assertion;

import java.util.Collection;
import java.util.stream.Collectors;

final class AssertionUtils {
    private AssertionUtils() {}

    static Collection<String> getAttributeValues(Assertion assertion, String attributeName) {
        return assertion.getAttributeStatements()
                .stream()
                .flatMap(x -> x.getAttributes().stream())
                .filter(x -> attributeName.equals(x.getName()))
                .flatMap(x -> x.getAttributeValues().stream())
                .map(AssertionUtils::getAttributeValue)
                .filter(x -> x != null && !x.isEmpty())
                .collect(Collectors.toList());
    }

    private static String getAttributeValue(XMLObject attributeValue) {
        if (attributeValue == null) return null;
        if (attributeValue instanceof XSString) return ((XSString) attributeValue).getValue();
        if (attributeValue instanceof XSAnyImpl) return ((XSAnyImpl) attributeValue).getTextContent();
        return attributeValue.toString();
    }
}
