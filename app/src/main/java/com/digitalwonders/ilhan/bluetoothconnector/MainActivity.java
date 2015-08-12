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

public class MainActivity extends AppCompatActivity implements QozmoBluetoothManager.QozmoActionListener {

    private static final String TAG = "BluetoothConnector";
    private QozmoBluetoothManager qbm;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        qbm = new QozmoBluetoothManager(this);
        qbm.setOnQozmoActionListener(this);
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listDevices();
            }
        });
        findViewById(R.id.button2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = ((EditText) findViewById(R.id.edit_text)).getText().toString();
                if (message.length() > 0) {
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
    public void onDestroy() {
        qbm.close();
        super.onDestroy();
    }

    @Override
    public void onQozmoAction(int action, String msg) {
        Log.i(TAG, msg);
        switch (action) {
            case QozmoBluetoothManager.ACTION_CONNECTED:
                findViewById(R.id.edit_text).setEnabled(true);
                findViewById(R.id.button2).setEnabled(true);
                break;
            case QozmoBluetoothManager.ACTION_DISCONNECTED:
                findViewById(R.id.edit_text).setEnabled(false);
                findViewById(R.id.button2).setEnabled(false);
                break;
            case QozmoBluetoothManager.ACTION_MSG_READ:
                ((TextView)findViewById(R.id.textView)).setText(msg);
                break;

        }

    }
}
