package edu.columbia.slime.service;

import java.util.Collection;
import java.nio.channels.Selector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;

public abstract class Event {
        public abstract void registerEvent(EventListFeeder elf, Selector selector);

        public abstract void cancelEvent(EventListFeeder elf);
}
