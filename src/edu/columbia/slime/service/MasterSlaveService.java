package edu.columbia.slime.service;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Queue;
import java.util.LinkedList;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;

public abstract class MasterSlaveService extends Service {
	private static final String ATTR_NAME_PORT = "port";
	private static final int MAGIC = 0x48031027;

	private static final int READ_RETRY_SLEEP = 500;
	private static final int READ_RETRY_CNT = 10;

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

		initMasterCustom();
	}

	private final void closeMaster() throws IOException {
		closeMasterCustom();

		cancel(se);

		ssc.close();
	}

	private final void initSlave() throws IOException {
		sc = SocketChannel.open();
		sc.socket().connect(new InetSocketAddress(Slime.getInstance().getConfig().get("master"), port));
		sc.configureBlocking(false);
		sc.socket().setTcpNoDelay(true);
		LOG.info("Created a [slave] service '" + getName() + "' connected to port " + port);
		//sc.write(java.nio.ByteBuffer.wrap("Hello".getBytes()));

		se = new SocketEvent(sc);
		register(se);

		initSlaveCustom();
	}

	private final void closeSlave() throws IOException {
		closeSlaveCustom();

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
			sc.socket().setTcpNoDelay(true);
			clientSockets.add(sc);
			register(new SocketEvent(sc));

			newConnection(sc);

			LOG.info("Opened a new connection from " + sc.socket());
		}
		if ((se.getReadyOps() & SelectionKey.OP_READ) != 0) {
			LOG.debug("Reading from a connection " + e);
			readInternal((SocketChannel) se.getSocket());
		}
		register(se);
	}

	public void initMasterCustom() throws IOException {
	}

	public void closeMasterCustom() throws IOException {
	}

	public void initSlaveCustom() throws IOException {
	}

	public void closeSlaveCustom() throws IOException {
	}

	public void newConnection(SocketChannel sc) throws IOException {
	}

	public void dispatchMessage(MessageEvent me) throws IOException {
	}

	public void dispatchTimer(TimerEvent te) throws IOException {
	}

	public abstract void read(SocketChannel sc, Object obj) throws IOException;
	public abstract void read(SocketChannel sc, ByteBuffer bb) throws IOException;

	protected final void readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
		int i;
		int readBytes = 0;
		for (i = 0; i < READ_RETRY_CNT; i++) {
			readBytes += sc.read(bb);
			LOG.trace("readBytes:" + readBytes + " bb.pos: " + bb.position() + " bb.limit: " + bb.limit() + " rem: " + bb.remaining());
			if (bb.remaining() == 0) {
//				bb.flip();
				return;
			}
			try {
				Thread.sleep(READ_RETRY_SLEEP);
			} catch (Exception e) { }
		}
		throw new IOException("Couldn't read fully (" + readBytes + "/" + bb.limit() + ") for " + (READ_RETRY_SLEEP * READ_RETRY_CNT) + " ms");
	}

	private final void readInternal(SocketChannel sc) throws IOException {
		final ByteBuffer headerBuffer = ByteBuffer.wrap(new byte[12]);
		ByteBuffer dataByteBuffer = null;
		ObjectInputStream ois;

		headerBuffer.order(ByteOrder.LITTLE_ENDIAN);

		readFully(sc, headerBuffer);
		int magic = headerBuffer.getInt(0);
		int isObject = headerBuffer.getInt(4);
		int length = headerBuffer.getInt(8);

		LOG.trace("read header (magic:" + magic + " object:" + isObject + " len:" + length + ").");

		if (magic != MAGIC)
			throw new RuntimeException("Illegal MAGIC (" + magic + ")");

		dataByteBuffer = ByteBuffer.allocate(length);
		dataByteBuffer.order(ByteOrder.LITTLE_ENDIAN);

		readFully(sc, dataByteBuffer);
		if (dataByteBuffer.remaining() != 0)
			throw new RuntimeException("Illegal Packet");

		if (isObject != 0) {
			byte[] array = dataByteBuffer.array();
			ois = new ObjectInputStream(new ByteArrayInputStream(array));
			try {
				read(sc, ois.readObject());
			} catch (ClassNotFoundException cnfe) {
				throw new RuntimeException(cnfe);
			}
			ois.close();
		}
		else
			read(sc, dataByteBuffer);
	}

	public final void writeAsync(SocketChannel sc, Object obj) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int i;
		for (i = 0; i < 12; i++)
			baos.write(0);
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(obj);
		oos.close();
		final ByteBuffer wrap = ByteBuffer.wrap(baos.toByteArray());
		wrap.order(ByteOrder.LITTLE_ENDIAN);

		wrap.putInt(0, MAGIC);
		wrap.putInt(4, 1);
		wrap.putInt(8, baos.size() - 12);

		Slime.getInstance().enqueueReply(sc, wrap);
	}

	public final void writeAsync(SocketChannel sc, ByteBuffer bb) throws IOException {
		final ByteBuffer header = ByteBuffer.allocate(12);
		header.order(ByteOrder.LITTLE_ENDIAN);

		header.putInt(MAGIC);
		header.putInt(0);
		header.putInt(bb.position());

		if (bb.order() != ByteOrder.LITTLE_ENDIAN)
			throw new IOException("Unmatched endian");

		Slime.getInstance().enqueueReply(sc, header);
		Slime.getInstance().enqueueReply(sc, bb);
	}

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
