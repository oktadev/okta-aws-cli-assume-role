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

import com.okta.tools.helpers.HttpHelper;
import com.okta.tools.models.AuthResult;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class OktaAuthnClientImpl implements OktaAuthnClient {
    private static final Logger logger = Logger.getLogger(OktaAuthnClientImpl.class.getName());
    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";

    @Override
    public AuthResult primaryAuthentication(String username, String password, String oktaOrg) throws IOException {
        // Okta authn API docs: https://developer.okta.com/docs/api/resources/authn#primary-authentication
        HttpPost httpPost = new HttpPost("https://" + oktaOrg + "/api/v1/authn");

        httpPost.addHeader("Accept", CONTENT_TYPE_APPLICATION_JSON);
        httpPost.addHeader("Content-Type", CONTENT_TYPE_APPLICATION_JSON);
        httpPost.addHeader("Cache-Control", "no-cache");

        JSONObject authnRequest = new JSONObject();
        authnRequest.put("username", username);
        authnRequest.put("password", password);

        StringEntity entity = new StringEntity(authnRequest.toString(), StandardCharsets.UTF_8);
        entity.setContentType(CONTENT_TYPE_APPLICATION_JSON);
        httpPost.setEntity(entity);

        logger.finer("Calling okta authn service at " + httpPost.getURI());
        try (CloseableHttpClient httpClient = HttpHelper.createClient()) {
            CloseableHttpResponse authnResponse = httpClient.execute(httpPost);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(65536);
            authnResponse.getEntity().writeTo(byteArrayOutputStream);

            return new AuthResult(authnResponse.getStatusLine(), byteArrayOutputStream.toString());
        }
    }
}
