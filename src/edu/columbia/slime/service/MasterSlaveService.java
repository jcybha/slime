package edu.columbia.slime.service;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.Queue;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;
import edu.columbia.slime.util.Network;

public abstract class MasterSlaveService extends Service implements NetworkServiceInterface {
	private static final String ATTR_NAME_PORT = "port";
	private static final String ATTR_NAME_MASTERS = "masters";
	private static final String ATTR_NAME_SOM = "slaveonmaster";
	private static final int MAGIC1 = 0x48031027;
	private static final int MAGIC2 = 0x91785319;

	private static final int READ_RETRY_SLEEP = 500;
	private static final int READ_RETRY_CNT = 10;

	private static final int SLAVE_INIT_DELAY_TIME = 10000;

        private int launcherIdentity = IDENTITY_UNKNOWN;
        private static final int IDENTITY_UNKNOWN = 0;
        private static final int IDENTITY_LAUNCHER = 1;
        private static final int IDENTITY_NONLAUNCHER = 2;

	int protocol_version = 0;

	SocketEvent se;
	ServerSocketChannel ssc;

	List<String> masters = new ArrayList<String>();
	boolean needSlaveOnMaster = false;
	boolean isMaster;
	int numberOfRandomMasters = -1;

	NetworkServiceInterface masterService;
	NetworkServiceInterface slaveService;

	public void setMasterAndSlaveServices(NetworkServiceInterface master, NetworkServiceInterface slave) {
		masterService = master;
		slaveService = slave;
	}

	protected final boolean needMaster() {
		return isMaster;
	}

	protected final boolean needSlave() {
		if (needSlaveOnMaster)
			return true;

		return !needMaster();
	}

	public final boolean isLauncher() {
                if (launcherIdentity == IDENTITY_UNKNOWN) {
                        if (Slime.getConfig().get("launcher") == null) {
                                launcherIdentity = IDENTITY_LAUNCHER;
                        }
                        else
                                launcherIdentity = IDENTITY_NONLAUNCHER;
                }
                return launcherIdentity == IDENTITY_LAUNCHER;
        }

	protected final void setProtocolVersion(int version) {
		protocol_version = version;
	}

	int port = 0;

	private final Queue<SocketChannel> slaveSockets = new LinkedList<SocketChannel>();
	private final Queue<SocketChannel> masterSockets = new LinkedList<SocketChannel>();

	private final void initMaster() throws IOException {
		ssc = ServerSocketChannel.open();
		ssc.socket().bind(new InetSocketAddress(port));
		ssc.configureBlocking(false);
		LOG.info("Created a [master] service'" + getName() + "' at port " + port);

		se = new SocketEvent(ssc);
		register(se);

		masterService.init();
	}

	private final void closeMaster() throws IOException {
		masterService.close();

//		cancel(se);

		ssc.close();
		synchronized (slaveSockets) {
			for (SocketChannel sc : slaveSockets) {
				sc.close();
			}
			masterSockets.clear();
		}
	}

	private final void initSlave() throws IOException {
		LOG.info("Sleep for " + SLAVE_INIT_DELAY_TIME + " ms waiting masters launched");
		try { Thread.sleep(SLAVE_INIT_DELAY_TIME); }
		catch (Exception e) { }

		for (String master : masters) {
			SocketChannel sc = SocketChannel.open();
			sc.socket().connect(new InetSocketAddress(master, port));
			sc.configureBlocking(false);
			sc.socket().setTcpNoDelay(true);

			synchronized (masterSockets) {
				masterSockets.add(sc);
			}

			se = new SocketEvent(sc);
			register(se);
		}
		LOG.info("Created a [slave] service '" + getName() + "' connected to masters " + masters + " through a port " + port);

		slaveService.init();
	}

	private final void closeSlave() throws IOException {
		slaveService.close();

//		cancel(se);

		synchronized (masterSockets) {
			for (SocketChannel sc : masterSockets) {
				sc.close();
			}
			masterSockets.clear();
		}
	}

