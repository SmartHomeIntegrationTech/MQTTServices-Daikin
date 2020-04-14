package de.karstenbecker.daikin;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.WebSocketClient; 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class WebsocketHelper implements WebSocketListener {

    private final Logger logger = LoggerFactory.getLogger(WebsocketHelper.class);

    private WebSocketClient webSocketClient;

    @Nullable
    private Session session;

    private BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>();

    private static final boolean CAPTURE_RESPONSE = false;

    @Nullable
    private PrintStream ps=null;
    
    public WebsocketHelper() throws Exception {
    	this(createAndStartClient());
    }

	private static WebSocketClient createAndStartClient() throws Exception {
		WebSocketClient client=new WebSocketClient();
    	client.start();
		return client;
	}

    public WebsocketHelper(WebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
        if (CAPTURE_RESPONSE) {
            try {
                this.ps = new PrintStream("responseCapture.txt");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    synchronized public Optional<JsonObject> doQuery(String item) {
        Optional<String> response = null;
        try {
            response = sendQuery(item);
            if (!response.isPresent())
                return Optional.empty();
            if (CAPTURE_RESPONSE) {
                ps.println(item + " " + response.get());
                ps.flush();
            }
            JsonElement ele = JsonParser.parseString(response.get());
            if (!ele.isJsonObject()) {
                logger.warn("Json response is not an obj:" + response);
                return Optional.empty();
            }
            JsonObject obj = ele.getAsJsonObject();
            JsonElement rsp = obj.get("m2m:rsp");
            if (rsp == null || !rsp.isJsonObject()) {
                logger.warn("Expected a m2m:rsp Json object, but got:" + response);
                return Optional.empty();
            }
            return Optional.of(rsp.getAsJsonObject());
        } catch (InterruptedException e) {
            logger.warn("Did not receive a reply within 5 seconds for:" + item);
            return Optional.empty();
        } catch (Exception e) {
            if (response != null)
                logger.warn("An exception occured while trying to access:" + item + " response was:" + response, e);
            else
                logger.warn("An exception occured while trying to access:" + item, e);
            return Optional.empty();
        }
    }

    public Optional<String> sendQuery(String item) throws IOException, InterruptedException {
        String query = "{\"m2m:rqp\":{\"op\":2,\"to\":\"/[0]/MNAE/" + item + "\",\"fr\":\"/OpenHab\",\"rqi\":\""
                + randomString() + "\"}}";
        if (session == null || !session.isOpen()) {
            logger.warn("Tried to query:" + item + " but session was null or not open");
            return Optional.empty();
        }
        String response;
        session.getRemote().sendString(query);
        response = receivedMessages.poll(5, TimeUnit.SECONDS);
        return Optional.of(response);
    }

    public Optional<String> sendDiscovery() throws IOException, InterruptedException {
        String query = "{\"m2m:rqp\":{\"op\":2,\"to\":\"/[0]/MNCSE-node/deviceInfo\",\"fr\":\"/OpenHab\",\"rqi\":\""
                + randomString() + "\"}}";
        if (session == null || !session.isOpen()) {
            logger.warn("Tried to run discovery but session was null or not open");
            return Optional.empty();
        }
        String response;
        session.getRemote().sendString(query);
        response = receivedMessages.poll(5, TimeUnit.SECONDS);
        logger.trace("Discovery Response:"+response);
        return Optional.of(response);
    }

    private static Random random = new Random();

    private String randomString() {
        return Long.toHexString(random.nextLong());
    }

    @Override
    public void onWebSocketClose(int statusCode, @Nullable String reason) {
        logger.trace("Websocket closed with status:" + statusCode);
        if (CAPTURE_RESPONSE)
            ps.close();
    }

    @Override
    public void onWebSocketConnect(@Nullable Session session) {
        logger.trace("Websocket connected to: " + session.getRemoteAddress());
    }

    @Override
    public void onWebSocketError(@Nullable Throwable cause) {
        logger.debug("Websocket error:", cause);
    }

    @Override
    public void onWebSocketBinary( byte @Nullable[] payload, int offset, int len) {
        logger.debug("Unexpected binary content on websocket with length:" + len);
    }

    @Override
    public void onWebSocketText(@Nullable String message) {
        try {
            if (message!=null)
                receivedMessages.put(message);
        } catch (InterruptedException e) {
            // This is so highly unlikely...
            logger.debug("Error while putting message", e);
        }
    }

    public boolean connect(URI url) {
        try {
            Future<Session> sessionFuture = webSocketClient.connect(this, url);
            session = sessionFuture.get(10, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void disconnect() {
        if (session != null)
            session.close();
    }
}