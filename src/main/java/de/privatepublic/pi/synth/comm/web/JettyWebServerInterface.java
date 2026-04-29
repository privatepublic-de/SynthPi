package de.privatepublic.pi.synth.comm.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.Hashtable;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;
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
		if (INSTANCE == null) {
			INSTANCE = new JettyWebServerInterface();
		}
	}

	public JettyWebServerInterface() {
		CachedResource.initCache();
		try {
			Server server = new Server(P.PORT_HTTP);

			WebSocketUpgradeHandler wsHandler = WebSocketUpgradeHandler.from(server, container ->
				container.addMapping(SOCKET_PATH, (req, resp, cb) -> new SynthSocket()));
			wsHandler.setHandler(new WebResourceHandler());
			server.setHandler(wsHandler);

			server.start();
			log.info("Started jetty web server on port {}", P.PORT_HTTP);
			if (!DISABLE_BROWSER_START) {
				AppWindow.openWebBrowser();
			} else {
				log.info("Conntect to SynthPi with: http://{}:{}",
					de.privatepublic.pi.synth.util.IOUtils.localV4InetAddressDisplayString(), P.PORT_HTTP);
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Could not start jetty webserver", e);
		}
	}


	public static class WebResourceHandler extends Handler.Abstract {

		@Override
		public boolean handle(Request request, Response response, Callback callback) throws Exception {
			String target = Request.getPathInContext(request);
			if (target.startsWith(SOCKET_PATH)) {
				return false;
			}
			String fileName = target.length() <= 1 ? "index.html" : target.substring(1);
			CachedResource res = CachedResource.get(fileName);
			if (res != null) {
				response.getHeaders().put(HttpHeader.CONTENT_TYPE, res.mimeType());
				response.getHeaders().put(HttpHeader.CONTENT_LENGTH, res.length());
				response.write(true, ByteBuffer.wrap(res.bytes()), callback);
			} else {
				Response.writeError(request, response, callback, 404, "Resource " + target + " not found!");
			}
			return true;
		}
	}


	private static class CachedResource {

		private static final String[] CACHE_FILENAMES = {
			"img/favicon.png",
			"img/frouting-s.png",
			"img/frouting-p.png",
			"img/frouting-o.png",
			"img/green-led.png",
			"img/ic_close_black_18dp.png",
			"img/ic_close_white_18dp.png",
			"img/ic_delete_black_18dp.png",
			"img/ic_delete_white_18dp.png",
			"img/ic_brightness_medium_black_18dp.png",
			"img/ic_brightness_medium_white_18dp.png",
			"img/ic_refresh_black_18dp.png",
			"img/ic_refresh_white_18dp.png",
			"img/ic_save_black_18dp.png",
			"img/ic_save_white_18dp.png",
			"img/ic_settings_black_18dp.png",
			"img/ic_settings_input_component_black_18dp.png",
			"img/ic_settings_input_component_white_18dp.png",
			"img/ic_settings_white_18dp.png",
			"img/ic_shuffle_black_18dp.png",
			"img/ic_shuffle_white_18dp.png",
			"index.html",
			"styles.css",
			// Vanilla-JS UI modules (replacing the jQuery UI). Phase 1 ships
			// app.js / controls.js / socket.js; later phases add more modules
			// (panels.js, patches.js, settings.js, learn.js, matrix.js, etc.)
			// — each new file must be appended to this array in the same
			// commit it's introduced or the server returns 404 in production.
			"app.js",
			"controls.js",
			"socket.js",
			"modal.js",
			"patches.js",
			"settings.js",
			"learn.js",
			"matrix.js",
			"keyboard.js",
			"waveform.js"
		};

		private String mimeType;
		private byte[] data;

		public static void initCache() {
			for (String filename : CACHE_FILENAMES) {
				get(filename);
			}
			log.debug("Cached {} files", CACHE_FILENAMES.length);
		}

		private CachedResource(String filename) throws IOException {
			InputStream in = JettyWebServerInterface.class.getResourceAsStream("/webresources/" + filename);
			if (in != null) {
				data = IOUtils.toByteArray(in);
				mimeType = URLConnection.guessContentTypeFromName(filename);
				if (mimeType == null || (filename.endsWith(".js") && !mimeType.contains("javascript"))) {
					if (filename.endsWith(".css")) {
						mimeType = "text/css";
					} else if (filename.endsWith(".js")) {
						// ES modules require "text/javascript" or "application/javascript".
						// Some JVMs return "application/x-javascript" which Chrome/Safari
						// refuse to load as a module — force the spec-compliant string.
						mimeType = "application/javascript";
					} else {
						mimeType = "application/octet-stream";
					}
				}
			} else {
				throw new IOException("Ressource not found: " + filename);
			}
		}

		public byte[] bytes() {
			return data;
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
			if (result == null || DISABLE_CACHING) {
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
