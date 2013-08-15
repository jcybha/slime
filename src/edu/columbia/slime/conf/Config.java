package edu.columbia.slime.conf;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Stack;
import java.util.Map;
import java.util.HashMap;
import javax.xml.parsers.SAXParser;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import edu.columbia.slime.Slime;
import edu.columbia.slime.util.Network;
import edu.columbia.slime.service.Service;

@SuppressWarnings("serial")
public class Config extends HashMap<String, String> {
	public static final Log LOG = LogFactory.getLog(Slime.class);

	private static final String configPath = "conf/slime.xml";

	public class ConfigParser extends DefaultHandler {
		private static final String ELEMENT_NAME_SLIME = "slime";
		private static final String ELEMENT_NAME_CONFIG = "configuration";
		private static final String ELEMENT_NAME_THREADS = "threads";
		private static final String ELEMENT_NAME_DIRS = "directories";
		private static final String ELEMENT_NAME_BASE = "base";
		private static final String ELEMENT_NAME_DIST = "dist";
		private static final String ELEMENT_NAME_PLUGINS = "plugins";
		private static final String ELEMENT_NAME_SERVERS = "servers";
		private static final String ELEMENT_NAME_SERVER = "server";
		private static final String ELEMENT_NAME_SERVICES = "services";
		private static final String ELEMENT_NAME_SERVICE = "service";

		private static final String ATTR_NAME_NAME = "name";
		private static final String ATTR_NAME_THREADS = "threads";

		private Stack<String> elementStack = new Stack<String>();
		private String attrThreads = null;

		private ServiceConfigParser serviceParser = null;

		Map<String, String> map;

		ConfigParser(Map<String, String> map) {
			this.map = map;
		}

		public void startElement(String uri, String localName, String qName, Attributes attributes)
			throws SAXException {
			if (ELEMENT_NAME_SERVICE.equals(qName)) {
				Service s = Slime.getInstance().getService(attributes.getValue(ATTR_NAME_NAME));
				if (s == null) {
					LOG.warn("Cannot find the service " + attributes.getValue(ATTR_NAME_NAME));
					serviceParser = new DefaultServiceConfigParser();
				}
				else {
					serviceParser = Slime.getInstance().getService(attributes.getValue(ATTR_NAME_NAME)).getConfigParser();
				}
			}

			if (serviceParser != null) {
				serviceParser.startElement(uri, localName, qName, attributes);
				return;
			}

			if (elementStack.empty() && !ELEMENT_NAME_SLIME.equals(qName))
				throw new SAXException("A Slime configuration file should begin with an element named 'slime'");

			if (ELEMENT_NAME_SERVER.equals(qName)) {
				attrThreads = attributes.getValue(ATTR_NAME_THREADS);
			}

			elementStack.push(qName);
		}

		public void endElement(String uri, String localName, String qName)
			throws SAXException {
			if (serviceParser != null) {
				serviceParser.endElement(uri, localName, qName);

				if (ELEMENT_NAME_SERVICE.equals(qName)) {
					serviceParser = null;
				}
				return;
			}

			if (elementStack.empty() || !elementStack.pop().equals(qName))
				throw new SAXException("Unmatched closing element: " + qName);
		}

		public void characters(char ch[], int start, int length)
			throws SAXException {
			if (elementStack.empty())
				throw new SAXException("String without element start");

			if (serviceParser != null) {
				serviceParser.characters(ch, start, length);
				return;
			}

			String newString = new String(ch, start, length);

			if (elementStack.peek().equals(ELEMENT_NAME_THREADS)) {
				if (map.get(ELEMENT_NAME_THREADS) == null)
					map.put(ELEMENT_NAME_THREADS, newString);
			}
			else if (elementStack.peek().equals(ELEMENT_NAME_BASE))
				map.put(ELEMENT_NAME_BASE, newString);
			else if (elementStack.peek().equals(ELEMENT_NAME_DIST))
				map.put(ELEMENT_NAME_DIST, newString);
			else if (elementStack.peek().equals(ELEMENT_NAME_PLUGINS))
				map.put(ELEMENT_NAME_PLUGINS, newString);
			else if (elementStack.peek().equals(ELEMENT_NAME_SERVER)) {
				String exist = map.get(ELEMENT_NAME_SERVERS);
				if (exist == null)
					exist = newString;
				else
					exist = exist + ";" + newString;
				map.put(ELEMENT_NAME_SERVERS, exist);
				if (Network.checkIfMyAddress(newString) && attrThreads != null)
					map.put(ELEMENT_NAME_THREADS, attrThreads);
			}
		}
	}

	public Config() {
		this(configPath);
	}

	public Config(String configPath) {
		InputStream is = Config.class.getResourceAsStream(configPath);
		if (is == null) {
			try {
				is = new FileInputStream(configPath);
			} catch (IOException ioe) {
				throw new IllegalArgumentException("Cannot find " + configPath + ".");
			}
		}

		javax.xml.parsers.SAXParserFactory saxFactory = javax.xml.parsers.SAXParserFactory.newInstance();
		try {
			SAXParser parser = saxFactory.newSAXParser();
			parser.parse(is, new ConfigParser(this));
		}
		catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}
}
