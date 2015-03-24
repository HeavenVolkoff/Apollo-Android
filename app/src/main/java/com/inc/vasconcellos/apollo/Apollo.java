package com.inc.vasconcellos.apollo;

import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import java.net.URISyntaxException;
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
    private static final Integer OFF = 2;
    private static final Integer ONCE = 3;
    private static final String LOGIN = "login";
    private static final String INTIMATION = "intimation";
    private static final String PROCESS_PIECE = "processPiece";
    private static final String PROCESS_INFO = "processInfo";
    private static final HashMap<String, Boolean> LISTENERS = new HashMap<>(4);

    //Internal
    private Socket socket;
    private Boolean logged;
    private ApolloSocket on;
    private ApolloSocket emit;
    private ApolloSocket off;
    private ApolloSocket once;

    //Socket Abstraction
    public class ApolloSocket {
        private Integer type;

        ApolloSocket(Integer type){
            this.type = type;
        }

        //Private Methods
        private boolean internalManager(String event, Object... args){
            if(type.equals(EMIT)){
                return emit(event, args);
            }else if(type.equals(ON) && args[0] instanceof Emitter.Listener){
                return on(event, (Emitter.Listener) args[0], false);
            }else if(type.equals(OFF) && args[0] instanceof Emitter.Listener){
                return off(event, (Emitter.Listener) args[0]);
            }else if(type.equals(ONCE) && args[0] instanceof Emitter.Listener){
                return on(event, (Emitter.Listener) args[0], true);
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
        off = new ApolloSocket(OFF);
        once = new ApolloSocket(ONCE);

        //Initialize Listeners HashMap
        LISTENERS.put(LOGIN, false);
        LISTENERS.put(INTIMATION, false);
        LISTENERS.put(PROCESS_INFO, false);
        LISTENERS.put(PROCESS_PIECE, false);

        //Initialize Socket
        try{
            socket = IO.socket(SERVER_ADDRESS);

            //Login Listener
            this.on(LOGIN, new Emitter.Listener() {
                @Override
                public void call(final Object... args) {
                    logged = args[1] != null && args[1] instanceof Boolean ? (Boolean) args[1] : false;
                }
            }, false);

            //Connect to Server
            socket.connect();
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

        return instance;
    }

    private boolean emit(String event, Object... args){
        if(LISTENERS.containsKey(event) && !LISTENERS.get(event) && isConnected()){
            Log.d(TAG, "Emitted custom event: " + event);

            String eventName = "request" + event.substring(0,1).toUpperCase() + event.substring(1);

            LISTENERS.put(event, true);
            socket.emit(eventName, args);

            return true;
        }

        return false;
    }

    public ApolloSocket emit(){
        return this.emit;
    }

    private boolean on(final String event, Emitter.Listener fn, Boolean once){
        if(LISTENERS.containsKey(event)){
            Log.d(TAG, "Added custom listener to: " + event);

            Emitter.Listener controlFlow = new Emitter.Listener(){

                @Override
                public void call(Object... args) {
                    Log.d(TAG, "Received custom event: " + event);
                    LISTENERS.put(event, false);
                }
            };

            socket.off(event, controlFlow);
            if(!once){
                socket.on(event, fn);
            }else{
                socket.once(event, fn);
            }
            socket.on(event, controlFlow);

            return true;
        }

        return false;
    }

    public ApolloSocket on(){
        return this.on;
    }

    public ApolloSocket once() { return this.once; }

    private boolean off(String event, Emitter.Listener fn){
        if(LISTENERS.containsKey(event)){
            Log.d(TAG, "Removed custom listener from: " + event);

            socket.off(event, fn);

            return true;
        }

        return false;
    }

    public ApolloSocket off(){ return this.off; }

    public Boolean isConnected() {
        return socket.connected();
    }

    public Boolean isLogged() {
        return logged;
    }
}
