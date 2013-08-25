package edu.columbia.slime;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Set;
import java.util.Comparator;
import java.util.Queue;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.ClosedChannelException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.service.*;
import edu.columbia.slime.service.core.*;
import edu.columbia.slime.proto.*;
import edu.columbia.slime.conf.Config;
import edu.columbia.slime.util.ClassUtils;
import edu.columbia.slime.util.PairList;

public class Slime implements EventListFeeder {
        public static final Log LOG = LogFactory.getLog(Slime.class);

	private static final Slime instance = new Slime();
	private static final int DEFAULT_SLEEP_MILLIS = 500;
	private static final int DEFAULT_LONG_SLEEP_MILLIS = 50000;
	private static Config config = null;

	protected volatile boolean stopRequest = false;
	protected Integer threadCnt = 0;

	Selector selector;
	List<SocketEvent> sockets;
	Queue<MessageEvent> messages;
	SortedSet<TimerEvent> timers;
	Map<Event, Service> eventTable;
	PairList<SocketChannel, ByteBuffer> sendQueue;
	PairList<Event, Service> dispatchQueue;
	Map<String, Service> services;
	List<Runnable> runQueue;

	protected Slime() {
		sockets = new ArrayList<SocketEvent>();
		timers = new TreeSet<TimerEvent>(
			new Comparator<TimerEvent>() {
				public int compare(TimerEvent e1, TimerEvent e2) {
					return (int) (e1.getTime() - e2.getTime());
				}
			});
		messages = new LinkedList<MessageEvent>();
		eventTable = new HashMap<Event, Service>();
		try {
			selector = Selector.open();
		} catch (IOException ioe) {
		}

		dispatchQueue = new PairList<Event, Service>();
		services = new HashMap<String, Service>();
		sendQueue = new PairList<SocketChannel, ByteBuffer>();
		runQueue = new ArrayList<Runnable>();

		/* registering default services */
		registerService(new UIService());
		registerService(new DeployService());
		registerService(new ManageService());
	}

	/* EventListFeeder Implementations */
	public Collection<? extends Event> getSocketEventList() {
		return sockets;
	}

	public Collection<? extends Event> getTimerEventList() {
		return timers;
	}

	public Collection<? extends Event> getMessageEventList() {
		return messages;
	}

	public static Slime getInstance() {
		return instance;
	}

	public static Config getConfig() {
		return config;
	}

	public static boolean isMaster() {
		return config.get("master") == null;
	}

	public void registerService(Service service) {
		services.put(service.getName(), service);
	}

	public Service getService(String name) {
		return services.get(name);
	}

	private void replySockets() {
		boolean empty;
		synchronized (sendQueue) {
			empty = sendQueue.isEmpty();
		}

		while (!empty) {
			SocketChannel sc;
			ByteBuffer bb;

			synchronized (sendQueue) {
				empty = sendQueue.isEmpty();
				if (empty)
					return;
				sc = sendQueue.getLeft();
				bb = sendQueue.getRight();
				sendQueue.remove();
				empty = sendQueue.isEmpty();
			}	
			try {
				sc.write(bb);
				LOG.debug("sent in replySocket()");
			} catch (IOException ioe) {
				LOG.error("write", ioe);
			}
		}
	}

	public void enqueueRun(Runnable r) {
		synchronized (runQueue) {
			runQueue.add(r);
		}
		selector.wakeup();
	}

	public void enqueueReply(SocketChannel sc, ByteBuffer bb) {
		synchronized (sendQueue) {
			sendQueue.add(sc, bb);
		}
		selector.wakeup();
	}

	private void dispatchSockets(long until) {
		long duration = until - System.currentTimeMillis();
		LOG.trace("[dispatchSockets] dur: " + duration + " until : " + until + " current " + System.currentTimeMillis());
		if (duration > 0) {
			LOG.debug("selecting from the keys : " + selector.keys() + " for " + duration);
			try {
				LOG.debug("select returns : " + selector.select(duration));
			} catch (IOException ioe) {
				LOG.error("Error while selecting", ioe);
				return;
			}
			Set<SelectionKey> keys = selector.selectedKeys();
			Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
			while (iterator.hasNext()) {
				SelectionKey sk = iterator.next();
				iterator.remove();

				if (!sk.isValid())
					continue;

				SocketEvent se = (SocketEvent) sk.attachment();
				LOG.debug("selected something sk:" + sk + " socket:" + se.getSocket() + " readyOps:" + sk.readyOps());
				se.setReadyOps(sk.readyOps());

				Service s = eventTable.get(se);
				unregisterEvent(se);

				synchronized (dispatchQueue) {
					dispatchQueue.add(se, s);
					dispatchQueue.notify();
				}
			}
			duration = until - System.currentTimeMillis();
		}
	}

	private void dispatchMessages() {
		MessageEvent me = null;
		while (!messages.isEmpty()) {
			synchronized (messages) {
				if (!messages.isEmpty()) {
					me = messages.remove();
				}
			}
			if (me != null) {
				synchronized (dispatchQueue) {
					if (me instanceof BroadcastMessageEvent) {
						for (Service s : services.values()) {
							dispatchQueue.add(me, s);
							dispatchQueue.notify();
						}
					}
					else {
						dispatchQueue.add(me, services.get(me.getDestination()));
						dispatchQueue.notify();
					}
				}
			}
		}
	}

