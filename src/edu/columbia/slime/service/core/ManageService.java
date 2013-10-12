package edu.columbia.slime.service.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.ByteBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;
import edu.columbia.slime.service.MessageEvent;
import edu.columbia.slime.service.TimerEvent;
import edu.columbia.slime.service.MasterTimerEvent;
import edu.columbia.slime.service.MasterSlaveService;
import edu.columbia.slime.service.NetworkServiceInterface;
import edu.columbia.slime.proto.Protocol;
import edu.columbia.slime.proto.manage.PingProtocol;
import edu.columbia.slime.proto.manage.PongProtocol;
import edu.columbia.slime.proto.manage.CloseProtocol;

public class ManageService extends MasterSlaveService {
	private static final int MASTER_PING_PERIOD_MS = 10000; // 10s

	public String getName() {
		return "manage";
	}

	NetworkServiceInterface masterService = new NetworkServiceInterface() {
		TimerEvent pingTimer;

		public void init() throws IOException {
			pingTimer = new MasterTimerEvent(TimerEvent.TYPE_PERIODIC_REL, MASTER_PING_PERIOD_MS);

			register(pingTimer);
		}

		public void close() throws IOException {
			unregister(pingTimer);
		}

		public void newConnection(SocketChannel sc) throws IOException {
		}

		public void dispatchMessage(MessageEvent me) throws IOException {
			LOG.info("ManageMaster received a request" + me);
			// from ui service
			if (me.getMessage().equals("closeAll")) {
				CloseProtocol cp = new CloseProtocol();
				LOG.info("received a stop request");

				broadcastToSlavesAsync(cp);
				LOG.debug("sent a close message to all");
				Slime.getInstance().stop();
			}
		}

		public void dispatchTimer(TimerEvent te) throws IOException {
			if (te == pingTimer) {
				PingProtocol pp = new PingProtocol();

				broadcastToSlavesAsync(pp);
				LOG.info("Pinged to all");
			}
		}

		public void read(SocketChannel sc, Serializable obj) throws IOException {
			Protocol.validate(obj, Protocol.class);

			LOG.info("Received:" + obj);

			if (obj instanceof CloseProtocol)
				Slime.getInstance().stop();
			else if (obj instanceof PongProtocol) {
			}
		}

		public void read(SocketChannel sc, ByteBuffer bb) throws IOException {
		}
	};

	NetworkServiceInterface slaveService = new NetworkServiceInterface() {
		public void init() throws IOException {
		}

		public void close() throws IOException {
		}

		public void newConnection(SocketChannel sc) throws IOException {
		}

		public void dispatchMessage(MessageEvent me) throws IOException {
		}

		public void dispatchTimer(TimerEvent te) throws IOException {
		}

		public void read(SocketChannel sc, Serializable obj) throws IOException {
			Protocol.validate(obj, Protocol.class);

			LOG.info("Received:" + obj);

			if (obj instanceof PingProtocol) {
				writeAsync(sc, new PongProtocol());
				LOG.info("requested a pong protocol");
			}
		}

		public void read(SocketChannel sc, ByteBuffer bb) throws IOException {
		}

	};

	public ManageService() {
		setMasterAndSlaveServices(masterService, slaveService);
	}
}
