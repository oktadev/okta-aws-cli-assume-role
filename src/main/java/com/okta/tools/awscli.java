package com.okta.tools;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.okta.tools.UserChoiceSelect.logger;

public class awscli {

    public static void main(String[] args) throws IOException, ParseException {


        Options options = new Options();


        options.addOption(Option.builder("a").longOpt("answers")
                .desc("a properties file mapping questions to predefined answers. Regex supported")
                .hasArg()
                .build());

        options.addOption(Option.builder("p").longOpt("password-name")
                .desc("You can supply a password from an environment variable.  Note this is the NAME and not the value")
                .build());

        options.addOption(Option.builder("debug")
                .type(Boolean.class)
                .desc("set to debug mode")
                .build());

        options.addOption(Option.builder("u")
                .longOpt("username")
                .hasArg()
                .desc("What user should log in.  Note that you can also supply this in your answers file.  CLI will take precedence")
                .build());
        options.addOption(Option.builder("h")
            .longOpt("help")
            .type(Boolean.class)
            .desc("Print this help message")
            .build());
        CommandLineParser parser = new DefaultParser();
        CommandLine line = parser.parse(options, args);
        if(line.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "okta_launch", options );
            System.exit(-1);
        }
        if(line.hasOption("debug")) {
            org.apache.logging.log4j.core.config.Configurator.setLevel("com.okta.tools", Level.DEBUG);

            logger.debug("Arguments are:{}",line.getArgList());
        }


        // Declare username outside of other blocks to allow us to
        // load from answers
        String username = null;
        UserChoiceSelect chooser;
        if(line.hasOption("answers")) {
            String answerFileName = line.getOptionValue("answers");
            logger.debug("Looking for answers {} and user {}",answerFileName,line.getOptionValue("username"));
            logger.debug("Arguments are:{}",line.getArgList());

            File answersFile = new File(answerFileName);
            if(!answersFile.exists()) {
                System.err.println("Cannot find the answers file");
                System.exit(-1);
            }
            FileReader reader = new FileReader(answersFile);
            Properties props = new Properties();
            props.load(reader);

            Map<String,String> configMap = new HashMap(props);
            chooser = new ConfigThenInput(new ConfigChoice(configMap));
            if (configMap.containsKey("username")) {
                username = configMap.get("username");
            }
        } else {
            chooser = new InputChoice();
        }
        if(line.hasOption("username")) {
            username = line.getOptionValue("username");
        }

        // How do we get Creds
        UsernameRetriever usernameRetriever;
        if(username != null) {
            //Need to declare final for inner class
            final String  user = username;
            usernameRetriever = new UsernameRetriever() {
                public String getUsername() {
                    return user;
                }
            };
        } else {
            usernameRetriever = new stdinUsernameRetiever();
        }

        PasswordRetriever passwordRetriever;

        String password;
        if(line.hasOption("password-name")) {
            passwordRetriever = new PasswordRetriever() {
                @Override
                public String getPassword() {
                    return System.getenv(line.getOptionValue("password-name"));
                }
            };
        } else {
            passwordRetriever = new stdinPasswordRetriever();
        }

        CredRetriever retriever = new CredRetriever(passwordRetriever,usernameRetriever);
        OktaAWSIntegration oA = new OktaAWSIntegration(chooser,retriever);
        String profileName = oA.authenticateAndSetupProfile();
        resultMessage(profileName);

        System.out.println(profileName);

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

    }

}
