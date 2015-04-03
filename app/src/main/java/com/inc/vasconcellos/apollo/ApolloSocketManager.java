package com.inc.vasconcellos.apollo;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.On;
import com.github.nkzawa.socketio.client.Socket;
import com.github.nkzawa.socketio.client.SocketIOException;
import com.github.nkzawa.socketio.parser.Packet;
import com.github.nkzawa.socketio.parser.Parser;
import com.github.nkzawa.thread.EventThread;

import org.java_websocket.WebSocket;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

public class ApolloSocketManager extends Emitter {

    public static final String TAG = "ApolloSocketManager"; 

    /*package*/ enum ConnectionState {
        CLOSED, CLOSING, OPENING, OPEN, RECONNECTING
    }

    public static interface OpenCallback {

        public void call(Exception err);
    }


    private static class Engine extends com.github.nkzawa.engineio.client.Socket {

        Engine(URI uri, Options opts) {
            super(uri, opts);
        }
    }

    public static class Options extends com.github.nkzawa.engineio.client.Socket.Options {

        public boolean reconnection = true;
        public int reconnectionAttempts;
        public long reconnectionDelay;
        public long reconnectionDelayMax;
        public long timeout = -1;
    }

    /**
     * Called on a successful connection.
     */
    public static final String EVENT_OPEN = "open";

    /**
     * Called on a disconnection.
     */
    public static final String EVENT_CLOSE = "close";
    public static final String EVENT_PACKET = "packet";
    public static final String EVENT_ERROR = "error";

    /**
     * Called on a connection error.
     */
    public static final String EVENT_CONNECT_ERROR = "connect_error";

    /**
     * Called on a connection timeout.
     */
    public static final String EVENT_CONNECT_TIMEOUT = "connect_timeout";

    /**
     * Called on a successful reconnection.
     */
    public static final String EVENT_RECONNECT = "reconnect";

    /**
     * Called on a reconnection attempt error.
     */
    public static final String EVENT_RECONNECT_ERROR = "reconnect_error";
    public static final String EVENT_RECONNECT_FAILED = "reconnect_failed";
    public static final String EVENT_RECONNECT_ATTEMPT = "reconnect_attempt";
    public static final String EVENT_RECONNECTING = "reconnecting";

    /**
     * Called on a network state change
     */
    public static final String EVENT_NETWORK_OFFLINE = "network_offline";
    public static final String EVENT_NETWORK_ONLINE = "network_online";

    /**
     * Called when a new transport is created. (experimental)
     */
    public static final String EVENT_TRANSPORT = Engine.EVENT_TRANSPORT;


    private static SSLContext defaultSSLContext;

    /*package*/ ConnectionState connectionState = null;

    private boolean canReconnect;
    private boolean skipReconnect;
    private boolean encoding;
    private boolean openReconnect;
    private int reconnectionAttempts;
    private int attempt;
    private long reconnectionDelay;
    private long reconnectionDelayMax;
    private long timeout;
    private Set<ApolloSocket> connected;
    private URI uri;
    private List<Packet> packetBuffer;
    private Queue<On.Handle> subs;
    private Options opts;
    private com.github.nkzawa.engineio.client.Socket engine;
    private Parser.Encoder encoder;
    private Parser.Decoder decoder;
    private ConnectivityReceiver connectivityReceiver;
    private Context context;

    /**
     * This HashMap can be accessed from outside of EventThread.
     */
    private ConcurrentHashMap<String, ApolloSocket> nsps;

    private ScheduledExecutorService timeoutScheduler;
    private ScheduledExecutorService reconnectScheduler;


    public ApolloSocketManager(Context context) {
        this(context, null, null);
    }

    public ApolloSocketManager(Context context, URI uri) {
        this(context, uri, null);
    }

    public ApolloSocketManager(Context context, Options opts) {
        this(context, null, opts);
    }

