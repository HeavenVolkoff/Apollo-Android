package com.inc.vasconcellos.apollo;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;

public class App extends Application {

    public static final String TAG = App.class.getSimpleName();

    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static App instance() { return instance; }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        //We are going to be killed, save serialized instance of Apollo
        if(level == TRIM_MEMORY_COMPLETE){

            try {
                String apolloInstanceJSON = Apollo.getInstance().deleteInstance().toString();

                SharedPreferences settings = getSharedPreferences(Apollo.PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();

                editor.putString(Apollo.PREFS_JSON, apolloInstanceJSON);

                editor.commit();

                Log.d(TAG, "Apollo Instance deleted to save memory");

            }catch (JSONException e) {
                Log.e(TAG, "Error while generating JSON from Apollo instance", e);

            }
        }
    }
}
