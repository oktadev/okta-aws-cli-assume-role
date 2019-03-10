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
package com.okta.tools.models;

import java.util.List;
import java.util.Objects;

public final class AccountOption {
    public final String accountName;
    public final List<RoleOption> roleOptions;

    public AccountOption(String accountName, List<RoleOption> roleOptions) {
        this.accountName = accountName;
        this.roleOptions = roleOptions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountOption that = (AccountOption) o;
        return Objects.equals(accountName, that.accountName) &&
                Objects.equals(roleOptions, that.roleOptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountName, roleOptions);
    }

    @Override
    public String toString() {
        return "AccountOption{" +
                "accountName='" + accountName + '\'' +
                ", roleOptions=" + roleOptions +
                '}';
    }
}
