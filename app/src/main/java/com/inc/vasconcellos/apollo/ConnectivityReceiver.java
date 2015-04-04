package com.inc.vasconcellos.apollo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;


public class ConnectivityReceiver extends BroadcastReceiver {
    public static final String TAG = ConnectivityReceiver.class.getSimpleName();

    private Runnable onConnected;
    private Runnable onDisconnected;
    private Boolean busy;
    private Boolean registered;
    private String intentAction;
    private Context context;

    //Staic Methods
    /**
     * Verifies if there is an valid connection in the given Context
     *
     * @param context = The Context in which the network connection will be verified
     * @return = return True if it's connected
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService((Context.CONNECTIVITY_SERVICE));
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    /**
     * Constructor
     *
     * @param onConnected = Runnable that will run when receive a connected event
     * @param onDisconnected = Runnable that will run when receive a disconnected event
     */
    public ConnectivityReceiver(Runnable onConnected, Runnable onDisconnected){
        super();
        this.onConnected = onConnected;
        this.onDisconnected = onDisconnected;
        this.busy = false;
        this.registered = false;
        this.intentAction = ConnectivityManager.CONNECTIVITY_ACTION;
        this.context = null;
    }

    //Private Methods
    private void debugIntent(Intent intent, String tag) {
        Log.v(tag, "action: " + intent.getAction());
        Log.v(tag, "component: " + intent.getComponent());
        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String key: extras.keySet()) {
                Log.v(tag, "key [" + key + "]: " +
                        extras.get(key));
            }
        }
        else {
            Log.v(tag, "no extras");
        }
    }

    //Public Methods
    @Override
    public void onReceive(Context context, Intent intent) {
        debugIntent(intent, TAG);

        if(!busy){
            busy = true;

            if(isNetworkAvailable(context) && onConnected != null){
                onConnected.run();
            }else if(onDisconnected != null){
                onDisconnected.run();
            }

            busy = false;
        }
    }

    public Boolean isRegistered() {
        return registered && context != null;
    }

    public void registerReciver(Context context) {
        if(!this.isRegistered()){
            context.registerReceiver(this, new IntentFilter(this.intentAction));
            this.registered = true;
            this.context = context;

            Log.d(TAG, "Registered to Intent Action: " + this.intentAction);
        }
    }

    public void unregisterReceiver(){
        if(this.isRegistered()){
            this.context.unregisterReceiver(this);
            this.registered = false;
            this.context = null;
            Log.d(TAG, "Unregistered from Intent Action: " + this.intentAction);
        }
    }
}