	private long dispatchTimers() {
		TimerEvent te = null;
		long minTime;
		try {
			te = timers.first();
			minTime = te.getTime();
			while (minTime <= System.currentTimeMillis()) {
				timers.remove(te);
				//eventTable.get(te).dispatch(te);
				synchronized (dispatchQueue) {
					dispatchQueue.add(te, eventTable.get(te));
					dispatchQueue.notify();
				}
				if (te.getType() == TimerEvent.TYPE_PERIODIC_REL) {
					te.advanceToNextPeriod();
					timers.add(te);
				}
				else
					eventTable.remove(te);
				te = timers.first();
				minTime = te.getTime();
			}
			return minTime;
		} catch (NoSuchElementException nsee) {
			return DEFAULT_LONG_SLEEP_MILLIS + System.currentTimeMillis();
		}
	}

	public void execute() {
		Event e;
		Service s;
		synchronized (dispatchQueue) {
			if (dispatchQueue.isEmpty())
				try {
					dispatchQueue.wait(DEFAULT_LONG_SLEEP_MILLIS);
				} catch (Exception ie) { }
			
			if (dispatchQueue.isEmpty() || stopRequest)
				return;

			e = dispatchQueue.getLeft();
			s = dispatchQueue.getRight();
			dispatchQueue.remove();
		}
		try {
			s.dispatch(e);
		} catch (IOException ioe) {
			LOG.error("Error while dispatching : ", ioe);
		}
	}

	public void start() {
		start(null, null);
	}

	public void start(String baseDirs) {
		start(baseDirs, null);
	}

	public void start(String baseDirs, String mainClass) {
		config = new Config();
		String masterAddr = System.getProperty(Config.PROPERTY_NAME_MASTERADDR);
		if (masterAddr != null) {
			LOG.debug("Setting master address as '" + masterAddr + "'.");
			config.put("master", masterAddr);
		}
		else {
			LOG.debug("Sending a 'deployAll' message to the 'deploy' service.");
			registerEvent(new MessageEvent("deploy", null, "deployAll"), null);
		}

		if (mainClass != null)
			config.put(Config.PROPERTY_NAME_MAINCLASS, mainClass);
		else
			config.put(Config.PROPERTY_NAME_MAINCLASS, ClassUtils.getMainClass().getName());

		if (baseDirs != null)
			config.put(Config.ELEMENT_NAME_BASE, baseDirs);

		for (Service s : services.values()) {
			try {
				LOG.info("A service '" + s.getName() + "' is getting started.");
				s.init();
			} catch (IOException ioe) {
				LOG.error("Error initializing a service '" + s.getName() + "'", ioe);
			}
		}

		int i = 0;
		try {
			i = Integer.parseInt(config.get("threads"));
		} catch (Exception e) { LOG.error("Error: " + e); }
		while (i > 0) {
			Thread slimeThread = new Thread() {
				public void run() {
					synchronized (threadCnt) {
						threadCnt++;
					}
					while (!stopRequest) {
						execute();
					}
					synchronized (threadCnt) {
						threadCnt--;
					}
					LOG.info("Thread " + Thread.currentThread() + " terminated");
				}
			};
			slimeThread.start();
			i--;
		}
		
		while (!stopRequest) {
			synchronized (runQueue) {
				for (Runnable r : runQueue) {
					r.run();
				}
				runQueue.clear();
			}
			long until = dispatchTimers();
			dispatchMessages();
			dispatchSockets(until);
			replySockets();
		}

		while (true) {
			try { Thread.sleep(DEFAULT_SLEEP_MILLIS); } catch (Exception e) { }
			try { Thread.sleep(DEFAULT_SLEEP_MILLIS); } catch (Exception e) { }
			try { Thread.sleep(DEFAULT_SLEEP_MILLIS); } catch (Exception e) { }
			try { Thread.sleep(DEFAULT_SLEEP_MILLIS); } catch (Exception e) { }
			try { Thread.sleep(DEFAULT_SLEEP_MILLIS); } catch (Exception e) { }
			synchronized (threadCnt) {
				if (threadCnt == 0)
					break;
			}
		}

		for (Service s : services.values()) {
			try {
				s.close();
			} catch (IOException ioe) {
				LOG.error("Error closing a service '" + s.getName() + "'", ioe);
			}
		}
		LOG.info("Main Thread " + Thread.currentThread() + " terminated");
	}

	public void stop() {
		this.stopRequest = true;

		selector.wakeup();
		synchronized (dispatchQueue) {
			dispatchQueue.notifyAll();
		}
	}

	public void registerEvent(Event e, Service s) {
		e.registerEvent(this, selector);

		eventTable.put(e, s);

		selector.wakeup();
	}

	public void unregisterEvent(Event e) {
		e.unregisterEvent(this, selector);

		eventTable.remove(e);

		selector.wakeup();
	}

	public void cancelEvent(Event e) {
		e.cancelEvent(this);

		eventTable.remove(e);
	}

	public static void main(String[] args) {
		for (String arg : args) {
		}
		Slime.getInstance().start();
	}
}
