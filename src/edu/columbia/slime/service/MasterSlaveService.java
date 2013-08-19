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

public abstract class MasterSlaveService extends Service {
	private static final String ATTR_NAME_PORT = "port";
	SocketEvent se;
	ServerSocketChannel ssc;
	SocketChannel sc;

	int port = 0;

	protected Queue<SocketChannel> clientSockets = new LinkedList<SocketChannel>();

	private final void initMaster() throws IOException {
		ssc = ServerSocketChannel.open();
		ssc.socket().bind(new InetSocketAddress(port));
		ssc.configureBlocking(false);
		LOG.info("Created a [master] service'" + getName() + "' at port " + port);

		se = new SocketEvent(ssc);
		register(se);
	}

	private final void closeMaster() throws IOException {
		cancel(se);

		ssc.close();
	}

	private final void initSlave() throws IOException {
		sc = SocketChannel.open();
		sc.socket().connect(new InetSocketAddress(Slime.getInstance().getConfig().get("master"), port));
		sc.configureBlocking(false);
		sc.socket().setTcpNoDelay(true);
		LOG.info("Created a [slave] service '" + getName() + "' connected to port " + port);
		sc.write(java.nio.ByteBuffer.wrap("Hello".getBytes()));

		se = new SocketEvent(sc);
		register(se);
	}

	private final void closeSlave() throws IOException {
		cancel(se);

		sc.close();
	}

	public final void init() throws IOException {
		try {
			if (getDefaultConfig() == null || getDefaultConfig().get(ATTR_NAME_PORT) == null) {
				LOG.error("Cannot find 'port' attribute for the service '" + getName());
				throw new IOException("Cannot find 'port' attribute for the service '" + getName());
			}
			port = Integer.parseInt(getDefaultConfig().get(ATTR_NAME_PORT));
		} catch (Exception e) {
			LOG.error("Error initializing the service " + getName());
			e.printStackTrace();
			throw new IOException(e);
		}
		if (Slime.isMaster()) {
			initMaster();
		}
		else {
			initSlave();
		}
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
			clientSockets.add(sc);
			register(new SocketEvent(sc));
			LOG.info("Opened a new connection from " + sc.socket());
		}
		if ((se.getReadyOps() & SelectionKey.OP_READ) != 0) {
			LOG.debug("Reading from a connection " + e);
			read((SocketChannel) se.getSocket());
		}
		register(se);
	}

	public void dispatchMessage(MessageEvent me) throws IOException {
	}

	public void dispatchTimer(TimerEvent te) throws IOException {
	}

	public abstract void read(SocketChannel sc) throws IOException;

	/* inherited from Closeable */
	public void close() throws IOException {
		if (Slime.isMaster()) {
			closeMaster();
		}
		else {
			closeSlave();
		}
	}
}
