package com.okta.tools.aws.settings;

import org.ini4j.Config;
import org.ini4j.Ini;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * A simple abstract class used to load AWS CLI settings files like the `credentials` or `config` files.
 */
public abstract class Settings {

    final Ini settings = new Ini();

    /**
     * Create a Settings object from a given {@link java.io.Reader}. The data given by this {@link java.io.Reader} should
     * be INI-formatted.
     * @param reader The settings we want to work with.
     * @throws IOException Thrown when we cannot read or load from the given {@param reader}.
     */
    Settings(Reader reader) throws IOException {
        // Don't escape special characters. By default ini4j escapes ':' to '\:', which is a problem in ARN's.
        Config.getGlobal().setEscape(false);
        settings.load(reader);
    }

    /**
     * Save the settings object to a given {@link java.io.Writer}. The caller is responsible for closing {@param writer}.
     * @param writer The writer we use to write the settings to.
     * @throws IOException Thrown when we cannot write to {@param writer}.
     */
    public void save(Writer writer) throws IOException {
        settings.store(writer);
    }
}
