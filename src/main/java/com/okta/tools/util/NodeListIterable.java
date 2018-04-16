package com.okta.tools.util;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Iterator;

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

        @Override
        public boolean hasNext() {
            return index < nodeList.getLength();
        }

        @Override
        public Node next() {
            return nodeList.item(index++);
        }
    }
}
