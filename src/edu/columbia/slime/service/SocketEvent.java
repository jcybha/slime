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
        public static final Log LOG = LogFactory.getLog(Slime.class);

	protected SelectableChannel s;

	SelectionKey sk;
	int readyOps;

	public SocketEvent(SelectableChannel s) {
		this.s = s;
	}

	public void setReadyOps(int readyOps) {
		this.readyOps = readyOps;
	}

	public int getReadyOps() {
		return readyOps;
	}

	@SuppressWarnings("unchecked")
        public void registerEvent(EventListFeeder elf, Selector selector) {
		Collection<SocketEvent> c = (Collection<SocketEvent>) elf.getSocketEventList();
		c.add(this);

		try {
LOG.info("regist isClient? " + (s instanceof SocketChannel) + " validOps: " + s.validOps());
			s.configureBlocking(false);
			sk = s.register(selector,
					s.validOps(), this);
LOG.info("got SelectionKey: " + sk);
		} catch (ClosedChannelException cce) {
			LOG.info("CCE: " + cce);
		} catch (Exception e) {
			LOG.info("Exception: " + e);
			e.printStackTrace();
		}
LOG.info("WTF happened?");
        }

	@SuppressWarnings("unchecked")
        public void unregisterEvent(EventListFeeder elf, Selector selector) {
		Collection<SocketEvent> c = (Collection<SocketEvent>) elf.getSocketEventList();
		c.remove(this);

		try {
LOG.info("unreg isClient? " + (s instanceof SocketChannel) + " validOps: " + s.validOps());
			sk = s.register(selector,
					0, this);
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
