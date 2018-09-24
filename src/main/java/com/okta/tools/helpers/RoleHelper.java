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

    private OktaAwsCliEnvironment environment;

    public RoleHelper(OktaAwsCliEnvironment environment) {
        this.environment = environment;
    }

    public AssumeRoleWithSAMLResult assumeChosenAwsRole(AssumeRoleWithSAMLRequest assumeRequest) {
        BasicAWSCredentials nullCredentials = new BasicAWSCredentials("", "");
        AWSCredentialsProvider nullCredentialsProvider = new AWSStaticCredentialsProvider(nullCredentials);
        AWSSecurityTokenService sts = AWSSecurityTokenServiceClientBuilder
                .standard()
                .withRegion(Regions.US_EAST_1)
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
                    if (roleOption.roleArn.equals(environment.awsRoleToAssume)) {
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
                selection = MenuHelper.promptForMenuSelection(roleArns.size());
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

    private List<AccountOption> getAvailableRoles(String samlResponse) throws IOException {
        Document document = AwsSamlRoleUtils.getSigninPageDocument(samlResponse);
        return AwsSamlSigninParser.parseAccountOptions(document);
    }
}
