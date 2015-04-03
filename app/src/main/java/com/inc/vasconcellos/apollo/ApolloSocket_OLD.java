package com.inc.vasconcellos.apollo;

import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Manager;
import com.github.nkzawa.socketio.client.Socket;

import java.net.URISyntaxException;
import java.util.HashMap;

public class ApolloSocket_OLD {

    //Logger Identifier String
    public static final String TAG = "ApolloSocket";

    //Internal Socket Management
    private Boolean disconnecting;
    private Boolean connecting;
    private Boolean reconnectionFailed;
    private Socket socket;
    private ConnectivityReceiver connectivityReceiver;
    private HashMap<String, ApolloEvent> events;
    private Integer reconnectionAttempts;

    public ApolloSocket_OLD(String url, String[] events) throws URISyntaxException{

        Log.d(TAG, "Instantiating ApolloSocket");

        //Initialize Variables
        reconnectionAttempts = 20;
        disconnecting = false;
        connecting = false;
        reconnectionFailed = false;

        try{
            //Socket Options
            IO.Options opts = new IO.Options();
            opts.forceNew = false;
            opts.reconnectionAttempts = reconnectionAttempts;

            //Initialize Socket from manager
            socket = IO.socket(url, opts);

        } catch (URISyntaxException e) {
            Log.e(TAG, "Error Parsing URL: " + url);
            throw e;
        }

        //Initialize Events
        this.events = new HashMap<String, ApolloEvent>() {{
            put(Socket.EVENT_CONNECT, null);
            put(Socket.EVENT_CONNECT_ERROR, null);
            put(Socket.EVENT_CONNECT_TIMEOUT, null);
            put(Socket.EVENT_DISCONNECT, null);
            put(Socket.EVENT_ERROR, null);
            put(Socket.EVENT_RECONNECT, null);
            put(Socket.EVENT_RECONNECT_ATTEMPT, null);
            put(Socket.EVENT_RECONNECT_FAILED, null);
            put(Socket.EVENT_RECONNECT_ERROR, null);
            put(Socket.EVENT_RECONNECTING, null);
        }};

        for (String event : events){
            this.events.put(event, new ApolloEvent(event));
        }

        //Connected Listener
        on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                ApolloSocket_OLD.this.connecting = false;
            }
        }, false);

        //Disconnect Listener
        on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                ApolloSocket_OLD.this.disconnecting = false;
            }
        }, false);

        //Reconnect Attempt Listener
        on(Socket.EVENT_RECONNECT_ATTEMPT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                ApolloSocket_OLD.this.reconnectionAttempts++;
            }
        }, false);

        //Reconnect Attempt Listener
        on(Socket.EVENT_RECONNECT_FAILED, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                ApolloSocket_OLD.this.reconnectionFailed = true;
                ApolloSocket_OLD.this.connecting = false;
            }
        }, false);

        //Initialize Connection Receiver
        connectivityReceiver = new ConnectivityReceiver(
                new Runnable() {
                    @Override
                    public void run() {
                        if(!ApolloSocket_OLD.this.isConnected() && !ApolloSocket_OLD.this.isConnecting()){
                            Log.d(ConnectivityReceiver.TAG, "Network Enable, Reconnecting Socket...");
                            ApolloSocket_OLD.this.socket.io().reconnectionAttempts(ApolloSocket_OLD.this.reconnectionAttempts);
                            ApolloSocket_OLD.this.socket.io().open(new Manager.OpenCallback() {
                                @Override
                                public void call(Exception err) {
                                    if (err == null) {
                                        ApolloSocket_OLD.this.connect();
                                    } else {
                                        Log.i(TAG, "Connection Failed, will attempt to reconnect");
                                    }
                                }
                            });
                        }
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        if(!ApolloSocket_OLD.this.isDisconnecting()){
                            ApolloSocket_OLD.this.disconnecting = true;

                            if(!ApolloSocket_OLD.this.reconnectionFailed){
                                ApolloSocket_OLD.this.on(Socket.EVENT_RECONNECT_FAILED, new Emitter.Listener() {
                                    @Override
                                    public void call(Object... args) {
                                        if(!ApolloSocket_OLD.this.isNetworkAvailable()){
                                            Log.d(ConnectivityReceiver.TAG, "Network Disable, Disconnecting Socket...");
                                            ApolloSocket_OLD.this.disconnect();
                                        }else{
                                            ApolloSocket_OLD.this.disconnecting = false;
                                        }
                                    }
                                }, true);
                                ApolloSocket_OLD.this.socket.io().reconnectionAttempts(1);

                            }else if(!ApolloSocket_OLD.this.isConnecting()){
                                Log.d(ConnectivityReceiver.TAG, "Network Disable, Disconnecting Socket...");
                                ApolloSocket_OLD.this.disconnect();
                            }
                        }
                    }
                }
        );

        //Register Connection Receiver to CONNECTIVITY_CHANGE Event
        App.instance().registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    //Public Methods
    public void connect(){
        if(isNetworkAvailable()){
            connecting = true;
            socket.connect();
        }
    }

    public void disconnect(){
        disconnecting = true;
        socket.disconnect();
    }

    public Boolean isNetworkAvailable(){ return connectivityReceiver.isNetworkAvailable(App.instance()); }

    public Boolean emit(String eventName, Object... args){
        if(this.isConnected() && this.events.containsKey(eventName)){
            ApolloEvent event = this.events.get(eventName);

            if(event != null && !event.isBusy()){
                Log.d(TAG, "Emitted custom event: " + eventName);

                String requestName = "request" + eventName.substring(0,1).toUpperCase() + eventName.substring(1);

                event.setBusy();
                socket.emit(requestName, args);
            }

            return true;
        }

        return false;
    }

    public Boolean on(final String eventName, Emitter.Listener fn, Boolean once){
        if(this.events.containsKey(eventName)){
            ApolloEvent event = this.events.get(eventName);

            Log.d(TAG, "Added custom listener to: " + eventName);

            if(event != null && event.canBeBusy()){
                socket.off(eventName, event.controlFlow());
            }

            if(!once){
                socket.on(eventName, fn);
            }else{
                socket.once(eventName, fn);
            }

            if(event != null && event.canBeBusy()){
                socket.on(eventName, event.controlFlow());
            }

            return true;
        }

        return false;
    }
    
    public Boolean off(String eventName, Emitter.Listener fn){
        if(events.containsKey(eventName)){
            Log.d(TAG, "Removed custom listener from: " + eventName);

            socket.off(eventName, fn);

            return true;
        }

        return false;
    }

    public Boolean isConnected() {
        return socket.connected();
    }

    public Boolean isConnecting() {
        return connecting;
    }

    public Boolean isDisconnecting() {
        return disconnecting;
    }
}
