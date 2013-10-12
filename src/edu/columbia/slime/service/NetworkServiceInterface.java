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

public interface NetworkServiceInterface {
	public void init() throws IOException;

	public void close() throws IOException;

	public void newConnection(SocketChannel sc) throws IOException;

	public void dispatchMessage(MessageEvent me) throws IOException;

	public void dispatchTimer(TimerEvent te) throws IOException;

	public void read(SocketChannel sc, Serializable obj) throws IOException;
	public void read(SocketChannel sc, ByteBuffer bb) throws IOException;
}
