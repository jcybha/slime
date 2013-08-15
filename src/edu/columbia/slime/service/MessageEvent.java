package edu.columbia.slime.service;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;

public class MessageEvent extends Event {
	public MessageEvent() {
	}

        @SuppressWarnings("unchecked")
        public void registerEvent(EventListFeeder elf, Selector selector) {
                Collection<MessageEvent> c = (Collection<MessageEvent>) elf.getMessageEventList();
		c.add(this);
	}

        public void cancelEvent(EventListFeeder elf) {
		elf.getMessageEventList().remove(this);
	}
}