    public ApolloSocketManager(Context context, URI uri, Options opts) {
        if (opts == null) {
            opts = new Options();
        }
        if (opts.path == null) {
            opts.path = "/socket.io";
        }
        if (opts.sslContext == null) {
            opts.sslContext = defaultSSLContext;
        }
        this.opts = opts;
        this.nsps = new ConcurrentHashMap<>();
        this.subs = new LinkedList<>();
        this.canReconnect(opts.reconnection);
        this.setReconnectionAttempts(opts.reconnectionAttempts != 0 ? opts.reconnectionAttempts : Integer.MAX_VALUE);
        this.setReconnectionDelay(opts.reconnectionDelay != 0 ? opts.reconnectionDelay : 1000);
        this.setReconnectionDelayMax(opts.reconnectionDelayMax != 0 ? opts.reconnectionDelayMax : 5000);
        this.setTimeout(opts.timeout < 0 ? 20000 : opts.timeout);
        this.connectionState = ConnectionState.CLOSED;
        this.uri = uri;
        this.connected = new HashSet<ApolloSocket>();
        this.attempt = 0;
        this.encoding = false;
        this.packetBuffer = new ArrayList<Packet>();
        this.encoder = new Parser.Encoder();
        this.decoder = new Parser.Decoder();
        this.context = context;

        //Initialize Connection Receiver
        connectivityReceiver = new ConnectivityReceiver(
                new Runnable() {
                    @Override
                    public void run() {
                        if(ApolloSocketManager.this.connectionState != ConnectionState.OPEN &&
                                ApolloSocketManager.this.connectionState != ConnectionState.OPENING &&
                                    ApolloSocketManager.this.connectionState != ConnectionState.RECONNECTING){
                            Log.d(ConnectivityReceiver.TAG, "Network Enabled, Reconnecting Socket Manager...");
                            ApolloSocketManager.this.emit(ApolloSocketManager.EVENT_NETWORK_ONLINE);
                            ApolloSocketManager.this.connectionState = ConnectionState.OPENING;
                            ApolloSocketManager.this.open();
                        }
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        if(ApolloSocketManager.this.connectionState != ConnectionState.CLOSED &&
                                ApolloSocketManager.this.connectionState != ConnectionState.CLOSING){
                            Log.d(ConnectivityReceiver.TAG, "Network Disable, Disconnecting Socket Manager...");
                            ApolloSocketManager.this.emit(ApolloSocketManager.EVENT_NETWORK_OFFLINE);
                            ApolloSocketManager.this.connectionState = ConnectionState.CLOSING;
                            ApolloSocketManager.this.close();
                        }
                    }
                }
        );
    }

    private void emitAll(String event, Object... args) {
        this.emit(event, args);
        for (ApolloSocket socket : this.nsps.values()) {
            socket.emit(event, args);
        }
    }

    /**
     * Open connection to the client.
     *
     * @param fn callback.
     * @return a reference to this object.
     */
    public ApolloSocketManager open(final OpenCallback fn) {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "ConnectionState " + ApolloSocketManager.this.connectionState);

                if (ApolloSocketManager.this.connectionState == ConnectionState.OPEN) {
                    return;

                }else if(ApolloSocketManager.this.connectionState != ConnectionState.RECONNECTING){
                    Log.v(TAG, "Opening connection to " + ApolloSocketManager.this.uri);

                    connectionState = ConnectionState.OPENING;
                }

                skipReconnect = false;
                engine = new Engine(ApolloSocketManager.this.uri, ApolloSocketManager.this.opts);

                // propagate transport event.
                engine.on(Engine.EVENT_TRANSPORT, new Listener() {
                    @Override
                    public void call(Object... args) {
                        ApolloSocketManager.this.emit(ApolloSocketManager.EVENT_TRANSPORT, args);
                    }
                });

                final On.Handle openSub = On.on(ApolloSocketManager.this.engine, Engine.EVENT_OPEN, new Listener() {
                    @Override
                    public void call(Object... objects) {
                        ApolloSocketManager.this.onConnectionOpen();

                        if (fn != null) {
                            fn.call(null);
                        }
                    }
                });

                On.Handle errorSub = On.on(ApolloSocketManager.this.engine, Engine.EVENT_ERROR, new Listener() {
                    @Override
                    public void call(Object... objects) {
                        Object data = objects.length > 0 ? objects[0] : null;

                        ApolloSocketManager.this.onConnectionError(data);

                        if (fn != null) {
                            fn.call(new SocketIOException("Connection error",
                                    data instanceof Exception ? (Exception) data : null));
                        }

                        if (canReconnect && connectionState != ConnectionState.RECONNECTING) {
                            Log.i(TAG, "Connection Failed, will attempt to reconnect");
                            attempt = 0;
                            ApolloSocketManager.this.reconnect();
                        }
                    }
                });

                if (ApolloSocketManager.this.timeout >= 0) {
                    Log.v(TAG, "Connection attempt will timeout after " + ApolloSocketManager.this.timeout);

                    final Future timer = getTimeoutScheduler().schedule(new Runnable() {
                        @Override
                        public void run() {
                            EventThread.exec(new Runnable() {
                                @Override
                                public void run() {
                                    Log.v(TAG, "Connect attempt timed out after " + ApolloSocketManager.this.timeout);

                                    openSub.destroy();

                                    ApolloSocketManager.this.engine.close();
                                    ApolloSocketManager.this.engine.emit(Engine.EVENT_ERROR, new SocketIOException("timeout"));
                                    ApolloSocketManager.this.emitAll(EVENT_CONNECT_TIMEOUT, ApolloSocketManager.this.timeout);
                                }
                            });
                        }
                    }, ApolloSocketManager.this.timeout, TimeUnit.MILLISECONDS);

                    ApolloSocketManager.this.subs.add(new On.Handle() {
                        @Override
                        public void destroy() {
                            timer.cancel(false);
                        }
                    });
                }

