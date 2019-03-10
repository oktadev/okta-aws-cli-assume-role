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

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {

    private Settings settingsFromConfig(String config) throws IOException {
        return new Settings(new StringReader(config)) {};
    }

    private String writeSettings(Settings settings) throws IOException {
        StringWriter writer = new StringWriter();
        settings.save(writer);
        return writer.toString();
    }

    @Test
    void save_EmptyAddsTrailingNewline() throws IOException {
        Settings settings = settingsFromConfig(
                ""
        );
        String writtenSettings = writeSettings(settings);
        String expected = normalizeNewlines(
                "\n");
        assertEquals(expected, writtenSettings);
    }

    @Test
    void save_BlankStripsExtraTrailingNewlines() throws IOException {
        Settings settings = settingsFromConfig(
                "\n\n\n"
        );
        String writtenSettings = writeSettings(settings);
        String expected = normalizeNewlines(
                "\n");
        assertEquals(expected, writtenSettings);
    }

    @Test
    void save_WithContentAddsTrailingNewline() throws IOException {
        Settings settings = settingsFromConfig(
                "key=value"
        );
        String writtenSettings = writeSettings(settings);
        String expected = normalizeNewlines(
                "key = value\n" +
                "\n");
        assertEquals(expected, writtenSettings);
    }

    @Test
    void save_PreservesNestedProperties() throws IOException {
        Settings settings = settingsFromConfig(
                "[profile development]\n" +
                "aws_access_key_id=foo\n" +
                "aws_secret_access_key=bar\n" +
                "s3 =\n" +
                "  max_concurrent_requests = 20\n" +
                "  max_queue_size = 10000\n" +
                "\n"
        );
        String writtenSettings = writeSettings(settings);
        // Extra spaces in the output appear not to affect AWS CLI
        String expected = normalizeNewlines(
                "[profile development]\n" +
                "aws_access_key_id = foo\n" + // DIVERGENCE: extra space
                "aws_secret_access_key = bar\n" + // DIVERGENCE: extra space
                "s3 = \n" + // DIVERGENCE: extra space
                "  max_concurrent_requests = 20\n" +
                "  max_queue_size = 10000\n" +
                "\n");
        assertEquals(expected, writtenSettings);
    }

    @Test
    void getProperty_FromGlobal() throws IOException {
        Settings settings = settingsFromConfig(
                "key=value\n" +
                "\n"
        );
        String actualValue = settings.getProperty(null, "key");
        String expectedValue = "value";
        assertEquals(expectedValue, actualValue);
    }


    @Test
    void getProperty_InSection() throws IOException {
        Settings settings = settingsFromConfig(
                "[myCoolSection]\n" +
                "key=value\n" +
                "\n"
        );
        String actualValue = settings.getProperty("myCoolSection", "key");
        String expectedValue = "value";
        assertEquals(expectedValue, actualValue);
    }

    @Test
    void getProperty_NullWhenDoesntExist() throws IOException {
        Settings settings = settingsFromConfig(
                "[myCoolSection]\n" +
                "key=value\n" +
                "\n"
        );
        String actualValue = settings.getProperty("myCoolSection", "keyNotThere");
        assertNull(actualValue);
    }

    @Test
    void getProperty_EmptyWhenNoValue() throws IOException {
        Settings settings = settingsFromConfig(
                "[myCoolSection]\n" +
                "key\n" +
                "\n"
        );
        String actualValue = settings.getProperty("myCoolSection", "key");
        String expectedValue = "";
        assertEquals(expectedValue, actualValue);
    }

    @Test
    void getProperty_EmptyWhenBlankValue() throws IOException {
        Settings settings = settingsFromConfig(
                "[myCoolSection]\n" +
                "key=\n" +
                "\n"
        );
        String actualValue = settings.getProperty("myCoolSection", "key");
        String expectedValue = "";
        assertEquals(expectedValue, actualValue);
    }

    // Don't use this for reading nested properties (sadness will ensue)
    @Test
    void getProperty_FirstNestedPropertyWins() throws IOException {
        Settings settings = settingsFromConfig(
                "[profile development]\n" +
                "aws_access_key_id=foo\n" +
                "aws_secret_access_key=bar\n" +
                "s3 =\n" +
                "  max_concurrent_requests = 20\n" +
                "  max_queue_size = 10000\n" +
                "dynamodb =\n" +
                "  max_concurrent_requests = 30\n" +
                "\n"
        );
        // Nested properties are preserved, but getting them is not supported
        // If you need this, roll up your sleeves and add it to AwsINIConfiguration
        String actualValue = settings.getProperty("profile development", "  max_concurrent_requests");
        String expectedValue = "20";
        assertEquals(expectedValue, actualValue);
    }

    @Test
    void setProperty_CanBeGotten() throws IOException {
        Settings settings = settingsFromConfig(
                ""
        );
        settings.setProperty("default", "key", "value");
        String actualValue = settings.getProperty("default", "key");
        String expectedValue = "value";
        assertEquals(expectedValue, actualValue);
    }

    @Test
    void setProperty_WrittenToFile() throws IOException {
        Settings settings = settingsFromConfig(
                ""
        );
        settings.setProperty("default", "key", "value");
        String writtenSettings = writeSettings(settings);
        String expected = normalizeNewlines(
                "[default]\n" +
                "key = value\n" +
                "\n");
        assertEquals(expected, writtenSettings);
    }

    // Don't use this for editing nested properties (sadness will ensue)
    @Test
    void setProperty_WritingNestedProperties() throws IOException {
        Settings settings = settingsFromConfig(
                "[profile development]\n" +
                "s3 =\n" +
                "  max_queue_size = 10000\n" +
                "aws_access_key_id=foo\n" +
                "aws_secret_access_key=bar\n" +
                "\n"
        );
        // Nested properties are preserved, but setting them is not supported
        settings.setProperty("profile development", "  max_concurrent_requests", "30");
        String writtenSettings = writeSettings(settings);
        // Extra spaces in the output appear not to affect AWS CLI
        String expected = normalizeNewlines(
                "[profile development]\n" +
                "s3 = \n" + // DIVERGENCE: extra space
                "  max_queue_size = 10000\n" +
                "aws_access_key_id = foo\n" + // DIVERGENCE: extra space
                "aws_secret_access_key = bar\n" + // DIVERGENCE: extra space
                "  max_concurrent_requests = 30\n" + // WAT! This is not what you likely wanted
                "\n");
        assertEquals(expected, writtenSettings);
    }

    @Test
    void clearSection() throws IOException {
        Settings settings = settingsFromConfig(
                "[myCoolSection]\n" +
                "stuffInSection=isAlsoCool\n" +
                "[anotherSection]\n"
        );
        settings.clearSection("myCoolSection");
        String writtenSettings = writeSettings(settings);
        String expected = normalizeNewlines(
                "[anotherSection]\n" +
                "\n");
        assertEquals(expected, writtenSettings);
    }

    @Test
    void containsProperty() throws IOException {
        Settings settings = settingsFromConfig(
            "[myCoolSection]\n" +
                "stuffInSection=isAlsoCool\n" +
                "[anotherSection]\n"
        );
        assertTrue(settings.containsProperty("myCoolSection", "stuffInSection"));
    }

    @Test
    void isEmpty_TrueOnBlankConfig() throws IOException {
        Settings settings = settingsFromConfig(
                ""
        );
        assertTrue(settings.isEmpty());
    }

    @Test
    void isEmpty_FalseOnTrivialConfig() throws IOException {
        Settings settings = settingsFromConfig(
                "key=value"
        );
        assertFalse(settings.isEmpty());
    }

    @Test
    void getSections() throws IOException {
        Settings settings = settingsFromConfig(
                "[myCoolSection]\n" +
                "stuffInSection=isAlsoCool\n" +
                "[anotherSection]\n"
        );
        Set<String> sections = settings.getSections();
        assertEquals(sections.size(), 2);
        assertTrue(sections.contains("myCoolSection"));
        assertTrue(sections.contains("anotherSection"));
    }

    @Test
    void sectionToMap_EmptyOnEmptySection() throws IOException {
        Settings settings = settingsFromConfig(
                "[myCoolSection]\n" +
                "stuffInSection=isAlsoCool\n" +
                "moreStuff=isLessCool\n" +
                "[anotherSection]\n"
        );
        Map<String, Object> anotherSection = settings.sectionToMap("anotherSection");
        assertTrue(anotherSection.isEmpty());
    }

    @Test
    void sectionToMap_HasAllAndOnlySectionProperties() throws IOException {
        Settings settings = settingsFromConfig(
                "[myCoolSection]\n" +
                "stuffInSection=isAlsoCool\n" +
                "moreStuff=isLessCool\n" +
                "[anotherSection]\n"
        );
        Map<String, Object> myCoolSection = settings.sectionToMap("myCoolSection");
        assertEquals(myCoolSection.size(), 2);
        assertTrue(myCoolSection.containsKey("stuffInSection"));
        assertEquals("isAlsoCool", myCoolSection.get("stuffInSection"));
        assertEquals("isLessCool", myCoolSection.get("moreStuff"));
    }

    private String normalizeNewlines(String lineFeedOnlyString) {
        return lineFeedOnlyString.replaceAll("\n", System.getProperty("line.separator"));
    }
}