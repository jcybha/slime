package edu.columbia.slime.service;

import java.nio.channels.Selector;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;

public class MasterTimerEvent extends TimerEvent {
	public MasterTimerEvent(int type, long millisec) {
		super(type, millisec);
	}
}
