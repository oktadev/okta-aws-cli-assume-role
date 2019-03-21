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

import com.okta.tools.models.AccountOption;
import com.okta.tools.models.RoleOption;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AwsSamlSigninParserTest {
    @Test
    void testParseAwsSamlRolePicker() throws IOException {
        Document document = Jsoup.parse(
                // Load a sanitized copy of the real AWS SAML sign-in page
                // If AWS changes that page significantly this test file can be updated
                getClass().getResourceAsStream("AwsSamlSigninParserTest.html"),
                StandardCharsets.UTF_8.name(),
                "https://signin.aws.amazon.com/saml"
        );
        List<AccountOption> accountOptions = AwsSamlSigninParser.parseAccountOptions(document);
        assertNotNull(accountOptions);
        assertEquals(accountOptions.size(), 9);
        assertMatchingAccount(accountOptions.get(0), "Account: example-master (000000000000)",
                new RoleOption("EC2-Admins", "arn:aws:iam::000000000000:role/EC2-Admins"));
        // Typo in actual IAM role name should be preserved in parsed output (try to catch variable fixation)
        assertMatchingAccount(accountOptions.get(1), "Account: example-identity (111111111111)",
                new RoleOption("EC2a-Admins", "arn:aws:iam::111111111111:role/EC2a-Admins"));
        // Typo in actual IAM role name should be preserved in parsed output (try to catch variable fixation)
        assertMatchingAccount(accountOptions.get(2), "Account: example-logging (222222222222)",
                new RoleOption("EC2b-Admins", "arn:aws:iam::222222222222:role/EC2b-Admins"),
                new RoleOption("S3-Admins", "arn:aws:iam::222222222222:role/S3-Admins")
        );
        assertMatchingAccount(accountOptions.get(3), "Account: example-security (333333333333)",
                new RoleOption("EC2-Admins", "arn:aws:iam::333333333333:role/EC2-Admins"));
        assertMatchingAccount(accountOptions.get(4), "Account: example-production (444444444444)",
                new RoleOption("EC2-Admins", "arn:aws:iam::444444444444:role/EC2-Admins"));
        assertMatchingAccount(accountOptions.get(5), "Account: example-marketing (555555555555)",
                new RoleOption("EC2-Admins", "arn:aws:iam::555555555555:role/EC2-Admins"));
        assertMatchingAccount(accountOptions.get(6), "Account: example-research (666666666666)",
                new RoleOption("EC2-Admins", "arn:aws:iam::666666666666:role/EC2-Admins"),
                new RoleOption("S3-Admins", "arn:aws:iam::666666666666:role/S3-Admins"));
        assertMatchingAccount(accountOptions.get(7), "Account: example-partner (777777777777)",
                new RoleOption("IAM-Admins", "arn:aws:iam::777777777777:role/IAM-Admins"));
        assertMatchingAccount(accountOptions.get(8), "Account: example-corporate (888888888888)",
                new RoleOption("EC2-Admins", "arn:aws:iam::888888888888:role/EC2-Admins"));

    }

    private void assertMatchingAccount(
            AccountOption masterAccount,
            String expectedAccountName,
            RoleOption... expectedRoleOptions
    ) {
        assertEquals(expectedAccountName, masterAccount.accountName);
        assertNotNull(masterAccount.roleOptions);
        assertEquals(expectedRoleOptions.length, masterAccount.roleOptions.size());
        Iterator<RoleOption> roleOptionIterator = masterAccount.roleOptions.iterator();
        for (RoleOption expectedRoleOption : expectedRoleOptions) {
            RoleOption roleOption = roleOptionIterator.next();
            assertNotNull(roleOption);
            assertEquals(expectedRoleOption.roleName, roleOption.roleName);
            assertEquals(expectedRoleOption.roleArn, roleOption.roleArn);
        }
    }
}