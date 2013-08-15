package edu.columbia.slime.service;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;

public abstract class BasicNetworkService extends Service {
	private static final String ATTR_NAME_PORT = "port";
	ServerSocketChannel ssc;

	public final void init() throws IOException {
		int port = 0;
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
		ssc = ServerSocketChannel.open();
		ssc.socket().bind(new InetSocketAddress(port));
		ssc.configureBlocking(false);
		LOG.info("Opened a service '" + getName() + "' at port " + port);

		register(new SocketEvent(ssc));
	}

	public final void dispatch(Event e) throws IOException {
		if (!(e instanceof SocketEvent))
			throw new IllegalStateException();

		SocketEvent se = (SocketEvent) e;
		if (se.getSelectionKey().isAcceptable()) {
			ServerSocketChannel ssc = (ServerSocketChannel) se.getSocket();
			register(new SocketEvent(ssc.accept()));
		}
		if (se.getSelectionKey().isReadable()) {
			read((SocketChannel) se.getSocket());
		}
	}

	public abstract void read(SocketChannel sc) throws IOException;

	/* inherited from Closeable */
	public void close() throws IOException {
		ssc.close();
	}
}
