package com.inc.vasconcellos.apollo;

import android.app.Application;
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
            FileOutputStream fos;

            try {
                String apolloInstanceJSON = Apollo.getInstance().deleteInstance().toString();

                fos = this.openFileOutput(Apollo.FILE_NAME, MODE_PRIVATE);
                OutputStreamWriter osw = new OutputStreamWriter (fos) ;
                osw.write (apolloInstanceJSON) ;
                osw.flush ( ) ;
                osw.close ( ) ;

                Log.d(TAG, "Apollo Instance deleted to save memory");

            }catch (JSONException e){
                Log.e(TAG, "Error while generating JSON from Apollo instance", e);

            }catch (FileNotFoundException e) {
                Log.e(TAG, "Error while opening file: " + Apollo.FILE_NAME, e);

            } catch (IOException e){
                Log.e(TAG, "Error while saving JSON of Apollo", e);
            }
        }
    }
}
