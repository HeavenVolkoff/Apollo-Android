package com.inc.vasconcellos.apollo;

import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.hasbinary.HasBinary;
import com.github.nkzawa.socketio.client.Ack;
import com.github.nkzawa.socketio.client.On;
import com.github.nkzawa.socketio.parser.Packet;
import com.github.nkzawa.socketio.parser.Parser;
import com.github.nkzawa.thread.EventThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class Socket extends Emitter {

    public static final String TAG = Socket.class.getSimpleName();

    /**
     * Called on a connection.
     */
    public static final String EVENT_CONNECT = "connect";

    /**
     * Called on a disconnection.
     */
    public static final String EVENT_DISCONNECT = "disconnect";

    /**
     * Called on a connection error.
     *
     * <p>Parameters:</p>
     * <ul>
     *   <li>(Exception) error data.</li>
     * </ul>
     */
    public static final String EVENT_ERROR = "error";

    public static final String EVENT_MESSAGE = "message";

    public static final String EVENT_NETWORK_OFFLINE = SocketManager.EVENT_NETWORK_OFFLINE;

    public static final String EVENT_NETWORK_ONLINE = SocketManager.EVENT_NETWORK_ONLINE;

    public static final String EVENT_CONNECT_ERROR = SocketManager.EVENT_CONNECT_ERROR;

    public static final String EVENT_CONNECT_TIMEOUT = SocketManager.EVENT_CONNECT_TIMEOUT;

    public static final String EVENT_RECONNECT = SocketManager.EVENT_RECONNECT;

    public static final String EVENT_RECONNECT_ERROR = SocketManager.EVENT_RECONNECT_ERROR;

    public static final String EVENT_RECONNECT_FAILED = SocketManager.EVENT_RECONNECT_FAILED;

    public static final String EVENT_RECONNECT_ATTEMPT = SocketManager.EVENT_RECONNECT_ATTEMPT;

    public static final String EVENT_RECONNECTING = SocketManager.EVENT_RECONNECTING;

    private static Map<String, Integer> events = new HashMap<String, Integer>() {{
        put(EVENT_CONNECT, 1);
        put(EVENT_CONNECT_ERROR, 1);
        put(EVENT_CONNECT_TIMEOUT, 1);
        put(EVENT_DISCONNECT, 1);
        put(EVENT_ERROR, 1);
        put(EVENT_RECONNECT, 1);
        put(EVENT_RECONNECT_ATTEMPT, 1);
        put(EVENT_RECONNECT_FAILED, 1);
        put(EVENT_RECONNECT_ERROR, 1);
        put(EVENT_RECONNECTING, 1);
        put(EVENT_NETWORK_OFFLINE, 1);
        put(EVENT_NETWORK_ONLINE, 1);
    }};

    private volatile boolean connected;
    private int ids;
    private String nsp;
    private SocketManager io;
    private SparseArray<Ack> acks = new SparseArray<>();
    private Queue<On.Handle> subs;
    private final Queue<List<Object>> receiveBuffer = new LinkedList<>();
    private final Queue<Packet<JSONArray>> sendBuffer = new LinkedList<>();

    public Socket(SocketManager io, String nsp) {
        this.io = io;
        this.nsp = nsp;
    }

    private void subEvents() {
        if (this.subs != null){
            return;
        }

        this.subs = new LinkedList<On.Handle>() {{
            add(On.on(Socket.this.io, SocketManager.EVENT_OPEN, new Listener() {
                @Override
                public void call(Object... args) {
                    Socket.this.onOpen();
                }
            }));
            add(On.on(Socket.this.io, SocketManager.EVENT_PACKET, new Listener() {
                @Override
                public void call(Object... args) {
                    Socket.this.onPacket((Packet) args[0]);
                }
            }));
            add(On.on(Socket.this.io, SocketManager.EVENT_CLOSE, new Listener() {
                @Override
                public void call(Object... args) {
                    Socket.this.onClose(args.length > 0 ? (String) args[0] : null, args.length <= 1 || (boolean) args[1]);
                }
            }));
        }};
    }

    /**
     * Connects the ApolloSocket.
     */
    public Socket open() {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                if (Socket.this.connected) return;

                Socket.this.subEvents();
                Socket.this.io.open(); // ensure open
                if (SocketManager.ConnectionState.OPEN == Socket.this.io.connectionState) Socket.this.onOpen();
            }
        });
        return this;
    }

    /**
     * Connects the ApolloSocket.
     */
    public Socket connect() {
        return this.open();
    }

    /**
     * Force Manager to reconnect if connection is closed and there is an available network
     */
    public Socket reconnect(){
        this.io().forceReconnection();
        return this;
    }

    /**
     * Send messages.
     *
     * @param args data to send.
     * @return a reference to this object.
     */
    public Socket send(final Object... args) {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                Socket.this.emit(EVENT_MESSAGE, args);
            }
        });
        return this;
    }

    /**
     * Emits an event. When you pass {@link Ack} at the last argument, then the acknowledge is done.
     *
     * @param event an event name.
     * @param args data to send.
     * @return a reference to this object.
     */
    @Override
    public Emitter emit(final String event, final Object... args) {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                if (events.containsKey(event)) {
                    Socket.super.emit(event, args);
                    return;
                }

                List<Object> _args = new ArrayList<>(args.length + 1);
                _args.add(event);
                _args.addAll(Arrays.asList(args));

                JSONArray jsonArgs = new JSONArray();
                for (Object arg : _args) {
                    jsonArgs.put(arg);
                }
                int parserType = Parser.EVENT;
                if (HasBinary.hasBinary(jsonArgs)) { parserType = Parser.BINARY_EVENT; }
                Packet<JSONArray> packet = new Packet<>(parserType, jsonArgs);

                if (_args.get(_args.size() - 1) instanceof Ack) {
                    Log.v(TAG, "Emitting packet with ack id " + Socket.this.ids);

                    Socket.this.acks.put(Socket.this.ids, (Ack) _args.remove(_args.size() - 1));
                    jsonArgs = remove(jsonArgs, jsonArgs.length() - 1);
                    packet.data = jsonArgs;
                    packet.id = Socket.this.ids++;
                }

                if (Socket.this.connected) {
                    Socket.this.packet(packet);
                } else {
                    Socket.this.sendBuffer.add(packet);
                }
            }
        });
        return this;
    }

    private static JSONArray remove(JSONArray a, int pos) {
        JSONArray na = new JSONArray();
        for (int i = 0; i < a.length(); i++){
            if (i != pos) {
                Object v;
                try {
                    v = a.get(i);
                } catch (JSONException e) {
                    v = null;
                }
                na.put(v);
            }
        }
        return na;
    }

    /**
     * Emits an event with an acknowledge.
     *
     * @param event an event name
     * @param args data to send.
     * @param ack the acknowledgement to be called
     * @return a reference to this object.
     */
    public Emitter emit(final String event, final Object[] args, final Ack ack) {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                List<Object> _args = new ArrayList<Object>() {{
                    add(event);
                    if (args != null) {
                        addAll(Arrays.asList(args));
                    }
                }};
                Packet<JSONArray> packet = new Packet<>(Parser.EVENT, new JSONArray(_args));

                Log.v(TAG, "Emitting packet with ack id " + ids);

                Socket.this.acks.put(ids, ack);
                packet.id = ids++;

                Socket.this.packet(packet);
            }
        });
        return this;
    }

    private void packet(Packet<JSONArray> packet) {
        packet.nsp = this.nsp;
        this.io.packet(packet);
    }

    private void onOpen() {
        Log.d(TAG, "Transport is open - connecting");

        if (!"/".equals(this.nsp)) {
            this.packet(new Packet<JSONArray>(Parser.CONNECT));
        }
    }

    private void onClose(String reason, boolean isNetworkEnable) {
        Log.d(TAG, "Connection Closed, reason: " + reason + ". With a " + (isNetworkEnable? "enable" : "disable") + " network connection");

        this.connected = false;
        this.emit(EVENT_DISCONNECT, reason, isNetworkEnable);
    }

    private void onPacket(Packet<JSONArray> packet) {
        if (!this.nsp.equals(packet.nsp)) return;

        switch (packet.type) {
            case Parser.CONNECT:
                this.onConnect();
                break;

            case Parser.EVENT:
                this.onEvent(packet);
                break;

            case Parser.BINARY_EVENT:
                this.onEvent(packet);
                break;

            case Parser.ACK:
                this.onAck(packet);
                break;

            case Parser.BINARY_ACK:
                this.onAck(packet);
                break;

            case Parser.DISCONNECT:
                this.onDisconnect();
                break;

            case Parser.ERROR:
                this.emit(EVENT_ERROR, packet.data);
                break;
        }
    }

    private void onEvent(Packet<JSONArray> packet) {
        List<Object> args = new ArrayList<>(Arrays.asList(toArray(packet.data)));

        Log.v(TAG, "Emitting event" + args);

        if (packet.id >= 0) {
            Log.v(TAG, "Attaching ack callback to event");

            args.add(this.ack(packet.id));
        }

        if (this.connected) {
            String event = (String)args.remove(0);
            super.emit(event, args.toArray());
        } else {
            this.receiveBuffer.add(args);
        }
    }

    private Ack ack(final int id) {
        final boolean[] sent = new boolean[] {false};
        return new Ack() {
            @Override
            public void call(final Object... args) {
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        if (sent[0]) return;
                        sent[0] = true;
                        Log.v(TAG, "sending ack " + (args.length != 0 ? args : null));

                        int type = HasBinary.hasBinary(args) ? Parser.BINARY_ACK : Parser.ACK;
                        Packet<JSONArray> packet = new Packet<>(type, new JSONArray(Arrays.asList(args)));
                        packet.id = id;
                        Socket.this.packet(packet);
                    }
                });
            }
        };
    }

    private void onAck(Packet<JSONArray> packet) {
        Log.v(TAG, "calling ack " + packet.id + " with "+ packet.data);

        Ack fn = this.acks.get(packet.id);
        this.acks.remove(packet.id);
        fn.call(toArray(packet.data));
    }

    private void onConnect() {
        Log.d(TAG, "Successfully Connected");

        this.connected = true;
        this.emit(EVENT_CONNECT);
        this.emitBuffered();
    }

    private void emitBuffered() {
        List<Object> data;
        while ((data = this.receiveBuffer.poll()) != null) {
            String event = (String)data.get(0);
            super.emit(event, data.toArray());
        }
        this.receiveBuffer.clear();

        Packet<JSONArray> packet;
        while ((packet = this.sendBuffer.poll()) != null) {
            this.packet(packet);
        }
        this.sendBuffer.clear();
    }

    private void onDisconnect() {
        Log.d(TAG, "server disconnect " + this.nsp);

        this.destroy();
        this.onClose("io server disconnect", this.io.isNetworkEnable());
    }

    private void destroy() {
        if (this.subs != null) {
            // clean subscriptions to avoid reconnection
            for (On.Handle sub : this.subs) {
                sub.destroy();
            }
            this.subs = null;
        }

        this.io.destroy(this);
    }

    /**
     * Disconnects the ApolloSocket.
     *
     * @return a reference to this object.
     */
    public Socket close() {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                if (Socket.this.connected) {
                    Log.v(TAG, "performing disconnect (" + Socket.this.nsp + ")");

                    Socket.this.packet(new Packet<JSONArray>(Parser.DISCONNECT));
                }

                Socket.this.destroy();

                if (Socket.this.connected) {
                    Socket.this.onClose("io client disconnect", Socket.this.io.isNetworkEnable());
                }
            }
        });
        return this;
    }

    /**
     * Disconnects the ApolloSocket.
     *
     * @return a reference to this object.
     */
    public Socket disconnect() {
        return this.close();
    }

    public SocketManager io() {
        return io;
    }

    public boolean isConnected() {
        return connected;
    }

    private static Object[] toArray(JSONArray array) {
        int length = array.length();
        Object[] data = new Object[length];
        for (int i = 0; i < length; i++) {
            Object v;
            try {
                v = array.get(i);
            } catch (JSONException e) {
                v = null;
            }
            data[i] = v == JSONObject.NULL ? null : v;
        }
        return data;
    }
}
