package edu.columbia.slime.service;

import java.util.Collection;
import java.nio.channels.Selector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;

public abstract class Event {
	protected boolean inDispatch = false;

	public synchronized boolean inDispatch() {
		return inDispatch;
	}

	public synchronized void startDispatch() {
		inDispatch = true;
	}

	public synchronized void endDispatch() {
		inDispatch = false;
	}

        public abstract void registerEvent(EventListFeeder elf, Selector selector);

        public abstract void unregisterEvent(EventListFeeder elf, Selector selector);

        public abstract void cancelEvent(EventListFeeder elf);
}
