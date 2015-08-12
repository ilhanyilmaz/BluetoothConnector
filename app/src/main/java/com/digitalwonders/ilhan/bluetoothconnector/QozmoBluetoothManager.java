package com.digitalwonders.ilhan.bluetoothconnector;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.ArrayAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Created by ilhan on 06.08.2015.
 *
 * 1- Create instance with your activity
 *      QozmoBluetoothManager qbm = new QozmoBluetoothManager(this);
 *
 * 2- Set action listener to your activity which implements QozmoActionListener
 *      qbm.setOnQozmoActionListener(this);
 *
 * 3- Implement QozmoActionListener function in your activity
 *      void onQozmoAction(int action, String message);
 *
 * 4- Send message to Qozmo using:
 *      qbm.sendMessageToDevice(message);
 *
 */

public class QozmoBluetoothManager {

    public static final int ACTION_CONNECTED = 1;
    public static final int ACTION_MSG_READ = 2;
    public static final int ACTION_DISCONNECTED = 3;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final String TAG = "DeviceListActivity";
    private static final String DEVICE_NAME = "QozmoNode";
    private static final String NO_DEVICE = "Qozmo not available";

    private Activity mActivity;
    private BluetoothAdapter mBluetoothAdapter;
    private QozmoActionListener mConnectionListener;
    private ArrayAdapter<String> mArrayAdapter;
    private ConnectedThread mConnectedThread;
    private ArrayList<BluetoothDevice> mDevices;
    private UUID mDeviceUUID = null;
    private AlertDialog.Builder mBuilderSingle;
    private ProgressDialog mProgress;
    private String receivedMessage = "";


    public interface QozmoActionListener {
        void onQozmoAction(int action, String message);
    }

    public QozmoBluetoothManager(Activity activity) {

        mActivity = activity;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mProgress = new ProgressDialog(mActivity);
        mDevices = new ArrayList<>();
        createListDialog();
        registerDiscoveryFilters();
    }

    public void setOnQozmoActionListener(QozmoActionListener q) {
        mConnectionListener = q;
    }

    public void discover() {

        if (!mBluetoothAdapter.isEnabled())
            requestEnableBluetooth();

        else {
            doDiscovery();
        }
    }



    public void sendMessageToDevice(String message) {

        char EOT = (char)3 ;
        byte[] bytes = (message+EOT).getBytes();
        mConnectedThread.write(bytes);

    }


    public void close() {

        // Make sure we're not doing discovery anymore
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.cancelDiscovery();
        }

        // Unregister broadcast listeners
        mActivity.unregisterReceiver(mReceiver);

