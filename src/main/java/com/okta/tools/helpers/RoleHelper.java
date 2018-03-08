package com.okta.tools.helpers;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
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
import java.util.List;
import java.util.Map;

public class RoleHelper {

    public static AssumeRoleWithSAMLResult assumeChosenAwsRole(AssumeRoleWithSAMLRequest assumeRequest) {
        BasicAWSCredentials nullCredentials = new BasicAWSCredentials("", "");
        AWSCredentialsProvider nullCredentialsProvider = new AWSStaticCredentialsProvider(nullCredentials);
        AWSSecurityTokenService sts = AWSSecurityTokenServiceClientBuilder
                .standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(nullCredentialsProvider)
                .build();
        return sts.assumeRoleWithSAML(assumeRequest);
    }

    public static AssumeRoleWithSAMLRequest chooseAwsRoleToAssume(String samlResponse) throws IOException {
        Map<String, String> roleIdpPairs = AwsSamlRoleUtils.getRoles(samlResponse);
        List<String> roleArns = new ArrayList<>();

        String principalArn;
        String roleArn;

        if (roleIdpPairs.containsKey(OktaAwsCliEnvironment.awsRoleToAssume)) {
            principalArn = roleIdpPairs.get(OktaAwsCliEnvironment.awsRoleToAssume);
            roleArn = OktaAwsCliEnvironment.awsRoleToAssume;
        } else if (roleIdpPairs.size() > 1) {
            List<AccountOption> accountOptions = getAvailableRoles(samlResponse);

            System.out.println("\nPlease choose the role you would like to assume: ");
            System.out.println(roleIdpPairs.toString());
            System.out.println(accountOptions.toString());
            //Gather list of applicable AWS roles
            int i = 0;
            int j = -1;

            for (AccountOption accountOption : accountOptions) {
                System.out.println(accountOption.accountName);
                for (RoleOption roleOption : accountOption.roleOptions) {
                    roleArns.add(roleOption.roleArn);
                    System.out.println("\t[ " + (i + 1) + " ]: " + roleOption.roleName);
                    if (roleOption.roleArn.equals(OktaAwsCliEnvironment.awsRoleToAssume)) {
                        j = i;
                    }
                    i++;
                }
            }
            if ((OktaAwsCliEnvironment.awsRoleToAssume != null && !OktaAwsCliEnvironment.awsRoleToAssume.isEmpty()) && j == -1) {
                System.out.println("No match for role " + OktaAwsCliEnvironment.awsRoleToAssume);
            }

            // Default to no selection
            final int selection;

            // If config.properties has matching role, use it and don't prompt user to select
            if (j >= 0) {
                selection = j;
                System.out.println("Selected option " + (j + 1) + " based on OKTA_AWS_ROLE_TO_ASSUME value");
            } else {
                //Prompt user for role selection
                selection = MenuHelper.promptForMenuSelection(roleArns.size());
            }

            roleArn = roleArns.get(selection);
            principalArn = roleIdpPairs.get(roleArn);
        } else {
            Map.Entry<String, String> role = roleIdpPairs.entrySet().iterator().next();
            System.out.println("Auto select role as only one is available : " + role.getKey());
            roleArn = role.getKey();
            principalArn = role.getValue();
        }

        return new AssumeRoleWithSAMLRequest()
                .withPrincipalArn(principalArn)
                .withRoleArn(roleArn)
                .withSAMLAssertion(samlResponse)
                .withDurationSeconds(3600);
    }

    private static List<AccountOption> getAvailableRoles(String samlResponse) throws IOException {
        Document document = AwsSamlRoleUtils.getSigninPageDocument(samlResponse);
        return AwsSamlSigninParser.parseAccountOptions(document);
    }
}
