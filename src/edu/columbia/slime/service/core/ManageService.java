package edu.columbia.slime.service.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.ByteBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;
import edu.columbia.slime.service.MessageEvent;
import edu.columbia.slime.service.TimerEvent;
import edu.columbia.slime.service.MasterSlaveService;
import edu.columbia.slime.proto.Protocol;
import edu.columbia.slime.proto.manage.PingProtocol;
import edu.columbia.slime.proto.manage.PongProtocol;
import edu.columbia.slime.proto.manage.CloseProtocol;

public class ManageService extends MasterSlaveService {
	private static final int MASTER_PING_PERIOD_MS = 10000; // 10s
	TimerEvent pingTimer;

	public String getName() {
		return "manage";
	}

	public void initMasterCustom() throws IOException {
		pingTimer = new TimerEvent(TimerEvent.TYPE_PERIODIC_REL, MASTER_PING_PERIOD_MS);

		register(pingTimer);
	}

	public void closeMasterCustom() throws IOException {
		unregister(pingTimer);
	}

	public void dispatchMessage(MessageEvent me) throws IOException {
	LOG.info("Manage received a request" + me);
		// from ui service
		if (me.getMessage().equals("closeAll")) {
			CloseProtocol cp = new CloseProtocol();
			LOG.info("received a stop request");
			for (SocketChannel sc : clientSockets) {
				writeAsync(sc, cp);
				LOG.debug("sent a close message to " + sc.socket());
			}
			Slime.getInstance().stop();
		}
	}

	public void dispatchTimer(TimerEvent te) throws IOException {
		if (te == pingTimer) {
			PingProtocol pp = new PingProtocol();

			for (SocketChannel sc : clientSockets) {
				writeAsync(sc, pp);
				LOG.info("requested a ping message to " + sc.socket());
			}
			LOG.info("Pinged to all");
		}
	}

	public void read(SocketChannel sc, Object obj) throws IOException {
		Protocol.validate(obj, Protocol.class);

		LOG.info("Received:" + obj);

		if (obj instanceof CloseProtocol)
			Slime.getInstance().stop();
		else if (obj instanceof PingProtocol) {
			writeAsync(sc, new PongProtocol());
			LOG.info("requested a pong protocol");
		}
		else if (obj instanceof PongProtocol) {
		}
	}

	public void read(SocketChannel sc, ByteBuffer bb) throws IOException {
	}

	public void newConnection(SocketChannel sc) throws IOException {
		// update slave list
	}
}
