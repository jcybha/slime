package edu.columbia.slime.conf;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public interface ServiceConfigParser {
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException;

	public void endElement(String uri, String localName, String qName) throws SAXException;

	public void characters(char ch[], int start, int length);
}
