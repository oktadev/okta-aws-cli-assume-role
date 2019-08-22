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
package com.okta.tools.helpers;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLResult;
import com.okta.tools.OktaAwsCliEnvironment;
import com.okta.tools.models.AccountOption;
import com.okta.tools.models.RoleOption;
import com.okta.tools.saml.AwsSamlRoleUtils;
import com.okta.tools.saml.AwsSamlSigninParser;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RoleHelper {

    private OktaAwsCliEnvironment environment;

    public RoleHelper(OktaAwsCliEnvironment environment) {
        this.environment = environment;
    }

    public AssumeRoleWithSAMLResult assumeChosenAwsRole(AssumeRoleWithSAMLRequest assumeRequest) {
        BasicAWSCredentials nullCredentials = new BasicAWSCredentials("", "");
        AWSCredentialsProvider nullCredentialsProvider = new AWSStaticCredentialsProvider(nullCredentials);
        AWSSecurityTokenService sts = AWSSecurityTokenServiceClientBuilder
                .standard()
                .withRegion(environment.awsRegion)
                .withCredentials(nullCredentialsProvider)
                .build();
        return sts.assumeRoleWithSAML(assumeRequest);
    }

    public AssumeRoleWithSAMLRequest chooseAwsRoleToAssume(String samlResponse) throws IOException {
        Map<String, String> roleIdpPairs = AwsSamlRoleUtils.getRoles(samlResponse);
        List<String> roleArns = new ArrayList<>();

        String principalArn;
        String roleArn;

        if (roleIdpPairs.containsKey(environment.awsRoleToAssume)) {
            principalArn = roleIdpPairs.get(environment.awsRoleToAssume);
            roleArn = environment.awsRoleToAssume;
        } else if (roleIdpPairs.size() > 1) {
            List<AccountOption> accountOptions = getAvailableRoles(samlResponse);

            System.err.println("\nPlease choose the role you would like to assume: ");
            //Gather list of applicable AWS roles
            int i = 0;
            int j = -1;

            for (AccountOption accountOption : accountOptions) {
                System.err.println(accountOption.accountName);
                for (RoleOption roleOption : accountOption.roleOptions) {
                    roleArns.add(roleOption.roleArn);
                    System.err.println("\t[ " + (i + 1) + " ]: " + roleOption.roleName);
                    if (roleOption.roleArn.equals(environment.awsRoleToAssume) ||
                        roleOption.roleName.equals(environment.awsRoleToAssume)) {
                        j = i;
                    }
                    i++;
                }
            }
            if ((environment.awsRoleToAssume != null && !environment.awsRoleToAssume.isEmpty()) && j == -1) {
                System.err.println("No match for role " + environment.awsRoleToAssume);
            }

            // Default to no selection
            final int selection;

            // If config.properties has matching role, use it and don't prompt user to select
            if (j >= 0) {
                selection = j;
                System.err.println("Selected option " + (j + 1) + " based on OKTA_AWS_ROLE_TO_ASSUME value");
            } else {
                //Prompt user for role selection
                selection = new MenuHelperImpl().promptForMenuSelection(roleArns.size());
            }

            roleArn = roleArns.get(selection);
            principalArn = roleIdpPairs.get(roleArn);
        } else {
            Map.Entry<String, String> role = roleIdpPairs.entrySet().iterator().next();
            System.err.println("Auto select role as only one is available : " + role.getKey());
            roleArn = role.getKey();
            principalArn = role.getValue();
        }

        int stsDuration = environment.stsDuration;

        return new AssumeRoleWithSAMLRequest()
                .withPrincipalArn(principalArn)
                .withRoleArn(roleArn)
                .withSAMLAssertion(samlResponse)
                .withDurationSeconds(stsDuration);
    }

    public List<AccountOption> getAvailableRoles(String samlResponse) throws IOException {
        Map<String, String> roles = AwsSamlRoleUtils.getRoles(samlResponse);
        if (roles.size() == 1) {
            String roleArn = roles.values().iterator().next();
            return Collections.singletonList(
                    new AccountOption("Account:  (" + roleArn.substring("arn:aws:iam::".length(), "arn:aws:iam::".length() + 12) + ")",
                            Collections.singletonList(
                                    new RoleOption(roleArn.substring(roleArn.indexOf(":role/") + ":role/".length()), roleArn)
                            )
                    )
            );
        }
        Document document = AwsSamlRoleUtils.getSigninPageDocument(samlResponse);
        return AwsSamlSigninParser.parseAccountOptions(document);
    }
}
