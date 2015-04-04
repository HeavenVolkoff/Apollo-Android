package com.inc.vasconcellos.apollo;


import android.util.Log;

import com.github.nkzawa.emitter.Emitter;

import java.net.URISyntaxException;
import java.util.HashMap;

public class ApolloSocket {

    //Logger Identifier String
    public static final String TAG = ApolloSocket.class.getSimpleName();

    //Internal Socket Management
    private Socket socket;
    private HashMap<String, ApolloEvent> events;

    public ApolloSocket(String url, String[] events) throws URISyntaxException{

        Log.d(TAG, "Instantiating ApolloSocket");

        //Initialize Variables
        int reconnectionAttempts = 20;

        try{
            //Socket Options
            IO.Options opts = new IO.Options();
            opts.forceNew = false;
            opts.reconnectionAttempts = reconnectionAttempts;

            //Initialize Socket from manager
            socket = IO.socket(App.instance(), url, opts);

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
            put(Socket.EVENT_NETWORK_OFFLINE, null);
            put(Socket.EVENT_NETWORK_ONLINE, null);
        }};

        for (String event : events){
            this.events.put(event, new ApolloEvent(event));
        }
    }

    //Public Methods
    public void connect(){
        socket.connect();
    }

    public void reconnect(){
        socket.reconnect();
    }

    public void disconnect(){
        socket.disconnect();
    }

    public Boolean isNetworkAvailable(){ return socket.io().isNetworkEnable(); }

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
        return socket.isConnected();
    }

    public Boolean isEventBusy(String eventName){
        if(this.events.containsKey(eventName)){
            ApolloEvent event = this.events.get(eventName);

            if(event != null){
                return event.isBusy();
            }
        }

        return false;
    }
}
