package com.inc.vasconcellos.apollo;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.On;
import com.github.nkzawa.socketio.client.SocketIOException;
import com.github.nkzawa.socketio.parser.Packet;
import com.github.nkzawa.socketio.parser.Parser;
import com.github.nkzawa.thread.EventThread;

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

public class SocketManager extends Emitter {

    public static final String TAG = SocketManager.class.getSimpleName();

    /*package*/ enum ConnectionState {
        CLOSED, CLOSING, OPENING, OPEN, RECONNECTING
    }

    /*package*/ enum NetworkState {
        ENABLE, DISABLE
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


    /*package*/ static SSLContext defaultSSLContext;

    /*package*/ ConnectionState connectionState = null;

    private boolean canReconnect;
    private boolean skipReconnect;
    private boolean encoding;
    private int reconnectionAttempts;
    private int attempt;
    private long reconnectionDelay;
    private long reconnectionDelayMax;
    private long timeout;
    private Set<Socket> connected;
    private URI uri;
    private List<Packet> packetBuffer;
    private Queue<On.Handle> subs;
    private Options opts;
    private com.github.nkzawa.engineio.client.Socket engine;
    private Parser.Encoder encoder;
    private Parser.Decoder decoder;
    private ConnectivityReceiver connectivityReceiver;
    private NetworkState networkState;
    private Context context;

    /**
     * This HashMap can be accessed from outside of EventThread.
     */
    private ConcurrentHashMap<String, Socket> nsps;

    private ScheduledExecutorService timeoutScheduler;
    private ScheduledExecutorService reconnectScheduler;


    public SocketManager(Context context) {
        this(context, null, null);
    }

    public SocketManager(Context context, URI uri) {
        this(context, uri, null);
    }

    public SocketManager(Context context, Options opts) {
        this(context, null, opts);
    }

    public SocketManager(Context context, URI uri, Options opts) {
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
        this.networkState = ConnectivityReceiver.isNetworkAvailable(context)? NetworkState.ENABLE : NetworkState.DISABLE;
        this.uri = uri;
        this.connected = new HashSet<>();
        this.attempt = 0;
        this.encoding = false;
        this.packetBuffer = new ArrayList<>();
        this.encoder = new Parser.Encoder();
        this.decoder = new Parser.Decoder();
        this.context = context;

        //Initialize Connection Receiver
        connectivityReceiver = new ConnectivityReceiver(
                new Runnable() {
                    @Override
                    public void run() {
                        if(SocketManager.this.connectionState != ConnectionState.OPEN &&
                                SocketManager.this.connectionState != ConnectionState.OPENING &&
                                    SocketManager.this.connectionState != ConnectionState.RECONNECTING){
                            SocketManager.this.connectionState = ConnectionState.OPENING;
                            SocketManager.this.networkState = NetworkState.ENABLE;

                            Log.d(ConnectivityReceiver.TAG, "Network Enabled, Reconnecting Socket Manager...");

                            SocketManager.this.emitAll(SocketManager.EVENT_NETWORK_ONLINE);
                            SocketManager.this.open();
                        }
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        if(SocketManager.this.connectionState != ConnectionState.CLOSED &&
                                SocketManager.this.connectionState != ConnectionState.CLOSING){
                            SocketManager.this.connectionState = ConnectionState.CLOSING;
                            SocketManager.this.networkState = NetworkState.DISABLE;

                            Log.d(ConnectivityReceiver.TAG, "Network Disable, Disconnecting Socket Manager...");

                            SocketManager.this.emitAll(SocketManager.EVENT_NETWORK_OFFLINE);
                            SocketManager.this.close();
                        }
                    }
                }
        );
    }

    /**
     * Emit Event for Socket Manager and all Sockets in Queue
     *
     * @param event = Event Name
     * @param args = Event Arguments
     */
    private void emitAll(String event, Object... args) {
        this.emit(event, args);
        for (Socket socket : this.nsps.values()) {
            socket.emit(event, args);
        }
    }

    /**
     * Open connection to the client.
     *
     * @param fn callback.
     * @return a reference to this object.
     */
    public SocketManager open(final OpenCallback fn) {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                // Register Connectivity Receiver to Connectivity Action Android Intent
                SocketManager.this.connectivityReceiver.registerReciver(SocketManager.this.context);

                if (SocketManager.this.connectionState == ConnectionState.OPEN) {
                    if (fn != null) {
                        fn.call(null);
                    }

                    return;

                }else if (!SocketManager.this.isNetworkEnable()){
                    Log.d(TAG, "Network Disabled, will open connection to " + SocketManager.this.uri + " when network is enabled");

                    if (fn != null) {
                        fn.call(new SocketIOException("Network Disabled"));
                    }

                    SocketManager.this.emitAll(SocketManager.EVENT_NETWORK_OFFLINE);

                    return;

                }else if(SocketManager.this.connectionState != ConnectionState.RECONNECTING){
                    Log.v(TAG, "Opening connection to " + SocketManager.this.uri);

                    connectionState = ConnectionState.OPENING;
                }

                skipReconnect = false;
                engine = new Engine(SocketManager.this.uri, SocketManager.this.opts);

                // propagate transport event.
                engine.on(Engine.EVENT_TRANSPORT, new Listener() {
                    @Override
                    public void call(Object... args) {
                        SocketManager.this.emit(SocketManager.EVENT_TRANSPORT, args);
                    }
                });

                final On.Handle openSub = On.on(SocketManager.this.engine, Engine.EVENT_OPEN, new Listener() {
                    @Override
                    public void call(Object... objects) {
                        SocketManager.this.onConnectionOpen();

                        if (fn != null) {
                            fn.call(null);
                        }
                    }
                });

                On.Handle errorSub = On.on(SocketManager.this.engine, Engine.EVENT_ERROR, new Listener() {
                    @Override
                    public void call(Object... objects) {
                        Object data = objects.length > 0 ? objects[0] : null;

                        SocketManager.this.onConnectionError(data);

                        if (fn != null) {
                            fn.call(new SocketIOException("Connection error",
                                    data instanceof Exception ? (Exception) data : null));
                        }

                        if (SocketManager.this.canReconnect && SocketManager.this.connectionState != ConnectionState.RECONNECTING) {
                            Log.i(TAG, "Connection Failed, will attempt to reconnect");
                            SocketManager.this.attempt = 0;
                            SocketManager.this.reconnect();
                        }
                    }
                });

                if (SocketManager.this.timeout >= 0) {
                    Log.v(TAG, "Connection attempt will timeout after " + SocketManager.this.timeout);

                    final Future timer = getTimeoutScheduler().schedule(new Runnable() {
                        @Override
                        public void run() {
                            EventThread.exec(new Runnable() {
                                @Override
                                public void run() {
                                    Log.v(TAG, "Connect attempt timed out after " + SocketManager.this.timeout);

                                    openSub.destroy();

                                    SocketManager.this.engine.close();
                                    SocketManager.this.engine.emit(Engine.EVENT_ERROR, new SocketIOException("timeout"));
                                    SocketManager.this.emitAll(EVENT_CONNECT_TIMEOUT, SocketManager.this.timeout);
                                }
                            });
                        }
                    }, SocketManager.this.timeout, TimeUnit.MILLISECONDS);

                    SocketManager.this.subs.add(new On.Handle() {
                        @Override
                        public void destroy() {
                            timer.cancel(false);
                        }
                    });
                }

                SocketManager.this.subs.add(openSub);
                SocketManager.this.subs.add(errorSub);

                SocketManager.this.engine.open();
            }
        });

        return this;
    }

    /**
     * Overload Open Function
     */
    public SocketManager open(){
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
                On.on(SocketManager.this.engine, Engine.EVENT_DATA, new Listener() {
                    @Override
                    public void call(Object... objects) {
                        Object data = objects[0];
                        if (data instanceof String) {
                            SocketManager.this.onData((String) data);
                        } else if (data instanceof byte[]) {
                            SocketManager.this.onData((byte[]) data);
                        }
                    }
                })
        );
        this.subs.add(
                On.on(SocketManager.this.decoder, Parser.Decoder.EVENT_DECODED, new Listener() {
                    @Override
                    public void call(Object... objects) {
                        SocketManager.this.onDecoded((Packet) objects[0]);
                    }
                })
        );
        this.subs.add(
                On.on(SocketManager.this.engine, Engine.EVENT_ERROR, new Listener() {
                    @Override
                    public void call(Object... objects) {
                        SocketManager.this.onError((Exception) objects[0]);
                    }
                })
        );
        this.subs.add(
                On.on(SocketManager.this.engine, Engine.EVENT_CLOSE, new Listener() {
                    @Override
                    public void call(Object... objects) {
                        SocketManager.this.onConnectionClose((String) objects[0]);
                    }
                })
        );
    }

    /**
     * Handler for connection error
     */
    private void onConnectionError(Object data) {
        Log.v(TAG, "Connection Error");

        SocketManager.this.cleanup();

        SocketManager.this.connectionState = ConnectionState.CLOSED;

        SocketManager.this.emitAll(EVENT_CONNECT_ERROR, data);
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
     * Initializes {@link com.github.nkzawa.socketio.client.Socket} instances for each namespaces.
     *
     * @param nsp namespace.
     * @return a socket instance for the namespace.
     */
    public Socket socket(String nsp) {
        Socket socket = this.nsps.get(nsp);
        if (socket == null) {
            socket = new Socket(this, nsp);
            Socket _socket = this.nsps.putIfAbsent(nsp, socket);
            if (_socket != null) {
                socket = _socket;
            } else {
                final Socket s = socket;
                socket.on(com.github.nkzawa.socketio.client.Socket.EVENT_CONNECT, new Listener() {
                    @Override
                    public void call(Object... objects) {
                        SocketManager.this.connected.add(s);
                    }
                });
            }
        }
        return socket;
    }

    /*package*/ void destroy(Socket socket) {
        this.connected.remove(socket);

        if (this.connected.size() > 0){
            return;
        }

        //Unregister Connectivity Receiver
        this.connectivityReceiver.unregisterReceiver();

        this.close();
    }

    /*package*/ void packet(Packet packet) {
        Log.v(TAG, String.format("Writing packet %s", packet));

        if (!SocketManager.this.encoding) {
            SocketManager.this.encoding = true;
            this.encoder.encode(packet, new Parser.Encoder.Callback() {
                @Override
                public void call(Object[] encodedPackets) {
                    for (Object packet : encodedPackets) {
                        if (packet instanceof String) {
                            SocketManager.this.engine.write((String)packet);
                        } else if (packet instanceof byte[]) {
                            SocketManager.this.engine.write((byte[])packet);
                        }
                    }
                    SocketManager.this.encoding = false;
                    SocketManager.this.processPacketQueue();
                }
            });
        } else {
            SocketManager.this.packetBuffer.add(packet);
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
        Log.d(TAG, "Connection Closed");
        this.cleanup();
        this.connectionState = ConnectionState.CLOSED;
        this.emit(EVENT_CLOSE, reason, networkState == NetworkState.ENABLE);

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
        if (this.skipReconnect || !this.isNetworkEnable()){
            return;
        }else{
            this.attempt++;
        }

        if (this.attempt > this.reconnectionAttempts) {
            Log.d(TAG, "Reconnection failed");

            this.skipReconnect = true;
            this.emitAll(EVENT_RECONNECT_FAILED);
            this.connectionState = ConnectionState.CLOSED;

        } else {
            this.connectionState = ConnectionState.RECONNECTING;

            long delay = Math.min(attempt * reconnectionDelay(), this.reconnectionDelayMax());
            Log.v(TAG, String.format("Will wait %dms before reconnect attempt", delay));

            final Future timer = this.getReconnectScheduler().schedule(new Runnable() {
                @Override
                public void run() {
                    EventThread.exec(new Runnable() {
                        @Override
                        public void run() {
                            if (SocketManager.this.skipReconnect || !SocketManager.this.isNetworkEnable()){
                                return;
                            }

                            Log.d(TAG, "Reconnect Attempt: " + SocketManager.this.attempt);

                            SocketManager.this.emitAll(EVENT_RECONNECT_ATTEMPT, SocketManager.this.attempt);
                            SocketManager.this.emitAll(EVENT_RECONNECTING, SocketManager.this.attempt);

                            // check again for the case socket closed in above events
                            if (SocketManager.this.skipReconnect || !SocketManager.this.isNetworkEnable()){
                                return;
                            }

                            SocketManager.this.open(new OpenCallback() {
                                @Override
                                public void call(Exception err) {
                                    if (err != null) {
                                        SocketManager.this.onReconnectError(err);
                                    } else {
                                        SocketManager.this.onReconnect();
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

        SocketManager.this.reconnect();
        SocketManager.this.emitAll(EVENT_RECONNECT_ERROR, err);
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

    public SocketManager canReconnect(boolean v) {
        this.canReconnect = v;
        return this;
    }

    public int reconnectionAttempts() {
        return this.reconnectionAttempts;
    }

    public SocketManager setReconnectionAttempts(int v) {
        this.reconnectionAttempts = v;
        return this;
    }

    public long reconnectionDelay() {
        return this.reconnectionDelay;
    }

    public SocketManager setReconnectionDelay(long v) {
        this.reconnectionDelay = v;
        return this;
    }

    public long reconnectionDelayMax() {
        return this.reconnectionDelayMax;
    }

    public SocketManager setReconnectionDelayMax(long v) {
        this.reconnectionDelayMax = v;
        return this;
    }

    public long timeout() {
        return this.timeout;
    }

    public SocketManager setTimeout(long v) {
        this.timeout = v;
        return this;
    }

    public boolean isNetworkEnable(){
        if(!this.connectivityReceiver.isRegistered()){
            this.networkState = ConnectivityReceiver.isNetworkAvailable(this.context)? NetworkState.ENABLE : NetworkState.DISABLE;
        }

        return this.networkState == NetworkState.ENABLE;
    }

    public SocketManager forceReconnection(){
        if(this.connectionState == ConnectionState.CLOSED && this.isNetworkEnable()){
            this.attempt = 0;
            this.skipReconnect = false;

            this.reconnect();
        }

        return this;
    }
}
