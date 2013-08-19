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
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.io.IOException;
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

public class Slime implements EventListFeeder {
        public static final Log LOG = LogFactory.getLog(Slime.class);

	private static final Slime instance = new Slime();
	private static final int DEFAULT_SLEEP_MILLIS = 500;
	private static Config config = null;

	protected volatile boolean stopRequest = false;
	protected Integer threadCnt = 0;

	Selector selector;
	List<SocketEvent> sockets;
	Queue<MessageEvent> messages;
	SortedSet<TimerEvent> timers;
	Map<Event, Service> eventTable;

	class Dispatch {
		Event e;
		Service s;
		
		Dispatch(Event e, Service s) { this.e = e; this.s = s; }
		void execute() {
			try {
				s.dispatch(e);
			} catch (IOException ioe) {
				LOG.error("Error while dispatching : " + ioe);
			}
		}
	}

	List<Dispatch> dispatchQueue;
	Map<String, Service> services;

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

		dispatchQueue = new ArrayList<Dispatch>();
		services = new HashMap<String, Service>();

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

	private void dispatchSockets(long until) {
		long duration = until - System.currentTimeMillis();
		LOG.trace("duration: " + duration + "until : " + until + " current " + System.currentTimeMillis());
		while (duration > 0) {
			if (sockets.isEmpty()) {
				try {
					Thread.sleep(duration);
				} catch (InterruptedException ie) { }
			}
			else {
				LOG.debug("waiting for the keys : " + selector.keys());
				try {
					selector.select(duration);
				} catch (IOException ioe) {
					LOG.error("Error while selecting : " + ioe);
				}
				Set<SelectionKey> keys = selector.selectedKeys();
				for (SelectionKey sk : keys) {
					SocketEvent se = (SocketEvent) sk.attachment();
					LOG.trace("selected something sk:" + sk + " socket:" + se.getSocket());
					//eventTable.get(se).dispatch(se);
					se.setReadyOps(sk.readyOps());

					keys.remove(sk);
					if (!sk.isValid())
						continue;

					synchronized (dispatchQueue) {
						dispatchQueue.add(new Dispatch(se, eventTable.get(se)));
					}
					unregisterEvent(se);
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
						for (Service s : services.values())
							dispatchQueue.add(new Dispatch(me, s));
					}
					else {
						dispatchQueue.add(new Dispatch(me, services.get(me.getDestination())));
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
					dispatchQueue.add(new Dispatch(te, eventTable.get(te)));
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
			return DEFAULT_SLEEP_MILLIS + System.currentTimeMillis();
		}
	}

	public void execute() {
		boolean empty;
		synchronized (dispatchQueue) {
			empty = dispatchQueue.isEmpty();
		}
		
		while (!empty) {
			Dispatch d;
			synchronized (dispatchQueue) {
				if (dispatchQueue.isEmpty())
					return;
				d = dispatchQueue.remove(0);
				empty = dispatchQueue.isEmpty();
			}
			d.execute();
		}
	}

	public void start() {
		config = new Config();
		String masterAddr = System.getProperty("master.address");
		if (masterAddr != null)
			config.put("master", masterAddr);
		else
			registerEvent(new MessageEvent("deploy", null, "deployAll"), null);

		for (Service s : services.values()) {
			try {
				s.init();
			} catch (IOException ioe) {
				LOG.error("Error initializing a service '" + s.getName() + "' due to " + ioe);
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
						try { Thread.sleep(DEFAULT_SLEEP_MILLIS); }
						catch (InterruptedException ie) { }
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
			long until = dispatchTimers();
			dispatchMessages();
			execute();
			dispatchSockets(until);
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
				LOG.error("Error closing a service '" + s.getName() + "' due to " + ioe);
			}
		}
		LOG.info("Main Thread " + Thread.currentThread() + " terminated");
	}

	public void stop() {
		this.stopRequest = true;
		selector.wakeup();
	}

	public void registerEvent(Event e, Service s) {
		e.registerEvent(this, selector);

		eventTable.put(e, s);
	}

	public void unregisterEvent(Event e) {
		e.unregisterEvent(this, selector);

		eventTable.remove(e);
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
