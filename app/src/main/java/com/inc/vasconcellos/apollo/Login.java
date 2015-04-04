package com.inc.vasconcellos.apollo;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.github.nkzawa.emitter.Emitter;
import com.nispok.snackbar.Snackbar;
import com.nispok.snackbar.SnackbarManager;
import com.nispok.snackbar.enums.SnackbarType;
import com.nispok.snackbar.listeners.ActionClickListener;
import com.nispok.snackbar.listeners.EventListener;
import com.nispok.snackbar.listeners.EventListenerAdapter;
import com.pnikosis.materialishprogress.ProgressWheel;



public class Login extends Activity{

    public static final String TAG = Login.class.getSimpleName();

    private Button loginButton;
    private EditText username;
    private EditText password;
    private ProgressWheel progressWheel;

    private String currentSnackbar;
    
    private Apollo apollo;

    //Listeners
    private Emitter.Listener loginListener;
    private Emitter.Listener connectListener;
    private Emitter.Listener reconnectingListener;
    private Emitter.Listener reconnectFailedListener;
    private Emitter.Listener networkOfflineListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        //Initialize Variables
        currentSnackbar = null;

        //Initialize self reference
        apollo = Apollo.getInstance();

        //Initialize Logo ImageView and set logoDrawable to it
        ImageView logo = (ImageView) findViewById(R.id.logo);
        logo.setImageDrawable(new logoDrawable());

        //Initialize Button and set it's listener to this
        loginButton = (Button) findViewById(R.id.loginButton);
        loginButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(apollo.isConnected() && !apollo.isLogged()){
                            String username = Login.this.username.getText().toString();
                            String password = Login.this.password.getText().toString();

                            if (username.length() > 0 && password.length() > 0) {
                                Login.this.username.setVisibility(View.GONE);
                                Login.this.password.setVisibility(View.GONE);
                                Login.this.loginButton.setVisibility(View.GONE);
                                Login.this.progressWheel.setVisibility(View.VISIBLE);

                                apollo.login(username, password);
                                Log.d(TAG, "Emitted Login Event to Server");
                            }else{
                                showSnackbar("failedLogin");
                            }
                        }
                    }
                });
            }
        });

        //Initialize Form fields
        username = (EditText) findViewById(R.id.loginUsername);
        password = (EditText) findViewById(R.id.loginPassword);

        //Initialize Spinner Bar
        progressWheel = (ProgressWheel) findViewById(R.id.progressWheel);

        //Login Listener
        loginListener = new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Boolean loggedIn = args[1] instanceof Boolean? (Boolean) args[1] : false;
                        onLoginReceived(loggedIn);
                    }
                });
            }
        };

        //Connect Listener
        connectListener = new Emitter.Listener(){
            @Override
            public void call(Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        SnackbarManager.dismiss();
                    }
                });
            }
        };

        //Reconnect Listener
        reconnectingListener = new Emitter.Listener(){
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showSnackbar("reconnecting");
                    }
                });
            }
        };

        //Connection Error Listener
        reconnectFailedListener = new Emitter.Listener(){
            @Override
            public void call(Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showSnackbar("reconnectingFailed");
                    }
                });
            }
        };

        //Network Offline Listener
        networkOfflineListener = new Emitter.Listener(){
            @Override
            public void call(Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showSnackbar("networkOffline");
                    }
                });
            }
        };

        //Add Listener to the Event queue
        apollo.on().login(loginListener);
        apollo.on().connect(connectListener);
        apollo.on().reconnecting(reconnectingListener);
        apollo.on().reconnectFailed(reconnectFailedListener);
        apollo.on().networkOffline(networkOfflineListener);

        //Connect
        apollo.connect();

        //Restore Activity state if restarted by system
        if(savedInstanceState != null){
            restorePreviousState(
                    apollo.isLogged(),
                    apollo.busy().login(),
                    savedInstanceState.getString("currentSnackbar")
            );
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_login, menu);
        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        savedInstanceState.putString("currentSnackbar", this.currentSnackbar);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        apollo.off().login(loginListener);
        apollo.off().connect(connectListener);
        apollo.off().reconnecting(reconnectingListener);
        apollo.off().reconnectFailed(reconnectFailedListener);
        apollo.off().networkOffline(networkOfflineListener);
    }

    private void onLoginReceived(boolean loggedIn){
        //Dismiss Progress Wheel
        progressWheel.setVisibility(View.GONE);

        if (loggedIn) {
                Log.d(TAG, "Logged In");
        }else{
            Log.w(TAG, "Login Failed");

            username.setVisibility(View.VISIBLE);
            password.setVisibility(View.VISIBLE);
            loginButton.setVisibility(View.VISIBLE);

            showSnackbar("failedLogin");
        }
    }

    private void restorePreviousState(Boolean logged, Boolean isLoggingIn, @Nullable String snackbar){
        if(logged || isLoggingIn){
            username.setVisibility(View.GONE);
            password.setVisibility(View.GONE);
            loginButton.setVisibility(View.GONE);

            if(isLoggingIn){
                this.progressWheel.setVisibility(View.VISIBLE);
            }
        }

        if(snackbar != null){
            Log.d(TAG, "restoring snackbar, key: " + snackbar);
            showSnackbar(snackbar);
        }
    }

    private void showSnackbar(@NonNull String key){
        if(key.equals(currentSnackbar)){
            return;
        }

        Snackbar snack = null;
        EventListener dismiss = new EventListenerAdapter() {
            @Override
            public void onDismiss(Snackbar snackbar) {
                super.onDismiss(snackbar);
                Login.this.currentSnackbar = null;
            }
        };

        switch (key){
            case "failedLogin":
                snack = Snackbar.with(getApplicationContext())
                        .type(SnackbarType.MULTI_LINE)
                        .color(getResources().getColor(R.color.material_red_700))
                        .text(R.string.failedLogin)
                        .duration(Snackbar.SnackbarDuration.LENGTH_INDEFINITE)
                        .actionLabel(R.string.close)
                        .actionListener(new ActionClickListener() {
                            @Override
                            public void onActionClicked(Snackbar snackbar) {
                                snackbar.dismiss();
                            }
                        }).eventListener(dismiss);
                break;
            case "reconnecting":
                snack = Snackbar.with(getApplicationContext())
                        .color(getResources().getColor(R.color.material_yellow_700))
                        .text(R.string.reconnecting)
                        .duration(Snackbar.SnackbarDuration.LENGTH_INDEFINITE)
                        .swipeToDismiss(false)
                        .eventListener(dismiss);
                break;
            case "reconnectFailed":
                snack = Snackbar.with(getApplicationContext())
                        .color(getResources().getColor(R.color.material_red_700))
                        .text(R.string.connectionError)
                        .duration(Snackbar.SnackbarDuration.LENGTH_INDEFINITE)
                        .swipeToDismiss(false)
                        .eventListener(dismiss);
                break;
            case "networkOffline":
                snack = Snackbar.with(getApplicationContext())
                        .color(getResources().getColor(R.color.material_red_700))
                        .text(R.string.noInternetConnection)
                        .duration(Snackbar.SnackbarDuration.LENGTH_INDEFINITE)
                        .swipeToDismiss(false)
                        .eventListener(dismiss);
                break;
        }

        if(snack != null){
            this.currentSnackbar = key;
            SnackbarManager.show(snack, this);
        }
    }
}
