package edu.columbia.slime.proto;

import java.io.Serializable;

public abstract class MessageHeader implements Serializable {
	/*
	   private void writeObject(java.io.ObjectOutputStream out)
	   throws IOException
	   private void readObject(java.io.ObjectInputStream in)
	   throws IOException, ClassNotFoundException;
	   private void readObjectNoData() 
	   throws ObjectStreamException;
	 */

	String senderServiceName;
	String receiverServiceName;
	String senderAddress;
	String receiverAddress;
}