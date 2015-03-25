package com.inc.vasconcellos.apollo;

import android.util.Log;

import com.github.nkzawa.emitter.Emitter;

public class ApolloEvent {
    private static final String TAG = "ApolloListener";

    private String name;
    private Emitter.Listener controlFlow;
    private Boolean busy;

    public ApolloEvent(String name){
        this.name = name;
        this.busy = false;
        this.controlFlow =  new Emitter.Listener(){
            @Override
            public void call(Object... args) {
                Log.d(TAG, "Received custom event: " + ApolloEvent.this.name);
                ApolloEvent.this.busy = false;
            }
        };
    }

    public String getName() {
        return name;
    }

    public Emitter.Listener controlFlow() {
        return controlFlow;
    }

    public Boolean isBusy() {
        return busy;
    }

    public void setBusy(){
        busy = true;
    }
}
