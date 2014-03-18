package edu.columbia.slime.service;

import java.nio.channels.Selector;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;

public class TimerEvent extends Event {
	public static final int TYPE_PERIODIC_REL = 0;
	public static final int TYPE_APPOINTED_REL = 1;
	public static final int TYPE_APPOINTED_ABS = 2;
	public static final int TYPE_PERIODIC_OVERLAP_REL = 3;

	protected int type;
	protected long targetTime;
	protected long period;

	protected boolean isRelative;
	protected boolean isPeriodic;
	protected boolean isOverlappable;

	public TimerEvent(int type, long millisec) {
		this.type = type;

		if (type != TYPE_PERIODIC_REL && type != TYPE_PERIODIC_OVERLAP_REL &&
				type != TYPE_APPOINTED_REL &&
				type != TYPE_APPOINTED_ABS) {
			throw new IllegalArgumentException("Invalid Type Value : " + type);
		}

		isRelative = (type == TYPE_PERIODIC_REL || type == TYPE_APPOINTED_REL
			|| type == TYPE_PERIODIC_OVERLAP_REL);
		isPeriodic = (type == TYPE_PERIODIC_REL || type == TYPE_PERIODIC_OVERLAP_REL);
		isOverlappable = (type != TYPE_PERIODIC_REL);

		if (isRelative)
			this.targetTime = System.currentTimeMillis() + millisec;
		else
			this.targetTime = millisec;

		if (isPeriodic)
			this.period = millisec; 
	}

	public int getType() {
		return type;
	}

	public long getTime() {
		return targetTime;
	}

	public void advanceToNextPeriod() {
		// assert(isPeriodic);
		targetTime += period;
	}

	public boolean isOverlappable() {
		return isOverlappable;
	}

   	@SuppressWarnings("unchecked")
   	public void registerEvent(EventListFeeder elf, Selector selector) {
   		Collection<TimerEvent> c = (Collection<TimerEvent>) elf.getTimerEventList();
 		c.add(this);
	}

	public void unregisterEvent(EventListFeeder elf, Selector selector) {
		elf.getTimerEventList().remove(this);
	}
		
   	public void cancelEvent(EventListFeeder elf) {
  		elf.getTimerEventList().remove(this);
	}
}
