package com.inc.vasconcellos.apollo;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.Tag;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Socket;

import java.net.URISyntaxException;

public class Apollo{

    //Singleton
    private static Apollo instance;

    //Log Tag
    private static final String TAG = "Apollo";

    //Constant
    private static final String SERVER_ADDRESS = "http://104.236.0.59:1969";
    private static final String LOGIN = "login";
    private static final String INTIMATION = "intimation";
    private static final String PROCESS_PIECE = "processPiece";
    private static final String PROCESS_INFO = "processInfo";
    private static final Integer EMIT = 0;
    private static final Integer ON = 1;
    private static final Integer OFF = 2;
    private static final Integer ONCE = 3;

    //Internal
    private ApolloSocket socket;
    private Boolean logged;
    private SocketAbstraction on;
    private SocketAbstraction emit;
    private SocketAbstraction off;
    private SocketAbstraction once;

    /**
     * Socket Abstraction
     * - This is something that I came up with to *try* emulate magic methods on java
     */
    public class SocketAbstraction {
        private Integer type;

        SocketAbstraction(Integer type){
            this.type = type;
        }

        //Private Methods
        private boolean internalManager(String event, Object... args){
            if(type.equals(EMIT)){
                return socket.emit(event, args);
            }else if(type.equals(ON) && args[0] instanceof Emitter.Listener){
                return socket.on(event, (Emitter.Listener) args[0], false);
            }else if(type.equals(OFF) && args[0] instanceof Emitter.Listener){
                return socket.off(event, (Emitter.Listener) args[0]);
            }else if(type.equals(ONCE) && args[0] instanceof Emitter.Listener){
                return socket.on(event, (Emitter.Listener) args[0], true);
            }

            return false;
        }

        //Public Methods
        public boolean connect(Object... args){
            return internalManager(Socket.EVENT_CONNECT, args);
        }

        public boolean connectError(Object... args){
            return internalManager(Socket.EVENT_CONNECT_ERROR, args);
        }

        public boolean timeout(Object... args){
            return internalManager(Socket.EVENT_CONNECT_TIMEOUT, args);
        }

        public boolean disconnect(Object... args){
            return internalManager(Socket.EVENT_DISCONNECT, args);
        }

        public boolean error(Object... args){
            return internalManager(Socket.EVENT_ERROR, args);
        }

        public boolean reconnect(Object... args){
            return internalManager(Socket.EVENT_RECONNECT, args);
        }

        public boolean reconnectAttempt(Object... args){
            return internalManager(Socket.EVENT_RECONNECT_ATTEMPT, args);
        }

        public boolean reconnectFailed(Object... args){
            return internalManager(Socket.EVENT_RECONNECT_FAILED, args);
        }

        public boolean reconnectError(Object... args){
            return internalManager(Socket.EVENT_RECONNECT_ERROR, args);
        }

        public boolean reconnecting(Object... args){
            return internalManager(Socket.EVENT_RECONNECTING, args);
        }

        public boolean login(Object... args){
            return internalManager(LOGIN, args);
        }

        public boolean intimation(Object... args){
            return internalManager(INTIMATION, args);
        }

        public boolean processPiece(Object... args){
            return internalManager(PROCESS_PIECE, args);
        }

        public boolean processInfo(Object... args){
            return internalManager(PROCESS_INFO, args);
        }
    }

    public Apollo() {
        //Initialize Variables
        logged = false;
        on = new SocketAbstraction(ON);
        emit = new SocketAbstraction(EMIT);
        off = new SocketAbstraction(OFF);
        once = new SocketAbstraction(ONCE);

        //Initialize ApolloSocket
        try{
            socket = new ApolloSocket(SERVER_ADDRESS, new String[] {LOGIN, PROCESS_INFO, PROCESS_PIECE, PROCESS_INFO});
        }catch (URISyntaxException e){
            //Should Never Happen, But in case it happens we have nothing to do otherwise exit;//TODO: Display a error Message to User Than Exit
            e.printStackTrace();
            System.exit(1);
        }

        //Login Listener
        this.on.login(new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                logged = args[1] != null && args[1] instanceof Boolean ? (Boolean) args[1] : false;
            }
        });
    }

    public static Apollo getInstance(){
        //Return Singleton if already instantiated
        if(Apollo.instance != null){
            return Apollo.instance;
        }

        //Initialize Singleton
        instance = new Apollo();

        return instance;
    }

    public Boolean isLogged() {
        return logged;
    }

    public Boolean isConnected() { return socket.isConnected(); }

    public Boolean isNetworkAvailable() { return socket.isNetworkAvailable(); }

    public void connect() { socket.connect(); }

    public SocketAbstraction on() {
        return on;
    }

    public SocketAbstraction emit() {
        return emit;
    }

    public SocketAbstraction off() {
        return off;
    }

    public SocketAbstraction once() {
        return once;
    }
}
