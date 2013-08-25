package edu.columbia.slime.proto;

import java.io.Serializable;

public class ProtocolHeader implements Serializable {
	private static final long serialVersionUID = 1326571155222171119L;

	public static final String MAGIC_STRING = "Slime.Protocol.Header.Magic";
	public static final String VERSION_STRING = "1.0";

	private String magic;
	private String version;

	public ProtocolHeader() {
		magic = MAGIC_STRING;
		version = VERSION_STRING;
	}

	public void validate() {
		if (!MAGIC_STRING.equals(magic))
			throw new RuntimeException("Wrong magic");
		if (!VERSION_STRING.equals(version))
			throw new RuntimeException("Wrong version");
	}
}
