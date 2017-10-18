package com.okta.tools.aws.settings;

import org.ini4j.Profile;

import java.io.IOException;
import java.io.Reader;

public class Configuration extends Settings {

    static final String ROLE_ARN = "role_arn";
    static final String SOURCE_PROFILE = "source_profile";
    static final String REGION = "region";
    static final String REGION_DEFAULT = "us-east-1";

    /**
     * Create a Configuration object from a given {@link Reader}. The data given by this {@link Reader} should
     * be INI-formatted.
     *
     * @param reader The settings we want to work with.
     * @throws IOException Thrown when we cannot read or load from the given {@param reader}.
     */
    public Configuration(Reader reader) throws IOException {
        super(reader);
    }

    public void addOrUpdateProfile(String name, String roleToAssume) {
        // profileName is the string used for the section in the AWS config file.
        // This should be prefixed with "profile ".
        final String profileName = "profile " + name;
        final Profile.Section awsProfile = settings.get(profileName) != null ? settings.get(profileName) : settings.add(profileName);
        writeConfigurationProfile(awsProfile, name, roleToAssume);
    }

    private void writeConfigurationProfile(Profile.Section awsProfile, String name, String roleToAssume) {
        awsProfile.put(ROLE_ARN, roleToAssume);
        awsProfile.put(SOURCE_PROFILE, name);
        awsProfile.put(REGION, REGION_DEFAULT);
    }
}
