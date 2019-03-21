/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.SubnodeConfiguration;
import org.apache.commons.configuration2.convert.ListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.tree.*;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.io.*;
import java.util.*;

/**
 * <p>Customized copy of INIConfiguration tuned for use with AWS INI-like files.</p>
 * <p>Changes to support indented properties:
 * <ul>
 * <li>Right trim lines (original trims left and right)</li>
 * <li>Right trim keys (original trims left and right)</li>
 * </ul>
 * </p>
 */
public class AwsINIConfiguration extends BaseHierarchicalConfiguration implements
        FileBasedConfiguration {
    /**
     * The characters that signal the start of a comment line.
     */
    private static final String COMMENT_CHARS = "#;";

    /**
     * The characters used to separate keys from values.
     */
    private static final String SEPARATOR_CHARS = "=:";

    /**
     * Constant for the line separator.
     */
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    /**
     * The characters used for quoting values.
     */
    private static final String QUOTE_CHARACTERS = "\"'";

    /**
     * The line continuation character.
     */
    private static final String LINE_CONT = "\\";

    /**
     * The separator used when writing an INI file.
     */
    private static final String SEPARATOR_USED_IN_OUTPUT = " = ";

    /**
     * Create a new empty INI Configuration.
     */
    AwsINIConfiguration() {
        super();
    }

    /**
     * Get separator used in INI output. see {@code setSeparatorUsedInOutput}
     * for further explanation
     *
     * @return the current separator for writing the INI output
     * @since 2.2
     */
    private String getSeparatorUsedInOutput() {
        beginRead(false);
        try {
            return SEPARATOR_USED_IN_OUTPUT;
        } finally {
            endRead();
        }
    }

    /**
     * Save the configuration to the specified writer.
     *
     * @param writer - The writer to save the configuration to.
     */
    @Override
    public void write(Writer writer) {
        PrintWriter out = new PrintWriter(writer);
        boolean first = true;
        final String separator = getSeparatorUsedInOutput();

        beginRead(false);
        try {
            for (ImmutableNode node : getModel().getNodeHandler().getRootNode()
                    .getChildren()) {
                if (isSectionNode(node)) {
                    if (!first) {
                        out.println();
                    }
                    out.print("[");
                    out.print(node.getNodeName());
                    out.print("]");
                    out.println();

                    for (ImmutableNode child : node.getChildren()) {
                        writeProperty(out, child.getNodeName(),
                                child.getValue(), separator);
                    }
                } else {
                    writeProperty(out, node.getNodeName(), node.getValue(), separator);
                }
                first = false;
            }
            out.println();
            out.flush();
        } finally {
            endRead();
        }
    }

    /**
     * Load the configuration from the given reader. Note that the
     * {@code clear()} method is not called so the configuration read in will
     * be merged with the current configuration.
     *
     * @param in the reader to read the configuration from.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void read(Reader in) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(in);
        Map<String, ImmutableNode.Builder> sectionBuilders = new LinkedHashMap<>();
        ImmutableNode.Builder rootBuilder = new ImmutableNode.Builder();

        createNodeBuilders(bufferedReader, rootBuilder, sectionBuilders);
        ImmutableNode rootNode = createNewRootNode(rootBuilder, sectionBuilders);
        addNodes(null, rootNode.getChildren());
    }

    /**
     * Creates a new root node from the builders constructed while reading the
     * configuration file.
     *
     * @param rootBuilder     the builder for the top-level section
     * @param sectionBuilders a map storing the section builders
     * @return the root node of the newly created hierarchy
     */
    private static ImmutableNode createNewRootNode(
            ImmutableNode.Builder rootBuilder,
            Map<String, ImmutableNode.Builder> sectionBuilders) {
        for (Map.Entry<String, ImmutableNode.Builder> e : sectionBuilders
                .entrySet()) {
            rootBuilder.addChild(e.getValue().name(e.getKey()).create());
        }
        return rootBuilder.create();
    }

    /**
     * Reads the content of an INI file from the passed in reader and creates a
     * structure of builders for constructing the {@code ImmutableNode} objects
     * representing the data.
     *
     * @param in              the reader
     * @param rootBuilder     the builder for the top-level section
     * @param sectionBuilders a map storing the section builders
     * @throws IOException if an I/O error occurs
     */
    private void createNodeBuilders(BufferedReader in,
                                    ImmutableNode.Builder rootBuilder,
                                    Map<String, ImmutableNode.Builder> sectionBuilders)
            throws IOException {
        ImmutableNode.Builder sectionBuilder = rootBuilder;
        String line = in.readLine();
        while (line != null) {
            line = StringUtils.stripEnd(line, null);
            sectionBuilder = createBuildersForLine(in, sectionBuilders, sectionBuilder, line);

            line = in.readLine();
        }
    }

    private ImmutableNode.Builder createBuildersForLine(BufferedReader in, Map<String, ImmutableNode.Builder> sectionBuilders, ImmutableNode.Builder sectionBuilder, @Nonnull String line) throws IOException {
        if (!isCommentLine(line)) {
            if (isSectionLine(line)) {
                sectionBuilder = createSectionBuilder(sectionBuilders, line);
            } else {
                createValueBuilder(in, sectionBuilder, line);
            }
        }
        return sectionBuilder;
    }

    private void createValueBuilder(BufferedReader in, ImmutableNode.Builder sectionBuilder, String line) throws IOException {
        String key;
        String value = "";
        int index = findSeparator(line);
        if (index >= 0) {
            key = line.substring(0, index);
            value = parseValue(line.substring(index + 1), in);
        } else {
            key = line;
        }
        key = StringUtils.stripEnd(key, null);
        if (key.length() < 1) {
            // use space for properties with no key
            key = " ";
        }
        createValueNodes(sectionBuilder, key, value);
    }

    private ImmutableNode.Builder createSectionBuilder(Map<String, ImmutableNode.Builder> sectionBuilders, String line) {
        ImmutableNode.Builder sectionBuilder;
        String section = line.substring(1, line.length() - 1);
        sectionBuilder = sectionBuilders.computeIfAbsent(section, x -> new ImmutableNode.Builder());
        return sectionBuilder;
    }

    /**
     * Creates the node(s) for the given key value-pair. If delimiter parsing is
     * enabled, the value string is split if possible, and for each single value
     * a node is created. Otherwise only a single node is added to the section.
     *
     * @param sectionBuilder the section builder for adding new nodes
     * @param key            the key
     * @param value          the value string
     */
    private void createValueNodes(ImmutableNode.Builder sectionBuilder,
                                  String key, String value) {
        Collection<String> values =
                getListDelimiterHandler().split(value, false);

        for (String v : values) {
            sectionBuilder.addChild(new ImmutableNode.Builder().name(key)
                    .value(v).create());
        }
    }

    /**
     * Writes data about a property into the given stream.
     *
     * @param out   the output stream
     * @param key   the key
     * @param value the value
     */
    private void writeProperty(PrintWriter out, String key, Object value, String separator) {
        out.print(key);
        out.print(separator);
        out.print(escapeValue(value.toString()));
        out.println();
    }

    /**
     * Parse the value to remove the quotes and ignoring the comment. Example:
     *
     * <pre>
     * &quot;value&quot; ; comment -&gt; value
     * </pre>
     *
     * <pre>
     * 'value' ; comment -&gt; value
     * </pre>
     * Note that a comment character is only recognized if there is at least one
     * whitespace character before it. So it can appear in the property value,
     * e.g.:
     * <pre>
     * C:\\Windows;C:\\Windows\\system32
     * </pre>
     *
     * @param val    the value to be parsed
     * @param reader the reader (needed if multiple lines have to be read)
     * @throws IOException if an IO error occurs
     */
    private static String parseValue(String val, BufferedReader reader) throws IOException {
        StringBuilder propertyValue = new StringBuilder();
        LoopState loopState = new LoopState();
        loopState.lineContinues = false;
        String value = val.trim();

        do {
            value = parseLine(reader, propertyValue, loopState, value);
        } while (loopState.lineContinues && value != null);

        return propertyValue.toString();
    }

    private static String parseLine(BufferedReader reader, StringBuilder propertyValue, LoopState loopState, String value) throws IOException {
        loopState.quoted = value.startsWith("\"") || value.startsWith("'");
        loopState.stop = false;
        loopState.escape = false;

        char quote = loopState.quoted ? value.charAt(0) : 0;

        int i = loopState.quoted ? 1 : 0;

        StringBuilder result = new StringBuilder();
        char lastChar = 0;
        i = collectValue(loopState, value, quote, i, result, lastChar);

        String v = result.toString();
        v = checkForLineContinuation(loopState, value, i, v);
        propertyValue.append(v);

        value = handleLineContinuation(reader, propertyValue, loopState, value);
        return value;
    }

    private static String handleLineContinuation(BufferedReader reader, StringBuilder propertyValue, LoopState loopState, String value) throws IOException {
        if (loopState.lineContinues) {
            propertyValue.append(LINE_SEPARATOR);
            value = reader.readLine();
        }
        return value;
    }

    private static String checkForLineContinuation(LoopState loopState, String value, int i, String v) {
        if (!loopState.quoted) {
            v = v.trim();
            loopState.lineContinues = lineContinues(v);
            if (loopState.lineContinues) {
                // remove trailing "\"
                v = v.substring(0, v.length() - 1).trim();
            }
        } else {
            loopState.lineContinues = lineContinues(value, i);
        }
        return v;
    }

    private static int collectValue(LoopState loopState, String value, char quote, int i, StringBuilder result, char lastChar) {
        while (i < value.length() && !loopState.stop) {
            char c = value.charAt(i);

            if (loopState.quoted) {
                parseQuoted(loopState, quote, result, c);
            } else {
                parseUnquoted(loopState, result, lastChar, c);
            }

            i++;
            lastChar = c;
        }
        return i;
    }

    private static void parseUnquoted(LoopState loopState, StringBuilder result, char lastChar, char c) {
        if (isCommentChar(c) && Character.isWhitespace(lastChar)) {
            loopState.stop = true;
        } else {
            result.append(c);
        }
    }

    private static void parseQuoted(LoopState loopState, char quote, StringBuilder result, char c) {
        if ('\\' == c && !loopState.escape) {
            loopState.escape = true;
        } else if (!loopState.escape && quote == c) {
            loopState.stop = true;
        } else if (loopState.escape && quote == c) {
            loopState.escape = false;
            result.append(c);
        } else {
            if (loopState.escape) {
                loopState.escape = false;
                result.append('\\');
            }

            result.append(c);
        }
    }

    private static final class LoopState {
        boolean lineContinues;
        boolean quoted;
        boolean stop;
        boolean escape;
    }

    /**
     * Tests whether the specified string contains a line continuation marker.
     *
     * @param line the string to check
     * @return a flag whether this line continues
     */
    private static boolean lineContinues(String line) {
        String s = line.trim();
        return s.equals(LINE_CONT)
                || (s.length() > 2 && s.endsWith(LINE_CONT) && Character
                .isWhitespace(s.charAt(s.length() - 2)));
    }

    /**
     * Tests whether the specified string contains a line continuation marker
     * after the specified position. This method parses the string to remove a
     * comment that might be present. Then it checks whether a line continuation
     * marker can be found at the end.
     *
     * @param line the line to check
     * @param pos  the start position
     * @return a flag whether this line continues
     */
    private static boolean lineContinues(String line, int pos) {
        String s;

        if (pos >= line.length()) {
            s = line;
        } else {
            int end = pos;
            while (end < line.length() && !isCommentChar(line.charAt(end))) {
                end++;
            }
            s = line.substring(pos, end);
        }

        return lineContinues(s);
    }

    /**
     * Tests whether the specified character is a comment character.
     *
     * @param c the character
     * @return a flag whether this character starts a comment
     */
    private static boolean isCommentChar(char c) {
        return COMMENT_CHARS.indexOf(c) >= 0;
    }

    /**
     * Tries to find the index of the separator character in the given string.
     * This method checks for the presence of separator characters in the given
     * string. If multiple characters are found, the first one is assumed to be
     * the correct separator. If there are quoting characters, they are taken
     * into account, too.
     *
     * @param line the line to be checked
     * @return the index of the separator character or -1 if none is found
     */
    private static int findSeparator(String line) {
        int index =
                findSeparatorBeforeQuote(line,
                        findFirstOccurrence(line, QUOTE_CHARACTERS));
        if (index < 0) {
            index = findFirstOccurrence(line, SEPARATOR_CHARS);
        }
        return index;
    }

    /**
     * Checks for the occurrence of the specified separators in the given line.
     * The index of the first separator is returned.
     *
     * @param line       the line to be investigated
     * @param separators a string with the separator characters to look for
     * @return the lowest index of a separator character or -1 if no separator
     * is found
     */
    private static int findFirstOccurrence(String line, String separators) {
        int index = -1;

        for (int i = 0; i < separators.length(); i++) {
            char sep = separators.charAt(i);
            int pos = line.indexOf(sep);
            if (pos >= 0 && (index < 0 || pos < index)) {
                index = pos;
            }
        }

        return index;
    }

    /**
     * Searches for a separator character directly before a quoting character.
     * If the first non-whitespace character before a quote character is a
     * separator, it is considered the "real" separator in this line - even if
     * there are other separators before.
     *
     * @param line       the line to be investigated
     * @param quoteIndex the index of the quote character
     * @return the index of the separator before the quote or &lt; 0 if there is
     * none
     */
    private static int findSeparatorBeforeQuote(String line, int quoteIndex) {
        int index = quoteIndex - 1;
        while (index >= 0 && Character.isWhitespace(line.charAt(index))) {
            index--;
        }

        if (index >= 0 && SEPARATOR_CHARS.indexOf(line.charAt(index)) < 0) {
            index = -1;
        }

        return index;
    }

    /**
     * Escapes the given property value before it is written. This method add
     * quotes around the specified value if it contains a comment character and
     * handles list delimiter characters.
     *
     * @param value the string to be escaped
     */
    private String escapeValue(String value) {
        return String.valueOf(getListDelimiterHandler().escape(
                escapeComments(value), ListDelimiterHandler.NOOP_TRANSFORMER));
    }

    /**
     * Escapes comment characters in the given value.
     *
     * @param value the value to be escaped
     * @return the value with comment characters escaped
     */
    private static String escapeComments(String value) {
        boolean quoted = false;

        for (int i = 0; i < COMMENT_CHARS.length() && !quoted; i++) {
            char c = COMMENT_CHARS.charAt(i);
            if (value.indexOf(c) != -1) {
                quoted = true;
            }
        }

        if (quoted) {
            return '"' + value.replaceAll("\"", "\\\\\\\"") + '"';
        } else {
            return value;
        }
    }

    /**
     * Determine if the given line is a comment line.
     *
     * @param line The line to check.
     * @return true if the line is empty or starts with one of the comment
     * characters
     */
    private boolean isCommentLine(String line) {
        if (line == null) {
            return false;
        }
        // blank lines are also treated as comment lines
        return line.length() < 1 || COMMENT_CHARS.indexOf(line.charAt(0)) >= 0;
    }

    /**
     * Determine if the given line is a section.
     *
     * @param line The line to check.
     * @return true if the line contains a section
     */
    private boolean isSectionLine(String line) {
        if (line == null) {
            return false;
        }
        return line.startsWith("[") && line.endsWith("]");
    }

    /**
     * Return a set containing the sections in this ini configuration. Note that
     * changes to this set do not affect the configuration.
     *
     * @return a set containing the sections.
     */
    Set<String> getSections() {
        Set<String> sections = new LinkedHashSet<>();
        boolean globalSection = false;
        boolean inSection = false;

        beginRead(false);
        try {
            for (ImmutableNode node : getModel().getNodeHandler().getRootNode()
                    .getChildren()) {
                if (isSectionNode(node)) {
                    inSection = true;
                    sections.add(node.getNodeName());
                } else {
                    if (!inSection && !globalSection) {
                        globalSection = true;
                        sections.add(null);
                    }
                }
            }
        } finally {
            endRead();
        }

        return sections;
    }

    /**
     * Returns a configuration with the content of the specified section. This
     * provides an easy way of working with a single section only. The way this
     * configuration is structured internally, this method is very similar to
     * calling {@link HierarchicalConfiguration#configurationAt(String)} with
     * the name of the section in question. There are the following differences
     * however:
     * <ul>
     * <li>This method never throws an exception. If the section does not exist,
     * it is created now. The configuration returned in this case is empty.</li>
     * <li>If section is contained multiple times in the configuration, the
     * configuration returned by this method is initialized with the first
     * occurrence of the section. (This can only happen if
     * {@code addProperty()} has been used in a way that does not conform
     * to the storage scheme used by {@code INIConfiguration}.
     * If used correctly, there will not be duplicate sections.)</li>
     * <li>There is special support for the global section: Passing in
     * <b>null</b> as section name returns a configuration with the content of
     * the global section (which may also be empty).</li>
     * </ul>
     *
     * @param name the name of the section in question; <b>null</b> represents
     *             the global section
     * @return a configuration containing only the properties of the specified
     * section
     */
    SubnodeConfiguration getSection(String name) {
        if (name == null) {
            return getGlobalSection();
        } else {
            try {
                return (SubnodeConfiguration) configurationAt(name, true);
            } catch (ConfigurationRuntimeException iex) {
                // the passed in key does not map to exactly one node
                // obtain the node for the section, create it on demand
                InMemoryNodeModel parentModel = getSubConfigurationParentModel();
                NodeSelector selector = parentModel.trackChildNodeWithCreation(null, name, this);
                return createSubConfigurationForTrackedNode(selector, this);
            }
        }
    }

    /**
     * Creates a sub configuration for the global section of the represented INI
     * configuration.
     *
     * @return the sub configuration for the global section
     */
    private SubnodeConfiguration getGlobalSection() {
        InMemoryNodeModel parentModel = getSubConfigurationParentModel();
        NodeSelector selector = new NodeSelector(null); // selects parent
        parentModel.trackNode(selector, this);
        AwsINIConfiguration.GlobalSectionNodeModel model =
                new AwsINIConfiguration.GlobalSectionNodeModel(this, selector);
        SubnodeConfiguration sub = new SubnodeConfiguration(this, model);
        initSubConfigurationForThisParent(sub);
        return sub;
    }

    /**
     * Checks whether the specified configuration node represents a section.
     *
     * @param node the node in question
     * @return a flag whether this node represents a section
     */
    private static boolean isSectionNode(ImmutableNode node) {
        return node.getValue() == null;
    }

    /**
     * A specialized node model implementation for the sub configuration
     * representing the global section of the INI file. This is a regular
     * {@code TrackedNodeModel} with one exception: The {@code NodeHandler} used
     * by this model applies a filter on the children of the root node so that
     * only nodes are visible that are no sub sections.
     */
    private static class GlobalSectionNodeModel extends TrackedNodeModel {
        /**
         * Creates a new instance of {@code GlobalSectionNodeModel} and
         * initializes it with the given underlying model.
         *
         * @param modelSupport the underlying {@code InMemoryNodeModel}
         * @param selector     the {@code NodeSelector}
         */
        GlobalSectionNodeModel(InMemoryNodeModelSupport modelSupport,
                               NodeSelector selector) {
            super(modelSupport, selector, true);
        }

        @Override
        public NodeHandler<ImmutableNode> getNodeHandler() {
            return new NodeHandlerDecorator<ImmutableNode>() {
                @Override
                public List<ImmutableNode> getChildren(ImmutableNode node) {
                    List<ImmutableNode> children = super.getChildren(node);
                    return filterChildrenOfGlobalSection(node, children);
                }

                @Override
                public List<ImmutableNode> getChildren(ImmutableNode node,
                                                       String name) {
                    List<ImmutableNode> children =
                            super.getChildren(node, name);
                    return filterChildrenOfGlobalSection(node, children);
                }

                @Override
                public int getChildrenCount(ImmutableNode node, String name) {
                    List<ImmutableNode> children =
                            (name != null) ? super.getChildren(node, name)
                                    : super.getChildren(node);
                    return filterChildrenOfGlobalSection(node, children).size();
                }

                @Override
                public ImmutableNode getChild(ImmutableNode node, int index) {
                    List<ImmutableNode> children = super.getChildren(node);
                    return filterChildrenOfGlobalSection(node, children).get(
                            index);
                }

                @Override
                public int indexOfChild(ImmutableNode parent,
                                        ImmutableNode child) {
                    List<ImmutableNode> children = super.getChildren(parent);
                    return filterChildrenOfGlobalSection(parent, children)
                            .indexOf(child);
                }

                @Override
                protected NodeHandler<ImmutableNode> getDecoratedNodeHandler() {
                    return AwsINIConfiguration.GlobalSectionNodeModel.super.getNodeHandler();
                }

                /**
                 * Filters the child nodes of the global section. This method
                 * checks whether the passed in node is the root node of the
                 * configuration. If so, from the list of children all nodes are
                 * filtered which are section nodes.
                 *
                 * @param node the node in question
                 * @param children the children of this node
                 * @return a list with the filtered children
                 */
                private List<ImmutableNode> filterChildrenOfGlobalSection(
                        ImmutableNode node, List<ImmutableNode> children) {
                    List<ImmutableNode> filteredList;
                    if (node == getRootNode()) {
                        filteredList =
                                new ArrayList<>(children.size());
                        for (ImmutableNode child : children) {
                            if (!isSectionNode(child)) {
                                filteredList.add(child);
                            }
                        }
                    } else {
                        filteredList = children;
                    }

                    return filteredList;
                }
            };
        }
    }
}
