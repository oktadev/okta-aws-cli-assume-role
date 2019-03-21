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
package com.okta.tools.aws.settings;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.configuration2.tree.ImmutableNode;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A simple abstract class used to load AWS CLI settings files like the `credentials` or `config` files.
 */
public abstract class Settings {

    private final AwsINIConfiguration awsINIConfiguration;

    /**
     * Create a Settings object from a given {@link java.io.Reader}. The data given by this {@link java.io.Reader} should
     * be INI-formatted.
     *
     * @param reader The settings we want to work with. N.B.: The reader is consumed by the constructor.
     * @throws IOException Thrown when we cannot read or load from the given {@param reader}.
     */
    Settings(Reader reader) throws IOException {
        awsINIConfiguration = new AwsINIConfiguration();
        awsINIConfiguration.read(reader);
    }

    /**
     * Save the settings object to a given {@link java.io.Writer}. The caller is responsible for closing {@param writer}.
     *
     * @param writer The writer we use to write the settings to.
     */
    public void save(Writer writer) {
        awsINIConfiguration.write(writer);
    }

    String getProperty(String section, String key) {
        return awsINIConfiguration.getSection(section).get(String.class, key);
    }

    void setProperty(String section, String key, String value) {
        awsINIConfiguration.getSection(section).setProperty(key, value);
    }

    void clearProperty(String section, String key) {
        awsINIConfiguration.getSection(section).clearProperty(key);
    }

    void clearSection(String section) {
        awsINIConfiguration.getSection(section).clear();
    }

    boolean containsProperty(String section, String key) {
        return awsINIConfiguration.getSection(section).containsKey(key);
    }

    boolean isEmpty() {
        return awsINIConfiguration.isEmpty();
    }

    Set<String> getSections() {
        return awsINIConfiguration.getSections();
    }

    @VisibleForTesting
    Map<String, Object> sectionToMap(String section) {
        return awsINIConfiguration.getSection(section).getNodeModel().getRootNode().getChildren().stream()
                .collect(Collectors.toMap(ImmutableNode::getNodeName, ImmutableNode::getValue));
    }
}
