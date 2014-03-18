package edu.columbia.slime.service.core;

import java.io.*;
import java.io.Closeable;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;

import com.jcraft.jsch.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;
import edu.columbia.slime.conf.Config;
import edu.columbia.slime.service.Service;
import edu.columbia.slime.service.Event;
import edu.columbia.slime.service.MessageEvent;
import edu.columbia.slime.util.Network;

public class DeployService extends Service {
	private static final int SSH_PORT = 22;

	private boolean ptimestamp = true;

	JSch jsch = new JSch();
	Session session;
	Map<String, Map<String, String>> servers;

	public String getName() {
		return "deploy";
	}

        public void init() throws IOException {
		servers = Slime.getConfig().getServerInfo();
	}

        public void dispatch(Event e) throws IOException {
		if (e instanceof MessageEvent) {
			MessageEvent me = (MessageEvent) e;
			if (me.getMessage().equals("deployAll")) {
				LOG.info("'deploy' service: Got a 'deployAll' message");
				for (String server : servers.keySet()) {
					if (Network.checkIfMyAddress(server))
						continue;
					deployServer(server);
				}
			}
		}
	}

	/* inherited from Closeable */
	public void close() throws IOException {
	}

	public void makeRemoteDir(String rdir) throws IOException, JSchException {
		String command = "mkdir -p " + rdir + "\n";
		executeCommand(command);
	}

	public void launchSlime() throws IOException, JSchException {
		String distDir = Slime.getConfig().get(Config.ELEMENT_NAME_DIST);
		String command = "cd " + distDir + " && " +
				"mkdir -p logs && " + 
				"java -D" + Config.PROPERTY_NAME_LAUNCHERADDR + "=" + Network.getMyAddress() +
				" -cp $(find lib -name \"*.jar\" | tr '\n' ':')`ls dist/*`" +
				" " + Slime.getConfig().get(Config.PROPERTY_NAME_MAINCLASS) + "\n";
		LOG.debug("launching Slime remote with commands (" + command + ")");
		executeCommand(command);
	}

	protected void executeCommand(String command) throws IOException, JSchException {

		Channel channel = session.openChannel("exec");
		((ChannelExec) channel).setCommand(command);

		channel.setInputStream(null);
		((ChannelExec) channel).setErrStream(System.err);

		channel.connect();
		channel.disconnect();
	}

	public void transferFileRecursive(File lf, InputStream in, OutputStream out) throws IOException, JSchException {
		String command;

		if (ptimestamp) {
			command = "T " + (lf.lastModified() / 1000) + " 0";
			// The access time should be sent here,
			// but it is not accessible with JavaAPI ;-<
			command += (" " + (lf.lastModified() / 1000) + " 0\n"); 
			out.write(command.getBytes());
			out.flush();
			if (checkAck(in) != 0) {
				throw new RuntimeException("Transfer Error");
			}
		}

		// send "C0644 filesize filename", where filename should not include '/'
		long filesize = lf.length();
		if (lf.isDirectory())
			command = "D0755 0 ";
		else if (lf.canExecute())
			command = "C0755 " + filesize + " ";
		else
			command = "C0644 " + filesize + " ";

		command += lf.getName() + "\n";
		out.write(command.getBytes());
		out.flush();
		if (checkAck(in) != 0) {
			throw new RuntimeException("Transfer Error");
		}

		if (lf.isDirectory()) {
			for (final File fileEntry : lf.listFiles()) {
				transferFileRecursive(fileEntry, in, out);
			}
		}
		else {
			// send a content of lfile
			FileInputStream fis = null;
			byte[] buf = new byte[1024];
			try {
				fis = new FileInputStream(lf);
				while (true) {
					int len = fis.read(buf, 0, buf.length);
					if (len <= 0) break;
					out.write(buf, 0, len); //out.flush();
				}
				fis.close();
			}
			catch (Exception e) {
				try { if (fis != null) fis.close(); } catch (Exception ee) { }
			}
			// send '\0'
			buf[0] = 0;
			out.write(buf, 0, 1);
			out.flush();
			if (checkAck(in) != 0) {
				throw new RuntimeException("Transfer Error");
			}
		}
	}

