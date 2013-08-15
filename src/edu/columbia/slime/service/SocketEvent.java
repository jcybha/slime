package edu.columbia.slime.service;

import java.util.Collection;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ClosedChannelException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;

public class SocketEvent extends Event {
	protected SelectableChannel s;

	SelectionKey sk;

	public SocketEvent(SelectableChannel s) {
		this.s = s;
	}

	@SuppressWarnings("unchecked")
        public void registerEvent(EventListFeeder elf, Selector selector) {
		Collection<SocketEvent> c = (Collection<SocketEvent>) elf.getSocketEventList();
		c.add(this);

		try {
			sk = s.register(selector,
					s.validOps(), this);
		} catch (ClosedChannelException cce) {
			cce.printStackTrace();
		}
        }

        public void cancelEvent(EventListFeeder elf) {
		elf.getSocketEventList().remove(this);

		if (sk != null)
			sk.cancel();
		sk = null;
        }

	public SelectionKey getSelectionKey() {
		return sk;
	}

	public SelectableChannel getSocket() {
		return s;
	}
}
