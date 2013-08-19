package edu.columbia.slime.service;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;

public class BroadcastMessageEvent extends MessageEvent {
	public BroadcastMessageEvent(Service origination, String message) {
		super(null, origination, message);
	}
}
