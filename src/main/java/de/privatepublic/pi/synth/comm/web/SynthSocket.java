package de.privatepublic.pi.synth.comm.web;
import java.io.IOException;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.SynthPi;
import de.privatepublic.pi.synth.comm.ControlMessageDispatcher;

@WebSocket
public class SynthSocket {

	private static final Logger log = LoggerFactory.getLogger(SynthSocket.class);
	public static final Vector<Session> ACTIVE_SESSIONS = new Vector<Session>(5); // Synchronize access?

	private Session session;
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	@OnWebSocketConnect
	public void onConnect(final Session session) {
		this.session = session;
		String info = browserInfo();
		log.info("Connection to: {} {}", info, session.getRemoteAddress().getAddress());
		ACTIVE_SESSIONS.add(this.session);
		SynthPi.uiMessage("Browser connected (#"+ACTIVE_SESSIONS.size()+"): "+info);
		scheduler.schedule(new Runnable() {
			@Override
			public void run() {
				//OSCHandler.instance().sendAll(true);
				ControlMessageDispatcher.INSTANCE.updateAllParams(session);
				log.debug("Sent initial parameters dump to {}", session.getRemoteAddress().getAddress());
			}
		}, 1, TimeUnit.SECONDS); 
	}

	@OnWebSocketClose
	public void onClose(int statusCode, String reason) {
		log.info("Connection closed: {} (status: {}, reason: {})", session.getRemoteAddress().getAddress(), statusCode, reason);
		ACTIVE_SESSIONS.remove(this.session);			
		SynthPi.uiMessage("Browser disconnected (active: "+ACTIVE_SESSIONS.size()+")");
	}


	@OnWebSocketMessage
	public void onText(String msg) {
		if (!"*".equals(msg)) { // ping
			ControlMessageDispatcher.INSTANCE.handleOscMessage(msg, session);
		}
	}

	@OnWebSocketError    
	public void handleError(Throwable error) {
		log.error("Web socket error: {}. Closing.", error.getMessage());
		if (session.isOpen()) {
			try {
				session.disconnect();
			} catch (IOException e) {

			}
			ACTIVE_SESSIONS.remove(session);
		}
	}

	private String browserInfo() {
		UpgradeRequest req = session.getUpgradeRequest();
		String ua = req.getHeader("user-agent");
		return ua;
	}
	
}
