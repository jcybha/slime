package edu.columbia.slime.proto;

import java.io.Serializable;

public abstract class Protocol implements Serializable {
	private static final long serialVersionUID = 1116291102682313243L;

	ProtocolHeader header;

	public Protocol() {
		header = new ProtocolHeader();
	}

	public static void validate(Object obj, Class klass) {
		if (!klass.isInstance(obj)) {
			throw new RuntimeException("Wrong class type");
		}
		Protocol proto = (Protocol) obj;

		proto.header.validate();
	}
}
