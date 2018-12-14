package gov.usgs.earthquake.indexer;

import java.util.logging.Logger;

import gov.usgs.util.Config;
import gov.usgs.util.WebSocketServer;

/**
 * Listener that broadcasts indexer events over a websocket.
 */
public class WebSocketIndexerListener extends DefaultIndexerListener {
    /** Logging object. */
    private final Logger LOGGER = Logger.getLogger(WebSocketIndexerListener.class.getName());

    /** Configuration property for port. */
    private static final String PORT_PROPERTY = "port";

    /** Websocket for broadcast. */
    private WebSocketServer webSocket;

    /** Default websocket port. */
    private int port = 8080;

    /**
     * Override the IndexerListener to broadcast the event.
     */
    @Override
    public void onIndexerEvent(IndexerEvent event) throws Exception {
        webSocket.broadcast(new JsonEventSummary(event).toJsonObject().toString());
    }

    @Override
    public void configure(final Config config) {
        String portValue = config.getProperty(PORT_PROPERTY, "8080");
        this.port = Integer.valueOf(portValue);
        LOGGER.config("[" + getName() + "] web socket port " + this.port);
    }

    /**
     * Start the websocket server.
     */
    @Override
    public void startup() throws Exception {
        if (webSocket == null) {
            webSocket = new WebSocketServer(port);
            webSocket.start();
        }
    }

    /**
     * Stop the websocket server.
     */
    @Override
    public void shutdown() throws Exception {
        if (webSocket != null) {
            webSocket.stop();
            webSocket = null;
        }
    }

}
