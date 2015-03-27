package com.inc.vasconcellos.apollo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;


public class ConnectivityReceiver extends BroadcastReceiver {
    public static final String TAG = ConnectivityReceiver.class.getSimpleName();

    private Runnable onConnected;
    private Runnable onDisconnected;
    private Boolean busy;

    public ConnectivityReceiver(Runnable onConnected, Runnable onDisconnected){
        super();
        this.onConnected = onConnected;
        this.onDisconnected = onDisconnected;
        this.busy = false;
    }

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
        }

        busy = false;
    }

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

    public boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService((Context.CONNECTIVITY_SERVICE));
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}
