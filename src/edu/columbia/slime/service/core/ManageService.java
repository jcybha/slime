package edu.columbia.slime.service.core;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.ByteBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;
import edu.columbia.slime.service.MessageEvent;
import edu.columbia.slime.service.MasterSlaveService;

public class ManageService extends MasterSlaveService {
	public String getName() {
		return "manage";
	}

	public void dispatchMessage(MessageEvent me) throws IOException {
	LOG.info("Manage received a request" + me);
		// from ui service
		if (me.getMessage().equals("closeAll")) {
			LOG.info("received a stop request");
System.out.println("received a stop request");
			for (SocketChannel sc : clientSockets) {
				ByteBuffer bb = ByteBuffer.wrap("close".getBytes());
				sc.write(bb);
System.out.println("sent a close message to " + sc.socket());
			}
			Slime.getInstance().stop();
		}
	}

	public void read(SocketChannel sc) throws IOException {
	/* when a message from the master to close */
		ByteBuffer bb = ByteBuffer.allocate(10);
		LOG.info("in read() for Manage bb:" + bb + " pos:" + bb.position() + " rem:" + bb.remaining());
		LOG.info("read bytes: " + sc.read(bb));
		LOG.info("in read() for Manage bb:" + bb + " pos:" + bb.position() + " rem:" + bb.remaining());
		int len = bb.position() - bb.arrayOffset();
		String msg = new String(bb.array(), bb.arrayOffset(), bb.position());
		LOG.info("Got " + msg);
		if (msg.equals("close"))
			Slime.getInstance().stop();
	}

}
