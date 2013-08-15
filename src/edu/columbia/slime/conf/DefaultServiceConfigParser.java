package edu.columbia.slime.conf;

import java.util.Map;
import java.util.HashMap;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

@SuppressWarnings("serial")
public class DefaultServiceConfigParser extends HashMap<String, String> implements ServiceConfigParser {
	String building;
	String currentQName;

	public DefaultServiceConfigParser() {
		building = "";
	}

	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		if (currentQName != null && !currentQName.equals("service"))
			throw new SAXException("No nested element (" + qName + " in " + currentQName + ") supported by " + DefaultServiceConfigParser.class.toString());
		currentQName = qName;
		building = "";
	}

	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (qName.equals("service"))
			return;

		if (!currentQName.equals(qName))
			throw new SAXException("Unmatched elements <" + currentQName + "> ... </" + qName + ">");

		if (get(qName) != null)
			throw new SAXException("Existing attribute name: " + qName);

		put(qName, building);
	}

	public void characters(char ch[], int start, int length) {
		building += new String(ch, start, length);
	}
}
