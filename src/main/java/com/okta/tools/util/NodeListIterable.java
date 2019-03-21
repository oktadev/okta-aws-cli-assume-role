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
package com.okta.tools.util;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Iterator;
import java.util.NoSuchElementException;

public final class NodeListIterable implements Iterable<Node>
{
    private final NodeList nodeList;

    public NodeListIterable(NodeList nodeList) {
        this.nodeList = nodeList;
    }

    @Override
    public Iterator<Node> iterator() {
        return new NodeListIterator();
    }

    public final class NodeListIterator implements Iterator<Node>
    {
        private int index = 0;
        private int length = nodeList.getLength();

        @Override
        public boolean hasNext() {
            return index < length;
        }

        @Override
        public Node next() {
            Node item = nodeList.item(index);
            if (item == null) {
                throw new NoSuchElementException();
            }
            index = index + 1;
            return item;
        }
    }
}
