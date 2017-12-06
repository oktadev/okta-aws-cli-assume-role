/*
 * Copyright 2017 Okta
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

    // the name of the aws cli "default" profile
    protected static final String DEFAULTPROFILENAME = "default";

    /**
     * Create a Settings object from a given {@link java.io.Reader}. The data given by this {@link java.io.Reader} should
     * be INI-formatted.
     * @param reader The settings we want to work with. N.B.: The reader is consumed by the constructor.
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
