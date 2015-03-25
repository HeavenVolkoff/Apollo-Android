package com.inc.vasconcellos.apollo;


import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import java.net.URISyntaxException;
import java.util.HashMap;

public class ApolloSocket {

    //Logger Identifier String
    private static final String TAG = "ApolloSocket";

    //Internal Socket Management
    private Socket socket;
    private HashMap<String, ApolloEvent> events;

    public ApolloSocket(String url, String[] events) throws URISyntaxException{
        //Initialize Socket
        try{
            socket = IO.socket(url);

        } catch (URISyntaxException e) {
            Log.e(TAG, "Error Parsing URL: " + url + ", error: " + e.getMessage());
            throw e;
        }

        //Initialize Events
        this.events = new HashMap<>();
        for (String event : events){
            this.events.put(event, new ApolloEvent(event));
        }
    }

    //Public Methods
    public void connect(){
        socket.connect();
    }

    public boolean emit(String eventName, Object... args){
        if(this.events.containsKey(eventName) && this.isConnected()){
            ApolloEvent event = this.events.get(eventName);

            if(!event.isBusy()){
                Log.d(TAG, "Emitted custom event: " + eventName);

                String requestName = "request" + eventName.substring(0,1).toUpperCase() + eventName.substring(1);

                event.setBusy();
                socket.emit(requestName, args);
            }

            return true;
        }

        return false;
    }

    public boolean on(final String eventName, Emitter.Listener fn, Boolean once){
        if(this.events.containsKey(eventName)){
            ApolloEvent event = this.events.get(eventName);

            Log.d(TAG, "Added custom listener to: " + eventName);

            socket.off(eventName, event.controlFlow());
            if(!once){
                socket.on(eventName, fn);
            }else{
                socket.once(eventName, fn);
            }
            socket.on(eventName, event.controlFlow());

            return true;
        }

        return false;
    }
    
    public boolean off(String eventName, Emitter.Listener fn){
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