        closeConnection();
    }


    private void registerDiscoveryFilters() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_UUID);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mActivity.registerReceiver(mReceiver, filter);
    }

    private void requestEnableBluetooth() {

        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        mActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }



    private void createListDialog() {

        mArrayAdapter = new ArrayAdapter<>(
                mActivity,
                android.R.layout.select_dialog_singlechoice);

        mBuilderSingle = new AlertDialog.Builder(
                mActivity);
        //mBuilderSingle.setIcon(R.drawable.ic_launcher);
        mBuilderSingle.setTitle("Choose device:");

        mBuilderSingle.setNegativeButton("cancel",
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        mBluetoothAdapter.cancelDiscovery();
                        dialog.dismiss();
                    }
                });

        mBuilderSingle.setAdapter(mArrayAdapter,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        final String strName = mArrayAdapter.getItem(which);

                        if (strName == NO_DEVICE) {
                            dialog.dismiss();
                            return;
                        }

                        displayDialog("Connecting", "Connecting to device " + strName);

                        if (mBluetoothAdapter.isDiscovering()) {
                            mBluetoothAdapter.cancelDiscovery();
                        }

                        BluetoothDevice device = mDevices.get(which);

                        Log.i(TAG, "Bond state: " + device.getBondState());

                        if (!device.fetchUuidsWithSdp()) {
                            Log.i(TAG, "\nSDP Failed for " + device.getName());
                        }

                        //Log.i(TAG, "Connecting to device " + which + " : " + strName);

                        dialog.dismiss();

                    }
                });
    }
    private void displayDialog(String title, String message) {

        mProgress.setTitle(title);
        mProgress.setMessage(message);
        mProgress.show();
    }

    private void doDiscovery() {


        Log.d(TAG, "doDiscovery()");

        mDevices.clear();
        mArrayAdapter.clear();
        mArrayAdapter.notifyDataSetChanged();

        //mBuilderSingle.show();
        displayDialog("Searching", "Searching for Qozmo Devices...");

        /*// If we're already discovering, stop it
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }*/

        // Request discover from BluetoothAdapter
        mBluetoothAdapter.startDiscovery();
    }

    private void closeConnection() {
        if(mConnectedThread!=null)
            mConnectedThread.cancel();

        mDeviceUUID = null;
        mHandler.obtainMessage(ACTION_DISCONNECTED)
                .sendToTarget();
    }




    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed already
                if(device.getName().equals(DEVICE_NAME)) {
                    mProgress.dismiss();
                    mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                    mDevices.add(device);
                    mBuilderSingle.show();
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.i(TAG, "Discovery finished!");
                //mActivity.setProgressBarIndeterminateVisibility(false);
                //setTitle(R.string.select_device);
                if (mArrayAdapter.getCount() == 0) {
                    //String noDevices = getResources().getText(R.string.none_found).toString();
                    Log.i(TAG, NO_DEVICE);
                    mArrayAdapter.add(NO_DEVICE);
                    mBuilderSingle.show();
                    mProgress.dismiss();
                }
            } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.i(TAG, "Bluetooth is off!");
                        requestEnableBluetooth();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        closeConnection();
                        Log.i(TAG, "Bluetooth is turning off!");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.i(TAG, "Bluetooth is on!");
                        discover();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.i(TAG, "Bluetooth is turning on!");
                        break;
                }
            } else if (action.equals(BluetoothDevice.ACTION_UUID)) {
                if(mDeviceUUID!=null)
                    return;

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Parcelable[] uuidExtra = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);

                mDeviceUUID = UUID.fromString(uuidExtra[0].toString());

                new ConnectThread(device).start();
                /*for (int i=0; i<uuidExtra.length; i++) {
                    deviceUUIDS.add(UUID.fromString(uuidExtra[i].toString()));
                    Log.i(TAG, "\n  Device: " + device.getName() + ", " + device + ", Service: " + uuidExtra[i].toString());
                }*/

                mProgress.dismiss();
            }
        }
    };



    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;


            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {

                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(mDeviceUUID);
                //tmp = device.createInsecureRfcommSocketToServiceRecord(deviceUUIDS.get(0));
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
            mmSocket = tmp;
        }

        public void run() {

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                Log.i(TAG, "Connecting...");
                mmSocket.connect();
                mHandler.obtainMessage(ACTION_CONNECTED)
                        .sendToTarget();
            } catch (IOException connectException) {
                Log.e(TAG, "Connection problem!");
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, closeException.toString());
                }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            manageConnectedSocket(mmDevice, mmSocket);
        }

        /** Will cancel an in-mProgress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    private void manageConnectedSocket(BluetoothDevice device, BluetoothSocket socket) {

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
    }





    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    mHandler.obtainMessage(ACTION_MSG_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        // Call this from the main activity to shutdown the connection
        public void cancel() {
            try {
                mmInStream.close();
                mmOutStream.close();
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }
    }


    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {


            String sMsg;
            char EOT = (char)3;
            switch (msg.what) {
                case ACTION_CONNECTED:

                    mConnectionListener.onQozmoAction(ACTION_CONNECTED, "");
                    break;
                case ACTION_MSG_READ:

                    byte[] bytes = (byte[])msg.obj;
                    try {
                        sMsg = new String(bytes, "UTF-8");
                        sMsg = sMsg.substring(0, msg.arg1);
                        receivedMessage = receivedMessage.concat(sMsg);
                        if(sMsg.indexOf(EOT)>-1) {
                            mConnectionListener.onQozmoAction(ACTION_MSG_READ, receivedMessage);
                            receivedMessage = "";
                        }
                        //Log.i(TAG, "message received: " + sMsg);
                    }
                    catch (UnsupportedEncodingException e) {
                        Log.e(TAG, e.toString());
                    }
                    break;
                case ACTION_DISCONNECTED:
                    mConnectionListener.onQozmoAction(ACTION_CONNECTED, "");
                    break;
            }
        }
    };

}
