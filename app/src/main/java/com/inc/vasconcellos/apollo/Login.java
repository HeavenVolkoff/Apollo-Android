package com.inc.vasconcellos.apollo;

import android.app.Activity;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.nispok.snackbar.Snackbar;
import com.nispok.snackbar.SnackbarManager;
import com.nispok.snackbar.enums.SnackbarType;
import com.nispok.snackbar.listeners.ActionClickListener;
import com.pnikosis.materialishprogress.ProgressWheel;

import java.net.URISyntaxException;


public class Login extends ActionBarActivity implements View.OnClickListener {

    private static final String TAG = Login.class.getSimpleName();

    private Button loginButton;
    private EditText username;
    private EditText password;
    private ProgressWheel progressWheel;
    
    private Apollo apollo;
    private Activity self;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        //Initialize self reference
        apollo = Apollo.getInstance();
        self = this;

        //Initialize Logo ImageView and set logoDrawable to it
        ImageView logo = (ImageView) findViewById(R.id.logo);
        logo.setImageDrawable(new logoDrawable());

        //Initialize Button and set it's listener to this
        loginButton = (Button) findViewById(R.id.loginButton);
        loginButton.setOnClickListener(this);

        //Initialize Text fields
        username = (EditText) findViewById(R.id.loginUsername);
        password = (EditText) findViewById(R.id.loginPassword);

        //Initialize Spinner Bar
        progressWheel = (ProgressWheel) findViewById(R.id.progressWheel);
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
                        .duration(Snackbar.SnackbarDuration.LENGTH_INDEFINITE)
                        .actionLabel(R.string.close) // action button label
                        .actionListener(new ActionClickListener() {
                            @Override
                            public void onActionClicked(Snackbar snackbar) {
                                snackbar.dismiss();
                            }
                        }), self);
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
