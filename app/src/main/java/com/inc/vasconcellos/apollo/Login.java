package com.inc.vasconcellos.apollo;

import android.os.Build;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.nkzawa.emitter.Emitter;
import com.nispok.snackbar.Snackbar;
import com.nispok.snackbar.SnackbarManager;
import com.nispok.snackbar.enums.SnackbarType;
import com.nispok.snackbar.listeners.ActionClickListener;
import com.pnikosis.materialishprogress.ProgressWheel;



public class Login extends ActionBarActivity implements View.OnClickListener {

    public static final String TAG = Login.class.getSimpleName();

    private Button loginButton;
    private EditText username;
    private EditText password;
    private ProgressWheel progressWheel;
    private ImageView connectionIcon;
    private TextView connectionStatus;
    
    private Apollo apollo;

    //Listeners
    private Emitter.Listener loginListener;
    private Emitter.Listener connectListener;
    private Emitter.Listener reconnectingListener;
    private Emitter.Listener reconnectFailedListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        //Initialize self reference
        apollo = Apollo.getInstance();

        //Initialize Logo ImageView and set logoDrawable to it
        ImageView logo = (ImageView) findViewById(R.id.logo);
        logo.setImageDrawable(new logoDrawable());

        //Initialize Button and set it's listener to this
        loginButton = (Button) findViewById(R.id.loginButton);
        loginButton.setOnClickListener(this);

        //Initialize Form fields
        username = (EditText) findViewById(R.id.loginUsername);
        password = (EditText) findViewById(R.id.loginPassword);

        //Initialize Connection Status Fields
        connectionStatus = (TextView) findViewById(R.id.connectionStatus);
        connectionIcon = (ImageView) findViewById(R.id.connectionImage);

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
                        Log.i(ApolloSocket.TAG, "Successfully Connected");
                        connectionStatus.setText(R.string.connected);
                        connectionStatus.setTextColor(getResources().getColor(R.color.material_green_700));

                        if(android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP){
                            //Lollipop (API >= 21) Only Methods
                            connectionIcon.setBackground(getApplicationContext().getDrawable(R.drawable.ic_sync_green_18dp));
                        }else{
                            //API < 21
                            connectionIcon.setBackgroundDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_sync_green_18dp));
                        }
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
                        Log.i(ApolloSocket.TAG, "Reconnection Attempt number: " + args[0].toString());
                        connectionStatus.setText(R.string.reconnecting);
                        connectionStatus.setTextColor(getResources().getColor(R.color.material_yellow_700));

                        if(android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP){
                            //Lollipop (API >= 21) Only Methods
                            connectionIcon.setBackground(getApplicationContext().getDrawable(R.drawable.ic_warning_amber_18dp));
                        }else{
                            //API < 21
                            connectionIcon.setBackgroundDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_warning_amber_18dp));
                        }
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
                        Log.i(ApolloSocket.TAG, "Reconnection Failed");
                        connectionStatus.setText(R.string.connectionError);
                        connectionStatus.setTextColor(getResources().getColor(R.color.material_red_700));

                        if(android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP){
                            //Lollipop (API >= 21) Only Methods
                            connectionIcon.setBackground(getApplicationContext().getDrawable(R.drawable.ic_error_red_18dp));
                        }else{
                            //API < 21
                            connectionIcon.setBackgroundDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_error_red_18dp));
                        }
                    }
                });
            }
        };

        //Add Listener to the Event queue
        apollo.on().login(loginListener);
        apollo.on().connect(connectListener);
        apollo.on().reconnecting(reconnectingListener);
        apollo.on().reconnectFailed(reconnectFailedListener);

        //Connect
        apollo.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        apollo.off().login(loginListener);
    }

    public void onLoginReceived(boolean loggedIn){
        //Dismiss Progress Wheel
        progressWheel.setVisibility(View.GONE);

        if (loggedIn) {
                Log.d(TAG, "Logged In");
        }else{
            Log.w(TAG, "Login Failed");

            username.setVisibility(View.VISIBLE);
            password.setVisibility(View.VISIBLE);
            loginButton.setVisibility(View.VISIBLE);

            showErrorMsg();
        }
    }

    public void showErrorMsg(){
        SnackbarManager.show(
                Snackbar.with(getApplicationContext())
                        .type(SnackbarType.MULTI_LINE) // Set is as a multi-line snackbar
                        .color(getResources().getColor(R.color.material_red_700)) //change the background color
                        .text(R.string.failedLogin)
                        .duration(Snackbar.SnackbarDuration.LENGTH_INDEFINITE) //change display time to indefinite
                        .actionLabel(R.string.close) // action button label
                        .actionListener(new ActionClickListener() {
                            @Override
                            public void onActionClicked(Snackbar snackbar) {
                                snackbar.dismiss();
                            }
                        }), this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_login, menu);
        return true;
    }

    //Login Button Click Listener
    @Override
    public void onClick(View v) {
        if(apollo.isConnected() && !apollo.isLogged()){
            String username = this.username.getText().toString();
            String password = this.password.getText().toString();

            if (username.length() > 0 && password.length() > 0) {
                this.username.setVisibility(View.GONE);
                this.password.setVisibility(View.GONE);
                this.loginButton.setVisibility(View.GONE);
                this.progressWheel.setVisibility(View.VISIBLE);

                apollo.emit().login(username, password);
                Log.d(TAG, "Emitted Login Event to Server");
            }else{
                showErrorMsg();
            }
        }
    }
}
