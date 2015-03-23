package com.inc.vasconcellos.apollo;

import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;

public class Apollo{

    //Singleton
    private static Apollo instance;

    //Log Tag
    private static final String TAG = "Apollo";

    //Constant
    private static final String SERVER_ADDRESS = "http://104.236.0.59:1969";

    //Constants
    private static final Integer EMIT = 0;
    private static final Integer ON = 1;
    private static final String LOGIN = "login";
    private static final String INTIMATION = "intimation";
    private static final String PROCESS_PIECE = "processPiece";
    private static final String PROCESS_INFO = "processInfo";
    private static final HashMap<String, Boolean> LISTENERS = new HashMap<>(4);

    //Internal
    private Socket _socket;
    private Boolean logged;
    private ApolloSocket on;
    private ApolloSocket emit;

    //Socket Abstraction
    public class ApolloSocket {
        private Integer type;

        ApolloSocket(Integer type){
            this.type = type;
        }

        //Private Methods
        private boolean internalManager(String event, Object... args){
            if(type.equals(EMIT)){
                return _emit(event, args);

            }else if(type.equals(ON) && args[0] instanceof Emitter.Listener){
                return _on(event, (Emitter.Listener) args[0]);
            }

            return false;
        }

        //Public Methods
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
        on = new ApolloSocket(ON);
        emit = new ApolloSocket(EMIT);

        //Initialize Listeners HashMap
        LISTENERS.put(LOGIN, false);
        LISTENERS.put(INTIMATION, false);
        LISTENERS.put(PROCESS_INFO, false);
        LISTENERS.put(PROCESS_PIECE, false);

        //Initialize Socket
        try{
            _socket = IO.socket(SERVER_ADDRESS);

            //Connect to Server
            _socket.connect();
            Log.d(TAG, "Connected to Server");

        } catch (URISyntaxException e) {
            //Should Never Happen, But in case it happens we have nothing to do otherwise exit;
            Log.e(TAG, "Error Parsing URL: " + SERVER_ADDRESS + ", error: " + e.getMessage());
            System.exit(1);
        }
    }

    public static Apollo getInstance(){
        //Return Singleton if already instantiated
        if(Apollo.instance != null){
            return Apollo.instance;
        }

        //Initialize Singleton
        instance = new Apollo();

        //Login Listener
        instance.on().login(new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                instance.logged = args[1] != null && args[1] instanceof Boolean ? (Boolean) args[1] : false;
            }
        });

        return instance;
    }

    private boolean _on(final String event, Emitter.Listener fn){
        if(LISTENERS.containsKey(event)){
            Emitter.Listener controlFlow = new Emitter.Listener(){

                @Override
                public void call(Object... args) {
                    LISTENERS.put(event, false);
                }
            };

            _socket.off(event, controlFlow);
            _socket.on(event, fn);
            _socket.on(event, controlFlow);

            return true;
        }

        return false;
    }

    private boolean _emit(String event, Object... args){
        if(LISTENERS.containsKey(event) && LISTENERS.get(event) && isConnected()){
            Log.d(TAG, "Emitted custom event: " + event + " with: " + Arrays.toString(args));

            String eventName = "request" + event.substring(0,1).toUpperCase() + event.substring(1);

            LISTENERS.put(event, true);
            _socket.emit(eventName, args);

            return true;
        }

        return false;
    }

    public ApolloSocket emit(){
        return this.emit;
    }

    public ApolloSocket on(){
        return this.on;
    }

    public Boolean isConnected() {
        return _socket.connected();
    }

    public Boolean isLogged() {
        return logged;
    }
}
