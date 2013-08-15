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

	protected int type;
	protected long targetTime;
	protected long period;

	public TimerEvent(int type, long millisec) {
		this.type = type;
		if (type == TYPE_PERIODIC_REL || type == TYPE_APPOINTED_REL)
			this.targetTime = System.currentTimeMillis() + millisec;
		else // TYPE_APPOINTED_ABS
			this.targetTime = millisec;

		if (type == TYPE_PERIODIC_REL)
			this.period = millisec; 

		if (type != TYPE_PERIODIC_REL &&
				type != TYPE_APPOINTED_REL &&
				type != TYPE_APPOINTED_ABS) {
			throw new IllegalArgumentException("Invalid Type Value : " + type);
		}
	}

	public int getType() {
		return type;
	}

	public long getTime() {
		return targetTime;
	}

	public void advanceToNextPeriod() {
		// assert(type == TYPE_PERIODIC_REL);
		targetTime += period;
	}


        @SuppressWarnings("unchecked")
        public void registerEvent(EventListFeeder elf, Selector selector) {
                Collection<TimerEvent> c = (Collection<TimerEvent>) elf.getTimerEventList();
                c.add(this);
        }

        public void cancelEvent(EventListFeeder elf) {
                elf.getTimerEventList().remove(this);
        }

}