	public final void init() throws IOException {
		try {
			if (getDefaultConfig() == null || getDefaultConfig().get(ATTR_NAME_PORT) == null) {
				LOG.error("Cannot find 'port' attribute for the service '" + getName());
				throw new IOException("Cannot find 'port' attribute for the service '" + getName());
			}
			port = Integer.parseInt(getDefaultConfig().get(ATTR_NAME_PORT));

			if (getDefaultConfig().get(ATTR_NAME_MASTERS) != null) {
				String mastersAttr = getDefaultConfig().get(ATTR_NAME_MASTERS);
				for (String master : mastersAttr.split(",")) {
					masters.add(master);
				}
			}
			if (masters.isEmpty()) {
				String launcher = Slime.getConfig().get("launcher");
				if (launcher == null)
					launcher = Network.getMyAddress();
				masters.add(launcher);

				if (Slime.getConfig().get("masterNum") != null) {
					String masterNum = Slime.getConfig().get("masterNum");
					try {
						numberOfRandomMasters = Integer.parseInt(masterNum);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
					if (numberOfRandomMasters < 1)
						throw new RuntimeException("Too small number for masters");

					Map<String, Map<String, String>> serverInfo = Slime.getConfig().getServerInfo();
					for (String server : serverInfo.keySet()) {
						if (numberOfRandomMasters <= masters.size())
							break;
						if (server.equals(launcher))
							continue;
						masters.add(server);
					}
				}
			}

			isMaster = masters.contains(Network.getMyAddress());

			if (getDefaultConfig().get(ATTR_NAME_SOM) != null) {
				String som = getDefaultConfig().get(ATTR_NAME_SOM);
				needSlaveOnMaster = (som.equalsIgnoreCase("true") || som.equalsIgnoreCase("yes"));
			}

		} catch (Exception e) {
			LOG.error("Error initializing the service " + getName());
			e.printStackTrace();
			throw new IOException(e);
		}

		if (needMaster()) {
			initMaster();
		}
		if (needSlave()) {
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
			register(new SocketEvent(sc));

			synchronized (slaveSockets) {
				slaveSockets.add(sc);
			}

			newConnection(sc);

			LOG.info("Opened a new connection from " + sc.socket());
		}
		if ((se.getReadyOps() & SelectionKey.OP_READ) != 0) {
			LOG.debug("Reading from a connection " + e);
			readInternal((SocketChannel) se.getSocket());
		}
		register(se);
	}

	public final void newConnection(SocketChannel sc) throws IOException {
		masterService.newConnection(sc);
	}

	public final void dispatchMessage(MessageEvent me) throws IOException {
		if (me instanceof MasterMessageEvent) {
			masterService.dispatchMessage(me);
		}
		else if (me instanceof SlaveMessageEvent) {
			slaveService.dispatchMessage(me);
		}
		else
			throw new RuntimeException("MessageEvent should be either a MasterMessageEvent or a SlaveMessageEvent");
	}

	public final void dispatchTimer(TimerEvent te) throws IOException {
		if (te instanceof MasterTimerEvent) {
			masterService.dispatchTimer(te);
		}
		else if (te instanceof SlaveTimerEvent) {
			slaveService.dispatchTimer(te);
		}
		else
			throw new RuntimeException("TimerEvent should be either a MasterTimerEvent or a SlaveTimerEvent");
	}

	public final void read(SocketChannel sc, Serializable obj) throws IOException {
		boolean toMaster;
		synchronized (masterSockets) {
			toMaster = masterSockets.contains(sc);
		}
		if (toMaster) {
			slaveService.read(sc, obj);
		}
		else {
			masterService.read(sc, obj);
		}
	}

	public final void read(SocketChannel sc, ByteBuffer bb) throws IOException {
		boolean toMaster;
		synchronized (masterSockets) {
			toMaster = masterSockets.contains(sc);
		}
		if (toMaster) {
			slaveService.read(sc, bb);
		}
		else {
			masterService.read(sc, bb);
		}
	}

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
		final ByteBuffer headerBuffer = ByteBuffer.wrap(new byte[20]);
		ByteBuffer dataByteBuffer = null;
		ObjectInputStream ois;

		headerBuffer.order(ByteOrder.LITTLE_ENDIAN);

		readFully(sc, headerBuffer);
		int magic1 = headerBuffer.getInt(0);
		int magic2 = headerBuffer.getInt(4);
		int version = headerBuffer.getInt(8);
		int isObject = headerBuffer.getInt(12);
		int length = headerBuffer.getInt(16);

		LOG.trace("read header (magic:" + magic1 + ":" + magic2 + " object:" + isObject + " len:" + length + ").");

		if (magic1 != MAGIC1 || magic2 != MAGIC2)
			throw new RuntimeException("Illegal MAGIC (" + magic1 + ":" + magic2 + ")");

		if (version != protocol_version)
			throw new RuntimeException("Unmatched protocol version, " + version + ", expecting " + protocol_version);

		dataByteBuffer = ByteBuffer.allocate(length);
		dataByteBuffer.order(ByteOrder.LITTLE_ENDIAN);

		readFully(sc, dataByteBuffer);
		if (dataByteBuffer.remaining() != 0)
			throw new RuntimeException("Illegal Packet");

		if (isObject != 0) {
			byte[] array = dataByteBuffer.array();
			ois = new ObjectInputStream(new ByteArrayInputStream(array));
			try {
				read(sc, (Serializable) ois.readObject());
			} catch (ClassNotFoundException cnfe) {
				throw new RuntimeException(cnfe);
			}
			ois.close();
		}
		else
			read(sc, dataByteBuffer);
	}