                ApolloSocketManager.this.subs.add(openSub);
                ApolloSocketManager.this.subs.add(errorSub);

                ApolloSocketManager.this.engine.open();
            }
        });

        return this;
    }

    /**
     * Overload Open Function
     */
    public ApolloSocketManager open(){
        return open(null);
    }

    /**
     * Handler for connection open
     */
    private void onConnectionOpen() {
        Log.v(TAG, "Connection Open");

        this.cleanup();

        this.connectionState = ConnectionState.OPEN;
        this.emit(EVENT_OPEN);

        this.subs.add(
                On.on(ApolloSocketManager.this.engine, Engine.EVENT_DATA, new Listener() {
                    @Override
                    public void call(Object... objects) {
                        Object data = objects[0];
                        if (data instanceof String) {
                            ApolloSocketManager.this.onData((String) data);
                        } else if (data instanceof byte[]) {
                            ApolloSocketManager.this.onData((byte[]) data);
                        }
                    }
                })
        );
        this.subs.add(
                On.on(ApolloSocketManager.this.decoder, Parser.Decoder.EVENT_DECODED, new Listener() {
                    @Override
                    public void call(Object... objects) {
                        ApolloSocketManager.this.onDecoded((Packet) objects[0]);
                    }
                })
        );
        this.subs.add(
                On.on(ApolloSocketManager.this.engine, Engine.EVENT_ERROR, new Listener() {
                    @Override
                    public void call(Object... objects) {
                        ApolloSocketManager.this.onError((Exception) objects[0]);
                    }
                })
        );
        this.subs.add(
                On.on(ApolloSocketManager.this.engine, Engine.EVENT_CLOSE, new Listener() {
                    @Override
                    public void call(Object... objects) {
                        ApolloSocketManager.this.onConnectionClose((String) objects[0]);
                    }
                })
        );

        // Register Connectivity Receiver to Connectivity Action Android Intent
        this.context.registerReceiver(this.connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    /**
     * Handler for connection error
     */
    private void onConnectionError(Object data) {
        Log.v(TAG, "Connection Error");

        ApolloSocketManager.this.cleanup();

        ApolloSocketManager.this.connectionState = ConnectionState.CLOSED;

        ApolloSocketManager.this.emitAll(EVENT_CONNECT_ERROR, data);
    }

    private void onData(String data) {
        this.decoder.add(data);
    }

    private void onData(byte[] data) {
        this.decoder.add(data);
    }

    private void onDecoded(Packet packet) {
        this.emit(EVENT_PACKET, packet);
    }

    private void onError(Exception err) {
        Log.d(TAG, "Error", err);
        this.emitAll(EVENT_ERROR, err);
    }

    /**
     * Initializes {@link Socket} instances for each namespaces.
     *
     * @param nsp namespace.
     * @return a socket instance for the namespace.
     */
    public ApolloSocket socket(String nsp) {
        ApolloSocket socket = this.nsps.get(nsp);
        if (socket == null) {
            socket = new ApolloSocket(this, nsp);
            ApolloSocket _socket = this.nsps.putIfAbsent(nsp, socket);
            if (_socket != null) {
                socket = _socket;
            } else {
                final ApolloSocket s = socket;
                socket.on(Socket.EVENT_CONNECT, new Listener() {
                    @Override
                    public void call(Object... objects) {
                        ApolloSocketManager.this.connected.add(s);
                    }
                });
            }
        }
        return socket;
    }

    /*package*/ void destroy(ApolloSocket socket) {
        this.connected.remove(socket);

        if (this.connected.size() > 0){
            return;
        }

        //Unregister Connectivity Receiver
        this.context.unregisterReceiver(this.connectivityReceiver);

        this.close();
    }

    /*package*/ void packet(Packet packet) {
        Log.v(TAG, String.format("Writing packet %s", packet));

        if (!ApolloSocketManager.this.encoding) {
            ApolloSocketManager.this.encoding = true;
            this.encoder.encode(packet, new Parser.Encoder.Callback() {
                @Override
                public void call(Object[] encodedPackets) {
                    for (Object packet : encodedPackets) {
                        if (packet instanceof String) {
                            ApolloSocketManager.this.engine.write((String)packet);
                        } else if (packet instanceof byte[]) {
                            ApolloSocketManager.this.engine.write((byte[])packet);
                        }
                    }
                    ApolloSocketManager.this.encoding = false;
                    ApolloSocketManager.this.processPacketQueue();
                }
            });
        } else {
            ApolloSocketManager.this.packetBuffer.add(packet);
        }
    }

    private void processPacketQueue() {
        if (this.packetBuffer.size() > 0 && !this.encoding) {
            Packet pack = this.packetBuffer.remove(0);
            this.packet(pack);
        }
    }

    private void cleanup() {
        On.Handle sub;
        while ((sub = this.subs.poll()) != null) sub.destroy();
    }

    private void close() {
        skipReconnect = true;
        connectionState = ConnectionState.CLOSED;
        if (this.engine != null) {
            this.engine.close();
        }
    }

    private void onConnectionClose(String reason) {
        Log.d(TAG, "Close");
        this.cleanup();
        this.connectionState = ConnectionState.CLOSED;
        this.emit(EVENT_CLOSE, reason);

        if (this.timeoutScheduler != null) {
            this.timeoutScheduler.shutdown();
        }
        if (this.reconnectScheduler != null) {
            this.reconnectScheduler.shutdown();
        }

        if (this.canReconnect && !this.skipReconnect) {
            this.reconnect();
        }
    }

    private void reconnect() {
        if (this.skipReconnect){
            return;
        }else{
            this.attempt++;
        }

        if (attempt > this.reconnectionAttempts) {
            Log.d(TAG, "Reconnection failed");

            this.emitAll(EVENT_RECONNECT_FAILED);
            connectionState = ConnectionState.CLOSED;

        } else {
            connectionState = ConnectionState.RECONNECTING;

            long delay = Math.min(attempt * reconnectionDelay(), this.reconnectionDelayMax());
            Log.v(TAG, String.format("Will wait %dms before reconnect attempt", delay));

            final Future timer = this.getReconnectScheduler().schedule(new Runnable() {
                @Override
                public void run() {
                    EventThread.exec(new Runnable() {
                        @Override
                        public void run() {
                            if (ApolloSocketManager.this.skipReconnect){
                                return;
                            }

                            Log.d(TAG, "Reconnect Attempt: " + ApolloSocketManager.this.attempt);

                            ApolloSocketManager.this.emitAll(EVENT_RECONNECT_ATTEMPT, ApolloSocketManager.this.attempt);
                            ApolloSocketManager.this.emitAll(EVENT_RECONNECTING, ApolloSocketManager.this.attempt);

                            // check again for the case socket closed in above events
                            if (ApolloSocketManager.this.skipReconnect){
                                return;
                            }

                            ApolloSocketManager.this.open(new OpenCallback() {
                                @Override
                                public void call(Exception err) {
                                    if (err != null) {
                                        ApolloSocketManager.this.onReconnectError(err);
                                    } else {
                                        ApolloSocketManager.this.onReconnect();
                                    }
                                }
                            });
                        }
                    });
                }
            }, delay, TimeUnit.MILLISECONDS);

            this.subs.add(new On.Handle() {
                @Override
                public void destroy() {
                    timer.cancel(false);
                }
            });
        }
    }

    private void onReconnect() {
        Log.d(TAG, "Reconnection Succeeded");

        int attempts = this.attempt;
        this.attempt = 0;
        this.emitAll(EVENT_RECONNECT, attempts);
    }

    private void onReconnectError(Exception err) {
        Log.d(TAG, "Reconnect Attempt Error");

        ApolloSocketManager.this.reconnect();
        ApolloSocketManager.this.emitAll(EVENT_RECONNECT_ERROR, err);
    }

    private ScheduledExecutorService getTimeoutScheduler() {
        if (this.timeoutScheduler == null || this.timeoutScheduler.isShutdown()) {
            this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor();
        }
        return timeoutScheduler;
    }

    private ScheduledExecutorService getReconnectScheduler() {
        if (this.reconnectScheduler == null || this.reconnectScheduler.isShutdown()) {
            this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
        }
        return this.reconnectScheduler;
    }

    /**
     * Public Methods
     */

    public boolean canReconnect() {
        return this.canReconnect;
    }

    public ApolloSocketManager canReconnect(boolean v) {
        this.canReconnect = v;
        return this;
    }

    public int reconnectionAttempts() {
        return this.reconnectionAttempts;
    }

    public ApolloSocketManager setReconnectionAttempts(int v) {
        this.reconnectionAttempts = v;
        return this;
    }

    public long reconnectionDelay() {
        return this.reconnectionDelay;
    }

    public ApolloSocketManager setReconnectionDelay(long v) {
        this.reconnectionDelay = v;
        return this;
    }

    public long reconnectionDelayMax() {
        return this.reconnectionDelayMax;
    }

    public ApolloSocketManager setReconnectionDelayMax(long v) {
        this.reconnectionDelayMax = v;
        return this;
    }

    public long timeout() {
        return this.timeout;
    }

    public ApolloSocketManager setTimeout(long v) {
        this.timeout = v;
        return this;
    }
}
