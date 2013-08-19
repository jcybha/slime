package edu.columbia.slime.service;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.LinkedList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;
import edu.columbia.slime.proto.*;
import edu.columbia.slime.conf.ServiceConfigParser;
import edu.columbia.slime.conf.DefaultServiceConfigParser;

public abstract class Service implements Closeable {
	public static final Log LOG = LogFactory.getLog(Slime.class);

	ServiceConfigParser parser = null;

	public void register(Event e) {
		Slime.getInstance().registerEvent(e, this);
	}

	public void unregister(Event e) {
		Slime.getInstance().unregisterEvent(e);
	}

	public void cancel(Event e) {
		Slime.getInstance().cancelEvent(e);
	}

	public abstract String getName();

	public abstract void init() throws IOException;

	public abstract void dispatch(Event e) throws IOException;

	/* inherited from Closeable */
	public abstract void close() throws IOException;

	public ServiceConfigParser getConfigParser() {
		if (parser == null)
			parser = new DefaultServiceConfigParser();
		return parser;
	}

	@SuppressWarnings("unchecked")
	public Map<String, String> getDefaultConfig() {
		if (parser instanceof Map)
			return (Map<String, String>) parser;
		return null;
	}
}