	public final void writeAsync(SocketChannel sc, Serializable obj) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int i;
		for (i = 0; i < 12; i++)
			baos.write(0);
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(obj);
		oos.close();
		final ByteBuffer wrap = ByteBuffer.wrap(baos.toByteArray());
		wrap.order(ByteOrder.LITTLE_ENDIAN);

		wrap.putInt(0, MAGIC1);
		wrap.putInt(4, MAGIC2);
		wrap.putInt(8, protocol_version);
		wrap.putInt(12, 1);
		wrap.putInt(16, baos.size() - 12);

		Slime.getInstance().enqueueReply(sc, wrap);
	}

	public final void writeAsync(SocketChannel sc, ByteBuffer bb) throws IOException {
		final ByteBuffer header = ByteBuffer.allocate(12);
		header.order(ByteOrder.LITTLE_ENDIAN);

		header.putInt(MAGIC1);
		header.putInt(MAGIC2);
		header.putInt(protocol_version);
		header.putInt(0);
		header.putInt(bb.position());

		if (bb.order() != ByteOrder.LITTLE_ENDIAN)
			throw new IOException("Unmatched endian");

		Slime.getInstance().enqueueReply(sc, header);
		Slime.getInstance().enqueueReply(sc, bb);
	}

	public final int broadcastToMastersAsync(Serializable obj) throws IOException {
		return broadcastAsync(masterSockets, obj);
	}

	public final int broadcastToSlavesAsync(Serializable obj) throws IOException {
		return broadcastAsync(slaveSockets, obj);
	}

	public final int broadcastAsync(Queue<SocketChannel> sockets, Serializable obj) throws IOException {
		int i = 0;
		synchronized (sockets) {
			for (SocketChannel sc : sockets) {
				i++;
				writeAsync(sc, obj);
			}
		}
		return i;
	}

	public final int broadcastToMastersAsync(ByteBuffer bb) throws IOException {
		return broadcastAsync(masterSockets, bb);
	}

	public final int broadcastToSlavesAsync(ByteBuffer bb) throws IOException {
		return broadcastAsync(slaveSockets, bb);
	}

	public final int broadcastAsync(Queue<SocketChannel> sockets, ByteBuffer bb) throws IOException {
		int i = 0;
		synchronized (sockets) {
			for (SocketChannel sc : sockets) {
				i++;
				writeAsync(sc, bb);
			}
		}
		return i;
	}

	public final Map<SocketChannel, Serializable> allocateObjectMapForMasters() {
		return allocateObjectMap(masterSockets);
	}

	public final Map<SocketChannel, Serializable> allocateObjectMapForSlaves() {
		return allocateObjectMap(slaveSockets);
	}

	public final Map<SocketChannel, Serializable> allocateObjectMap(Queue<SocketChannel> sockets) {
		Map<SocketChannel, Serializable> map = new HashMap<SocketChannel, Serializable>();
		
		synchronized (sockets) {
			for (SocketChannel sc : sockets) {
				map.put(sc, null);
			}
		}
		return map;
	}

	public final Map<SocketChannel, ByteBuffer> allocateBufferMapForMasters() {
		return allocateBufferMap(masterSockets);
	}

	public final Map<SocketChannel, ByteBuffer> allocateBufferMapForSlaves() {
		return allocateBufferMap(slaveSockets);
	}

	public final Map<SocketChannel, ByteBuffer> allocateBufferMap(Queue<SocketChannel> sockets) {
		Map<SocketChannel, ByteBuffer> map = new HashMap<SocketChannel, ByteBuffer>();
		
		synchronized (sockets) {
			for (SocketChannel sc : sockets) {
				map.put(sc, null);
			}
		}
		return map;
	}

	public final void writeObjectMapAsync(Map<SocketChannel, Serializable> map) throws IOException {
		for (SocketChannel sc : map.keySet()) {
			writeAsync(sc, map.get(sc));
		}
	}

	public final void writeBufferMapAsync(Map<SocketChannel, ByteBuffer> map) throws IOException {
		for (SocketChannel sc : map.keySet()) {
			writeAsync(sc, map.get(sc));
		}
	}

	/* inherited from Closeable */
	public final void close() throws IOException {
		if (needMaster()) {
			closeMaster();
		}
		if (needSlave()) {
			closeSlave();
		}
	}

	public void register(Event e) {
		if (e instanceof MessageEvent) {
			if (!(e instanceof MasterMessageEvent) &&
			    !(e instanceof SlaveMessageEvent))
				throw new RuntimeException("Use MasterMessageEvent or SlaveMessageEvent");
		}
		else if (e instanceof TimerEvent) {
			if (!(e instanceof MasterTimerEvent) &&
			    !(e instanceof SlaveTimerEvent))
				throw new RuntimeException("Use MasterTimerEvent or SlaveTimerEvent");
		}

		super.register(e);
	}
}
