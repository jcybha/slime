package edu.columbia.slime.service;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;

public class MasterMessageEvent extends MessageEvent {
	public MasterMessageEvent(String destination, Service origination, String message) {
		super(destination, origination, message);
	}
}
