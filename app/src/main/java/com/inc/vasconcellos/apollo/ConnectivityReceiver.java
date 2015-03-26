package com.inc.vasconcellos.apollo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.util.concurrent.ConcurrentLinkedQueue;

public class ConnectivityReceiver extends BroadcastReceiver {
    private Runnable onConnected;
    private Runnable onDisconnected;

    public ConnectivityReceiver(Runnable onConnected, Runnable onDisconnected){
        super();
        this.onConnected = onConnected;
        this.onDisconnected = onDisconnected;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(ConnectivityReceiver.class.getSimpleName(), "action: " + intent.getAction());
        if(isNetworkAvailable(context) && onConnected != null){
            onConnected.run();
        }else if(onDisconnected != null){
            onDisconnected.run();
        }
    }

    public boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService((Context.CONNECTIVITY_SERVICE));
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}