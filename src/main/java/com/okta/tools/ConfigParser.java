package com.okta.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

/**
 * Created by Johan Lyheden <Johan.Lyheden@UNIBET.com> on 02/12/16.
 */
public class ConfigParser {

    private static final Logger LOGGER = LogManager.getLogger(ConfigParser.class);

    private String oktaOrg;
    private String oktaAWSAppURL;
    private String awsIamKey;
    private String awsIamSecret;
    private String awsRegion;

    public static String HOME_DIR_FILE = System.getProperty("user.home") + System.getProperty("file.separator") + ".okta-aws.properties";
    public static String WORK_DIR_FILE = System.getProperty("user.dir") + System.getProperty("file.separator") + "config.properties";

    public ConfigParser() {
    }

    public String getOktaOrg() {
        return oktaOrg;
    }

    public void setOktaOrg(String oktaOrg) {
        this.oktaOrg = oktaOrg;
    }

    public String getOktaAWSAppURL() {
        return oktaAWSAppURL;
    }

    public void setOktaAWSAppURL(String oktaAWSAppURL) {
        this.oktaAWSAppURL = oktaAWSAppURL;
    }

    public String getAwsIamKey() {
        return awsIamKey;
    }

    public void setAwsIamKey(String awsIamKey) {
        this.awsIamKey = awsIamKey;
    }

    public String getAwsIamSecret() {
        return awsIamSecret;
    }

    public void setAwsIamSecret(String awsIamSecret) {
        this.awsIamSecret = awsIamSecret;
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public void setAwsRegion(String awsRegion) {
        this.awsRegion = awsRegion;
    }

    public static ConfigParser getConfig() throws IOException {
        FileReader reader = null;

        if (new File(WORK_DIR_FILE).exists()) {
            LOGGER.info("Using configuration file from: " + WORK_DIR_FILE);
            reader = new FileReader(new File(WORK_DIR_FILE));
        } else if (new File(HOME_DIR_FILE).exists()) {
            LOGGER.info("Using configuration file from: " + HOME_DIR_FILE);
            reader = new FileReader(new File(HOME_DIR_FILE));
        } else {
            throw new FileNotFoundException("Neither " + HOME_DIR_FILE + " or " + WORK_DIR_FILE + " configuration files found");
        }

        Properties props = new Properties();
        props.load(reader);

        ConfigParser config = new ConfigParser();
        config.setOktaOrg(props.getProperty("OKTA_ORG"));
        config.setOktaAWSAppURL(props.getProperty("OKTA_AWS_APP_URL"));
        config.setAwsIamKey(props.getProperty("AWS_IAM_KEY"));
        config.setAwsIamSecret(props.getProperty("AWS_IAM_SECRET"));
        config.setAwsRegion(props.getProperty("AWS_DEFAULT_REGION", "us-east-1"));

        return config;
    }

}
