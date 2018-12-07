package gov.usgs.earthquake.indexer;

import gov.usgs.util.WebSocketServer;


/**
 * Listener that broadcasts indexer events over a websocket.
 */
public class WebSocketIndexerListener extends DefaultIndexerListener {

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