	public void transferFile(String lfile, String rfile) throws IOException, JSchException {

		// exec 'scp -t rfile' remotely
		String command;
		if (ptimestamp)
			command = "scp -r -p -t " + rfile;
		else
			command = "scp -r -t " + rfile;

		Channel channel = session.openChannel("exec");
		((ChannelExec) channel).setCommand(command);

		// get I/O streams for remote scp
		OutputStream out = channel.getOutputStream();
		InputStream in = channel.getInputStream();

		channel.connect();

		if (checkAck(in) != 0) {
			throw new RuntimeException("Transfer Error");
		}

		transferFileRecursive(new File(lfile), in, out);

		in.close();
		out.close();

		channel.disconnect();
	}

	private String readFile(String filename) {
		String content = null;
		File file = new File(filename); //for ex foo.txt
		try {
			FileReader reader = new FileReader(file);
			char[] chars = new char[(int) file.length()];
			reader.read(chars);
			content = new String(chars);
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return content;
	}

	public void deployServer(final String address) {
		Map<String, String> attr = Slime.getConfig().getServerInfo().get(address);

		if (attr == null) {
			LOG.debug("servInfo:" + Slime.getConfig().getServerInfo());
			throw new RuntimeException("Illegal State");
		}

		String user = attr.get(Config.ATTR_NAME_USER);
		if (user == null) {
			user = System.getProperty("user.name");
		}
		final String passwd = attr.get(Config.ATTR_NAME_PASSWD);
		final String passphraseFile = attr.get(Config.ATTR_NAME_PASSPHRASEFILE);
		String ppFromFile = null;
		if (passphraseFile != null)
			ppFromFile = readFile(passphraseFile);
		if (ppFromFile == null)
			ppFromFile = attr.get(Config.ATTR_NAME_PASSPHRASE);
			
		final String passphrase = ppFromFile;

		try {
			session = jsch.getSession(user, address, SSH_PORT);

			// username and password will be given via UserInfo interface.
			session.setUserInfo(new UserInfo() {
					public String getPassphrase()  {
						return passphrase;
					}
					public String getPassword()  {
						return passwd;
					}
					public boolean promptPassphrase(String message)  {
						return passphrase != null;
					}
					public boolean promptPassword(String message)  {
						return passwd != null;
					}
					public boolean promptYesNo(String message)  {
						LOG.debug("JSCH questions (" + address + "): " + message);
						LOG.debug("                answered Yes");
						return true;
					}
					public void showMessage(String message)  {
						LOG.debug("JSCH tells (" + address + "): " + message);
					} 
			});
			session.connect();

			String distDir = Slime.getConfig().get(Config.ELEMENT_NAME_DIST);
			String baseDirs = Slime.getConfig().get(Config.ELEMENT_NAME_BASE);

			makeRemoteDir(distDir);

			String[] baseDir = baseDirs.split(":");
			for (int x = 0; x < baseDir.length; x++)
				transferFile(baseDir[x], distDir);

			launchSlime();

			session.disconnect();
			session = null;
		}
		catch (Exception e) {
			System.out.println(e);
		}
	}

	static int checkAck(InputStream in) throws IOException {
		int b = in.read();
		// b may be 0 for success,
		//          1 for error,
		//          2 for fatal error,
		//          -1
		if (b == 0) return b;
		if (b == -1) return b;

		if (b == 1 || b == 2) {
			StringBuffer sb = new StringBuffer();
			int c;
			do {
				c = in.read();
				sb.append((char) c);
			}
			while (c != '\n');
			if (b == 1) { // error
				System.out.print(sb.toString());
			}
			if (b == 2) { // fatal error
				System.out.print(sb.toString());
			}
		}
		return b;
	}
}
