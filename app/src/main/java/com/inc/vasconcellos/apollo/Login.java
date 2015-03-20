package com.inc.vasconcellos.apollo;

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

import java.net.URISyntaxException;


public class Login extends ActionBarActivity implements View.OnClickListener {

    private static final String TAG = Login.class.getSimpleName();

    private Button loginButton;
    private EditText username;
    private EditText password;
    private Socket socket;
    private Boolean connected = false;
    private Boolean logged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        //Initialize Logo ImageView and set logoDrawable to it
        ImageView logo = (ImageView) findViewById(R.id.logo);
        logo.setImageDrawable(new logoDrawable());

        //Initialize Button and set it's listener to this
        loginButton = (Button) findViewById(R.id.loginButton);
        loginButton.setOnClickListener(this);

        //Initialize Text fields
        username = (EditText) findViewById(R.id.loginUsername);
        password = (EditText) findViewById(R.id.loginPassword);

        //Initialize Socket and connect to server
        try {
            socket = IO.socket("http://104.236.0.59:1969");

            //Initialize Listeners
            socket.on("login", new Emitter.Listener(){
                @Override
                public void call(final Object... args) {
                    runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    if(args[1] != null){
                                        Boolean loggedIn = (Boolean) args[1];

                                        if (loggedIn) {
                                            logged = true;
                                            loginButton.setText("Logado");
                                        } else {
                                            loginButton.setText("Error");
                                        }
                                    }else{
                                        loginButton.setText("Error");
                                    }
                                }
                            }
                    );
                }
            });

            socket.connect();
            connected = true;
            Log.d(TAG, "Connected to Server");

        } catch (URISyntaxException e) {
            Log.w(TAG, "Server is offline");
            loginButton.setText("Error");
        }
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
        String username = this.username.getText().toString();
        String password = this.password.getText().toString();

        if (connected && !logged && username.length() > 0 && password.length() > 0) {
            socket.emit("login", username, password);
            Log.d(TAG, "Emitted Login Event to Server");
        }
    }
}
