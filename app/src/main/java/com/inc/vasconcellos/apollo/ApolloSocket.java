package com.inc.vasconcellos.apollo;

import android.content.IntentFilter;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import java.net.URISyntaxException;
import java.util.HashMap;

public class ApolloSocket {

    //Logger Identifier String
    public static final String TAG = "ApolloSocket";

    //Internal Socket Management
    private Socket socket;
    private ConnectivityReceiver connectivityReceiver;
    private HashMap<String, ApolloEvent> events;

    public ApolloSocket(String url, String[] events) throws URISyntaxException{

        //Initialize Connection Receiver
        connectivityReceiver = new ConnectivityReceiver(
                new Runnable() {
                    @Override
                    public void run() {
                        Log.i(ApolloSocket.TAG, "Network Enable, Reconnecting Socket...");

                        if(!ApolloSocket.this.socket.connected()){
                            ApolloSocket.this.socket.connect();
                        }
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Log.i(ApolloSocket.TAG, "Network Disable, Disconnecting Socket.");

                        ApolloSocket.this.socket.disconnect();
                    }
                }
        );
        //Register Connection Receiver to CONNECTIVITY_CHANGE Event
        App.instance().registerReceiver(connectivityReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));

        try{
            socket = IO.socket(url);
            //Max Reconnection attempts
            socket.io().reconnectionAttempts(20);

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
    }

    //Public Methods
    public void connect(){
        if(isNetworkAvailable()){
            socket.connect();
        }
    }

    public void disconnect(){
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
}
