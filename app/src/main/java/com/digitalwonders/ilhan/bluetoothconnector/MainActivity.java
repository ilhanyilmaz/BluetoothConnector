package com.digitalwonders.ilhan.bluetoothconnector;

import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;

public class MainActivity extends AppCompatActivity implements QozmoBluetoothManager.QozmoConnectionListener {

    private static final String TAG = "BluetoothConnector";
    private QozmoBluetoothManager qbm;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        qbm = new QozmoBluetoothManager(this);
        qbm.setOnQozmoConnectionListener(this);
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listDevices();
            }
        });
        findViewById(R.id.button2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = ((EditText)findViewById(R.id.edit_text)).getText().toString();
                if(message.length() > 0) {
                    qbm.sendMessageToDevice(message);
                    Log.i(TAG, "sending message: " + message);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void listDevices() {
        qbm.discover();
    }

    @Override
    public void onQozmoConnected(String s) {
        Log.i(TAG, s);
        findViewById(R.id.edit_text).setEnabled(true);
        findViewById(R.id.button2).setEnabled(true);
    }

    @Override
    public void onQozmoMessageReceived(String msg) {

        Log.i(TAG, "Message: " + msg);
        ((TextView)findViewById(R.id.textView)).setText(msg);
    }


}
