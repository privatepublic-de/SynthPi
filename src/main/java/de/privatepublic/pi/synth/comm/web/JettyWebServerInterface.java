package de.privatepublic.pi.synth.comm.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Hashtable;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.AppWindow;
import de.privatepublic.pi.synth.P;

public class JettyWebServerInterface {

	public static boolean DISABLE_CACHING = false;
	public static boolean DISABLE_BROWSER_START = false;
	
	private static final Logger log = LoggerFactory.getLogger(JettyWebServerInterface.class);
	private static final String SOCKET_PATH = "/oscsocket";
	private static JettyWebServerInterface INSTANCE;
	
	public static void init() {
		if (INSTANCE==null) {
//			final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger("org.eclipse.jetty");
//			if ((logger instanceof ch.qos.logback.classic.Logger)) {
//				ch.qos.logback.classic.Logger logbackLogger = (ch.qos.logback.classic.Logger) logger;
//				logbackLogger.setLevel(ch.qos.logback.classic.Level.WARN);    
//			}
			INSTANCE = new JettyWebServerInterface();
		}
	}
	
	public JettyWebServerInterface() {
		CachedResource.initCache();
        try {
        	Server server = new Server(P.PORT_HTTP);        	
        	ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
            ctx.setContextPath("/");
            ctx.addServlet(SocketServlet.class, SOCKET_PATH);
        	HandlerList handlerList = new HandlerList();
        	handlerList.setHandlers(new Handler[] {new WebResourceHandler(), ctx});
        	
            server.setHandler(handlerList);
        	
        	server.start();
        	log.info("Started jetty web server on port {}", P.PORT_HTTP);
        	if (!DISABLE_BROWSER_START) {
        		AppWindow.openWebBrowser();
        	}
        	else {
        		log.info("Conntect to SynthPi with: http://{}:{}", de.privatepublic.pi.synth.util.IOUtils.localV4InetAddressDisplayString(), P.PORT_HTTP);
        	}
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Could not start jetty webserver", e);
		}
		
	}


	public static class WebResourceHandler extends AbstractHandler {

		public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
			if (!target.startsWith(SOCKET_PATH)) {
				String fileName = null;
				if (target.length()<=1) {
					// deliver default (page)
					fileName = "index.html";
				}
				else {
					fileName = target.substring(1);
				}
				CachedResource res = CachedResource.get(fileName);
				if (res!=null) {
					response.setContentType(res.mimeType());
					response.setContentLength(res.length());
					response.setStatus(HttpServletResponse.SC_OK);
					IOUtils.copy(res.inputStream(), response.getOutputStream());
				}
				else {
					response.setContentType("text/plain");
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
					IOUtils.write("Resource "+target+" not found!", response.getOutputStream());
				}
				baseRequest.setHandled(true);
			}
	    }
	}
	
	
	
	public static class SocketServlet extends WebSocketServlet {

		private static final long serialVersionUID = 1748374769253999991L;

		@Override
		public void configure(WebSocketServletFactory factory) {
			factory.register(SynthSocket.class);
		}

	}
	
	
	private static class CachedResource {
		
		private static final String[] CACHE_FILENAMES = {
			"img/favicon.png",
			"img/green-led.png",
			"img/bg_saw.png",
			"img/bg_sin.png",
			"img/bg_pul.png",
			"img/bg_tri.png",
			"img/bg_saw_sel.png",
			"img/bg_sin_sel.png",
			"img/bg_pul_sel.png",
			"img/bg_tri_sel.png",
			"img/ic_close_white_18dp.png",
			"img/ic_delete_white_18dp.png",
			"img/ic_refresh_white_36dp.png",
			"img/ic_save_white_36dp.png",
			"img/ic_settings_white_36dp.png",
			"img/ic_shuffle_white_36dp.png",
			"img/ic_midi.png",
			"img/synthpi192x192.png",
			"index.html",
			"jquery-2.1.4.min.js",
			"jquery.color-2.1.2.min.js",
			"jquery.knob.min.js",
			"styles.css",
			"synthcontrol.js",
			"manifest.json"
		};
		
		private String mimeType;
		private byte[] data;
		
		public static void initCache() {
			for (String filename:CACHE_FILENAMES) {
				get(filename);
			}
			log.debug("Cached {} files", CACHE_FILENAMES.length);
		}
		
		private CachedResource(String filename) throws IOException {
			InputStream in = JettyWebServerInterface.class.getResourceAsStream("/webresources/"+filename);
			if (in!=null) {
				data = IOUtils.toByteArray(in);
				mimeType = URLConnection.guessContentTypeFromName(filename);
				if (mimeType==null) {
					if (filename.endsWith(".css")) {
						mimeType = "text/css";
					}
					else if (filename.endsWith(".js")) {
						mimeType = "application/x-javascript";
					}
					else {
						mimeType = "application/octet-stream";
					}
				}
			}
			else {
				throw new IOException("Ressource not found: "+filename);
			}
		}
		
		public ByteArrayInputStream inputStream() {
			return new ByteArrayInputStream(data);
		}
		
		public int length() {
			return data.length;
		}
		
		public String mimeType() {
			return mimeType;
		}
		
		
		private static Map<String, CachedResource> cache = new Hashtable<String, CachedResource>();
		
		public static CachedResource get(String filename) {
			CachedResource result = cache.get(filename);
			if (result==null || DISABLE_CACHING) {
				try {
					result = new CachedResource(filename);
					cache.put(filename, result);
				} catch (IOException e) {
					log.warn("Resource not found: {}", filename);
				}
			}
			return result;
		}
		
	}
	
}
