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
	private String destination;
	private Service origination;

	private String message;

	public MessageEvent(String destination, Service origination, String message) {
		this.destination = destination;
		this.origination = origination;

		this.message = message;
	}

	public String getDestination() {
		return destination;
	}

	public Service getOrignation() {
		return origination;
	}

	public String getMessage() {
		return message;
	}

        @SuppressWarnings("unchecked")
        public void registerEvent(EventListFeeder elf, Selector selector) {
                Collection<MessageEvent> c = (Collection<MessageEvent>) elf.getMessageEventList();
		synchronized (c) {
			c.add(this);
		}
	}

        public void unregisterEvent(EventListFeeder elf, Selector selector) {
		cancelEvent(elf);
	}

	@SuppressWarnings("unchecked")
        public void cancelEvent(EventListFeeder elf) {
                Collection<MessageEvent> c = (Collection<MessageEvent>) elf.getMessageEventList();
		synchronized (c) {
			c.remove(this);
		}
	}
}
