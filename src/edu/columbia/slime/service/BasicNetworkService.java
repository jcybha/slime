package edu.columbia.slime.service;

import java.io.Closeable;
import java.io.IOException;
import java.util.Queue;
import java.util.LinkedList;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;

public abstract class BasicNetworkService extends Service {
	private static final String ATTR_NAME_PORT = "port";
	SocketEvent se;
	ServerSocketChannel ssc;
	SocketChannel sc;

	int port = 0;

	protected Queue<SocketChannel> clientSockets = new LinkedList<SocketChannel>();

	public final void init() throws IOException {
		ssc = ServerSocketChannel.open();
		ssc.socket().bind(new InetSocketAddress(port));
		ssc.configureBlocking(false);
		LOG.info("Created a BasicNet service'" + getName() + "' at port " + port);

		se = new SocketEvent(ssc);
		register(se);

		initCustom();
	}

	public final void close() throws IOException {
		closeCustom();

		cancel(se);

		ssc.close();
	}

	public final void dispatch(Event e) throws IOException {
		if (e instanceof MessageEvent) {
			dispatchMessage((MessageEvent) e);
			return;
		}
		if (e instanceof TimerEvent) {
			dispatchTimer((TimerEvent) e);
			return;
		}
		SocketEvent se = (SocketEvent) e;
		if ((se.getReadyOps() & SelectionKey.OP_ACCEPT) != 0) {
			ServerSocketChannel ssc = (ServerSocketChannel) se.getSocket();
			SocketChannel sc = ssc.accept();
			sc.socket().setTcpNoDelay(true);
			clientSockets.add(sc);
			register(new SocketEvent(sc));
			newConnection(sc);

			LOG.info("Opened a new connection from " + sc.socket());
		}
		if ((se.getReadyOps() & SelectionKey.OP_READ) != 0) {
			LOG.debug("Reading from a connection " + e);
			try {
				read((SocketChannel) se.getSocket());
			} catch (IOException ioe) {
				clientSockets.remove(sc);
				closedConnection(sc);

				LOG.info("Closed a connection to " + sc.socket() + " due to " + ioe);
				throw ioe;
			}
		}
		register(se);
	}

	public void dispatchMessage(MessageEvent me) throws IOException {
	}

	public void dispatchTimer(TimerEvent te) throws IOException {
	}

	public boolean newConnection(SocketChannel sc) throws IOException {
		return false;
	}

	public void closedConnection(SocketChannel sc) throws IOException {
	}

	public abstract void read(SocketChannel sc) throws IOException;

	public void initCustom() throws IOException {
	}

	public void closeCustom() throws IOException {
	}
}
