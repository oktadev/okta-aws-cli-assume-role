package com.okta.tools;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class awscli {

    public static void main(String[] args) throws IOException {
        OktaAWSIntegration oA = new OktaAWSIntegration();
        String profileName = oA.authenticateAndSetupProfile();
        resultMessage(profileName);
    }


    /* prints final status message to user */
    private static void resultMessage(String profileName) {
        Calendar date = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat();
        date.add(Calendar.HOUR, 1);

        //change with file customization
        System.err.println("\n----------------------------------------------------------------------------------------------------------------------");
        System.err.println("Your new access key pair has been stored in the aws configuration file with the following profile name: " + profileName);
        System.err.println("The AWS Credentials file is located in " + System.getProperty("user.home") + "/.aws/credentials.");
        System.err.println("Note that it will expire at " + dateFormat.format(date.getTime()));
        System.err.println("After this time you may safely rerun this script to refresh your access key pair.");
        System.err.println("To use these credentials, please call the aws cli with the --profile option "
                + "(e.g. aws --profile " + profileName + " ec2 describe-instances)");
        System.err.println("You can also omit the --profile option to use the last configured profile "
                + "(e.g. aws s3 ls)");
        System.err.println("----------------------------------------------------------------------------------------------------------------------");
        System.out.println(profileName);

    }

}
