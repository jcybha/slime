package edu.columbia.slime.service.core;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;
import edu.columbia.slime.service.BasicNetworkService;

public class UIService extends BasicNetworkService {
	public String getName() {
		return "ui";
	}

	public void read(SocketChannel sc) throws IOException {
	}
}
