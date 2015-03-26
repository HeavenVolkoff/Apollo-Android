package com.inc.vasconcellos.apollo;

import android.app.Application;

public class App extends Application {
    private static App instance;
    private Apollo apollo;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        apollo = Apollo.getInstance();
    }

    public static App instance() { return instance; }

    public  Apollo getApollo() { return apollo; }
}
