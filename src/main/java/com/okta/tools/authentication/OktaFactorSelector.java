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

import org.json.JSONException;
import org.json.JSONObject;

public interface OktaFactorSelector {
    /**
     * Handles selection of a factor from multiple choices
     *
     * @param primaryAuthResponse The response from Primary Authentication
     * @return A {@link JSONObject} representing the selected factor.
     * @throws JSONException if a network or protocol error occurs
     */
    JSONObject selectFactor(JSONObject primaryAuthResponse);
}
