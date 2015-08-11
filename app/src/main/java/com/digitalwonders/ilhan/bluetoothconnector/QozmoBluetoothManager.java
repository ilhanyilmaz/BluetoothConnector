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
import java.util.Iterator;
import java.util.UUID;

/**
 * Created by ilhan on 06.08.2015.
 */

public class QozmoBluetoothManager {

    public static final int MSG_CONNECTED = 1;
    public static final int MSG_READ = 2;
    private static final String TAG = "DeviceListActivity";
    public static String EXTRA_DEVICE_ADDRESS = "device_address";
    private static String DEVICE_NAME = "QozmoNode";

    private BluetoothAdapter mBluetoothAdapter;
    //private ArrayAdapter<String> mNewDevicesArrayAdapter;
    private Activity mActivity;
    private QozmoConnectionListener mConnectionListener;
    private ArrayAdapter<String> mArrayAdapter;
    private final static int REQUEST_ENABLE_BT = 1;
    private ArrayList<BluetoothDevice> devices;
    private ArrayList<UUID> deviceUUIDS;
    private AlertDialog.Builder mBuilderSingle;
    private ConnectedThread mConnectedThread;

    public QozmoBluetoothManager(Activity activity) {
        // Setup the window
        mActivity = activity;

        // Get the local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();





    }

    public void setOnQozmoConnectionListener(QozmoConnectionListener q) {
        mConnectionListener = q;
    }

    private boolean mAlreadyFetched = false;

    public void discover() {

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_UUID);

        if (!mBluetoothAdapter.isEnabled()) {

            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            mActivity.registerReceiver(mReceiver, filter);

            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        else {
            mActivity.registerReceiver(mReceiver, filter);
            doDiscovery();
        }

        mBuilderSingle = new AlertDialog.Builder(
                mActivity);
        //mBuilderSingle.setIcon(R.drawable.ic_launcher);
        mBuilderSingle.setTitle("Choose device:");

        mBuilderSingle.setNegativeButton("cancel",
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        mBuilderSingle.setAdapter(mArrayAdapter,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String strName = mArrayAdapter.getItem(which);

                        Log.i(TAG, "Connecting to device " + which + " : " + strName);
                        new ConnectThread(devices.get(which)).start();
                        dialog.dismiss();
                    }
                });
    }


    public interface QozmoConnectionListener {
        void onQozmoConnected(String s);
        void onQozmoMessageReceived(String message);
    }

    protected void close() {


        // Make sure we're not doing discovery anymore
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.cancelDiscovery();
        }

        // Unregister broadcast listeners
        mActivity.unregisterReceiver(mReceiver);
    }

    private ProgressDialog mProgress;
    public void doDiscovery() {
        Log.d(TAG, "doDiscovery()");

        // Indicate scanning in the title
        //mActivity.setProgressBarIndeterminateVisibility(true);

        mProgress = new ProgressDialog(mActivity);
        mProgress.setTitle("Searching");
        mProgress.setMessage("Wait while searching...");
        mProgress.show();

        // To dismiss the dialog

        /*
        // Get a set of currently paired devices
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            String noDevices = "No device found!";
            mArrayAdapter.add(noDevices);
        }*/

        mArrayAdapter = new ArrayAdapter<String>(
                mActivity,
                android.R.layout.select_dialog_singlechoice);

        devices = new ArrayList<>();
        deviceUUIDS = new ArrayList<>();
        // If we're already discovering, stop it
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }

        // Request discover from BluetoothAdapter
        mBluetoothAdapter.startDiscovery();
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
                //if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                if(device.getName().equals(DEVICE_NAME)) {
                    mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                    devices.add(device);
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.i(TAG, "Discovery finished!");


                Iterator<BluetoothDevice> itr = devices.iterator();
                while (itr.hasNext()) {
                    // Get Services for paired devices
                    BluetoothDevice device = itr.next();
                    Log.i(TAG, "\nGetting Services for " + device.getName() + ", " + device);
                    if (!device.fetchUuidsWithSdp()) {
                        Log.i(TAG, "\nSDP Failed for " + device.getName());
                    }
                }

                //mActivity.setProgressBarIndeterminateVisibility(false);
                //setTitle(R.string.select_device);
                if (mArrayAdapter.getCount() == 0) {
                    //String noDevices = getResources().getText(R.string.none_found).toString();
                    String noDevices = "No device found!";
                    Log.i("Qozmo", noDevices);
                    //mArrayAdapter.add(noDevices);
                }
            } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:

                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:

                        break;
                    case BluetoothAdapter.STATE_ON:
                        doDiscovery();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:

                        break;
                }
            } else if (action.equals(BluetoothDevice.ACTION_UUID)) {
                if(mAlreadyFetched)
                    return;
                //final int state = intent.getIntExtra(BluetoothDevice.EXTRA_UUID, BluetoothDevice.ERROR);
                //Log.i(TAG, "state: " + state);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Parcelable[] uuidExtra = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);

                for (int i=0; i<uuidExtra.length; i++) {
                    deviceUUIDS.add(UUID.fromString(uuidExtra[i].toString()));
                    Log.i(TAG, "\n  Device: " + device.getName() + ", " + device + ", Service: " + uuidExtra[i].toString());
                }

                mProgress.dismiss();
                mBuilderSingle.show();

                mAlreadyFetched = true;
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
                tmp = device.createRfcommSocketToServiceRecord(deviceUUIDS.get(0));
                //tmp = device.createInsecureRfcommSocketToServiceRecord(deviceUUIDS.get(0));
            } catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                Log.i(TAG, "Connecting...");
                mmSocket.connect();
                mHandler.obtainMessage(MSG_CONNECTED)
                        .sendToTarget();
            } catch (IOException connectException) {
                Log.e(TAG, "Connection problem!");
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            manageConnectedSocket(mmDevice, mmSocket);
        }

        /** Will cancel an in-mProgress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    private void manageConnectedSocket(BluetoothDevice device, BluetoothSocket socket) {

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
    }



    public void sendMessageToDevice(String message) {

        char EOT = (char)3 ;
        byte[] bytes = (message+EOT).getBytes();
        mConnectedThread.write(bytes);

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
            } catch (IOException e) { }

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
                    mHandler.obtainMessage(MSG_READ, bytes, -1, buffer)
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
            } catch (IOException e) { }
        }

        // Call this from the main activity to shutdown the connection
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
    private String receivedMessage = "";

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {


            String sMsg;
            char EOT = (char)3;
            switch (msg.what) {
                case MSG_CONNECTED:

                    //mAlreadyFetched = true;
                    mConnectionListener.onQozmoConnected("hede hede");
                    break;
                case MSG_READ:

                    byte[] bytes = (byte[])msg.obj;
                    try {
                        sMsg = new String(bytes, "UTF-8");
                        sMsg = sMsg.substring(0, msg.arg1);
                        receivedMessage = receivedMessage.concat(sMsg);
                        if(sMsg.indexOf(EOT)>-1) {
                            mConnectionListener.onQozmoMessageReceived(receivedMessage);
                            receivedMessage = "";
                        }
                        //Log.i(TAG, "message received: " + sMsg);
                    }
                    catch (UnsupportedEncodingException e) {
                        Log.e(TAG, e.toString());
                    }
                    break;
            }
        }
    };

}
