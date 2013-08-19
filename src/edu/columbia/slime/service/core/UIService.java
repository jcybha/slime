package edu.columbia.slime.service.core;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.columbia.slime.Slime;
import edu.columbia.slime.service.Service;
import edu.columbia.slime.service.Event;
import edu.columbia.slime.service.MessageEvent;

public class UIService extends Service {

	private static final URI BASE_URI = URI.create("http://128.59.14.28:8080/base/");
	public static final String ROOT_PATH = "ui";

	private HttpServer server = null;

	public String getName() {
		return "ui";
	}

	@Path("ui")
	public static class UIResource {
		public static final String CLICHED_MESSAGE = "closed all";

		@GET
		@Produces("text/plain")
		public String getClose() {
			LOG.info("22 UIService got close request!");
			Slime.getInstance().registerEvent(new MessageEvent("manage", null, "closeAll"), null);
			return CLICHED_MESSAGE;
		}
	}

	public void init() throws IOException {
		if (!Slime.isMaster())
			return;
		LOG.info("In master, trying to launch an http server");
		UIResource uir = new UIResource();
		final ResourceConfig resourceConfig = new ResourceConfig(UIResource.class);
		final HttpServer server = GrizzlyHttpServerFactory.createHttpServer(BASE_URI, resourceConfig);

//		server.start();
	}

	public void dispatch(Event e) throws IOException {
	}

	public void close() throws IOException {
		if (server != null)
			server.shutdownNow();
	}
}
