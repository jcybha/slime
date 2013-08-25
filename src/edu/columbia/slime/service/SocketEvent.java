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
        public void registerEvent(EventListFeeder elf, final Selector selector) {
		final Collection<SocketEvent> c = (Collection<SocketEvent>) elf.getSocketEventList();
		final SelectableChannel sc = s;
		final SocketEvent se = this;

		Slime.getInstance().enqueueRun(new Runnable() {
			public void run() {
				c.add(se);

				try {
					sc.configureBlocking(false);
					sc.register(selector, (sc.validOps() & ~(SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT)), se);
				} catch (ClosedChannelException cce) {
					LOG.info("CCE: " + cce);
				} catch (Exception e) {
					LOG.info("Exception: " + e);
					e.printStackTrace();
				}
			}
		});
        }

	@SuppressWarnings("unchecked")
        public void unregisterEvent(EventListFeeder elf, final Selector selector) {
		final Collection<SocketEvent> c = (Collection<SocketEvent>) elf.getSocketEventList();
		final SelectableChannel sc = s;
		final SocketEvent se = this;

		Slime.getInstance().enqueueRun(new Runnable() {
			public void run() {
				c.remove(se);

				try {
					sc.register(selector, 0, se);
				} catch (ClosedChannelException cce) {
					LOG.info("CCE: " + cce);
				} catch (Exception e) {
					LOG.info("Exception: " + e);
					e.printStackTrace();
				}
			}
		});
        }

        public void cancelEvent(EventListFeeder elf) {
		elf.getSocketEventList().remove(this);
/*
		if (sk != null)
			sk.cancel();
		sk = null;
*/
        }

	public SelectableChannel getSocket() {
		return s;
	}
}
