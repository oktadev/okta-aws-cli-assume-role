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

import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.impl.ResponseUnmarshaller;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

final class SamlResponseUtils {
    private SamlResponseUtils() {}

    static {
        try {
            InitializationService.initialize();
        } catch (InitializationException e) {
            throw new IllegalStateException(e);
        }
    }

    static Assertion getAssertion(String samlResponse) throws ParserConfigurationException, UnmarshallingException, SAXException, IOException {
        Response response = decodeSamlResponse(samlResponse);
        return getAssertion(response);
    }

    static String getDestination(String samlResponse) throws ParserConfigurationException, UnmarshallingException, SAXException, IOException {
        Response response = decodeSamlResponse(samlResponse);
        return getDestination(response);
    }

    private static Response decodeSamlResponse(String samlResponse) throws IOException, ParserConfigurationException, SAXException, UnmarshallingException {
        byte[] base64DecodedResponse = Base64.getDecoder().decode(samlResponse);
        ByteArrayInputStream is = new ByteArrayInputStream(base64DecodedResponse);
        DocumentBuilder docBuilder = getDocumentBuilder();
        Document document = docBuilder.parse(is);
        Element element = document.getDocumentElement();
        XMLObject responseXmlObj = new ResponseUnmarshaller().unmarshall(element);
        return (Response) responseXmlObj;
    }

    private static DocumentBuilder getDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory documentBuilderFactory = getDocumentBuilderFactory();
        return documentBuilderFactory.newDocumentBuilder();
    }

    private static DocumentBuilderFactory getDocumentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        documentBuilderFactory.setXIncludeAware(false);
        documentBuilderFactory.setExpandEntityReferences(false);
        return documentBuilderFactory;
    }

    private static Assertion getAssertion(Response response) {
        if (!response.getEncryptedAssertions().isEmpty())
            throw new IllegalStateException("Encrypted assertions are not supported");
        else if (response.getAssertions().isEmpty())
            throw new IllegalStateException("No assertions in SAML response");
        else if (response.getAssertions().size() > 1)
            throw new IllegalStateException("More than one assertion in SAML response");
        else
            return response.getAssertions().get(0);
    }

    private static String getDestination(Response response) {
        if (response.getDestination() == null)
            throw new IllegalStateException("No destination in SAML response");
        return response.getDestination();
    }
}
