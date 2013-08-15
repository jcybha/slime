package edu.columbia.slime.service;

import java.util.Collection;

public interface EventListFeeder {
	public Collection<? extends Event> getSocketEventList();
	public Collection<? extends Event> getTimerEventList();
	public Collection<? extends Event> getMessageEventList();
}
