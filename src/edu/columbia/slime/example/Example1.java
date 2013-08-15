package edu.columbia.slime.example;

import edu.columbia.slime.Slime;
import edu.columbia.slime.service.Service;
import edu.columbia.slime.service.Event;
import edu.columbia.slime.service.TimerEvent;

public class Example1 {
	static class SampleService1 extends Service {
		public String getName() {
			return "SampleService1";
		}

		public void init() {
			register(new TimerEvent(TimerEvent.TYPE_PERIODIC_REL, 3000));
		}

		public void dispatch(Event e) {
			System.out.println("Got an event at " + System.currentTimeMillis() + "! in Thread:" + Thread.currentThread());
		}

		public void close() {
		}
	}

	public static void main(String[] args) {
		new SampleService1().init();
		Slime.getInstance().start();
	}
}
