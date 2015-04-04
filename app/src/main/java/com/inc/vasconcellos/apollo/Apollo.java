package com.inc.vasconcellos.apollo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.net.URISyntaxException;

public class Apollo{

    //Singleton
    private static Apollo instance;

    //Log Tag
    public static final String TAG = Apollo.class.getSimpleName();

    //FileName to save instance
    public static final String PREFS_NAME = "ApolloInstance";
    public static final String PREFS_JSON = "ApolloJSON";

    //Json name fields
    public static final String JSON_USERNAME = "username";
    public static final String JSON_PASSWORD = "password";
    public static final String JSON_LOGGED = "logged";

    //Constant
    private static final String SERVER_ADDRESS = "http://104.236.0.59:1969";
    private static final String LOGIN = "login";
    private static final String INTIMATION = "intimation";
    private static final String PROCESS_PIECE = "processPiece";
    private static final String PROCESS_INFO = "processInfo";
    private static final int EMIT = 0;
    private static final int ON = 1;
    private static final int OFF = 2;
    private static final int ONCE = 3;
    private static final int BUSY = 4;

    //Internal
    private ApolloSocket socket;
    private Boolean logged;
    private SocketAbstraction on;
    private SocketAbstraction emit;
    private SocketAbstraction off;
    private SocketAbstraction once;
    private SocketAbstraction busy;
    private String username;
    private String password;

    /**
     * Socket Abstraction
     * - This is something that I came up with to *try* facilitate the events access
     */
    public class SocketAbstraction {
        private int type;

        SocketAbstraction(int type){
            this.type = type;
        }

        //Private Methods
        private boolean internalManager(String event, Object... args){
            switch (type){
                case EMIT:
                    return socket.emit(event, args);
                case ON:
                    if(args[0] instanceof Emitter.Listener){
                        return socket.on(event, (Emitter.Listener) args[0], false);
                    }
                    break;
                case OFF:
                    if(args[0] instanceof Emitter.Listener){
                        return socket.off(event, (Emitter.Listener) args[0]);
                    }
                    break;
                case ONCE:
                    if(args[0] instanceof Emitter.Listener){
                        return socket.on(event, (Emitter.Listener) args[0], true);
                    }
                    break;
                case BUSY:
                    return socket.isEventBusy(event);
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

        public boolean networkOffline(Object... args){
            return internalManager(Socket.EVENT_NETWORK_OFFLINE, args);
        }

        public boolean networkOnline(Object... args){
            return internalManager(Socket.EVENT_NETWORK_ONLINE, args);
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

    private Apollo() {
        //Initialize Variables
        logged = false;
        on = new SocketAbstraction(ON);
        emit = new SocketAbstraction(EMIT);
        off = new SocketAbstraction(OFF);
        once = new SocketAbstraction(ONCE);
        busy = new SocketAbstraction(BUSY);

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

                if(!logged){
                    Apollo.this.username = null;
                    Apollo.this.password = null;
                }
            }
        });
    }

    private void retriveInstance(Context context){
        try {
            SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
            String apolloJSONString = settings.getString(PREFS_JSON, "");

            JSONObject apolloJson = new JSONObject(apolloJSONString);

            String username = apolloJson.getString(JSON_USERNAME);
            String password = apolloJson.getString(JSON_PASSWORD);
            boolean logged = apolloJson.getBoolean(JSON_LOGGED);

            if(logged){
                this.username = username;
                this.password = password;

                this.login(username, password);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error while parsing Apollo JSON", e);
        }
    }

    private JSONObject saveInstance() throws JSONException{
        JSONObject apollo = new JSONObject();

        apollo.put(JSON_USERNAME, this.username);
        apollo.put(JSON_PASSWORD, this.password);
        apollo.put(JSON_LOGGED, this.logged);

        return apollo;
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

    /**
     * WARNING: internal use only
     * This will delete the static reference to apollo and return
     * a JSON object with all the inner class sensitive information
     * to be saved and retrieved later.
     *
     * @return = JSON representation of all the inner class sensitive information
     */
    public JSONObject deleteInstance() throws JSONException{
        JSONObject apollo;

        try{
            apollo = this.saveInstance();

        } finally {
            Apollo.instance = null;
        }

        return apollo;
    }

    public Boolean isLogged() {
        return logged;
    }

    public Boolean isConnected() { return socket.isConnected(); }

    public Boolean isNetworkAvailable() { return socket.isNetworkAvailable(); }

    public void connect() {
        if(!socket.isConnected()){
            socket.connect();
        }
    }

    public SocketAbstraction on() {
        return on;
    }

    public SocketAbstraction off() {
        return off;
    }

    public SocketAbstraction once() {
        return once;
    }

    public SocketAbstraction busy(){
        return busy;
    }

    public void login (String username, String password){
        if(!this.logged){
            this.username = username;
            this.password = password;

            this.emit.login(username, password);
        }
    }
}