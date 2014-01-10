/*
 * Copyright (C) 2013 Arun Raghavan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.arunraghavan.bluebike;

import android.app.Activity;
import android.os.Bundle;

import java.util.UUID;

import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import net.arunraghavan.bluebikelib.BlueBikeSensor;

public class BlueBikeActivity extends Activity
{
    private static final String TAG = BlueBikeActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int SCAN_TIMEOUT = 10000; // 10 seconds
    private static final UUID[] UUIDS = new UUID[] { BlueBikeSensor.CSC_SERVICE_UUID };

    private BlueBikeSensor mSensor;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;
    private BluetoothDevice mBluetoothDevice;

    private boolean mScanning;
    private Handler mHandler;

    private boolean hasSpeed, hasCadence;
    private double instSpeed, instCadence;

    private BlueBikeSensor.Callback mCallback;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Initialise the wheel size spinner
        Spinner wheelSpinner = (Spinner) findViewById(R.id.wheelSizeList);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.wheel_size_array,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        wheelSpinner.setAdapter(adapter);

        mHandler = new Handler();

        mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord)
            {
                BlueBikeActivity.this.mBluetoothDevice = device;
                stopScan();

                BlueBikeActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run()
                    {
                        // FIXME: update device list, update UI
                    }
                });

                startRead();
            }
        };

        mCallback = new BlueBikeSensor.Callback() {
            @Override
            public void onConnectionStateChange(BlueBikeSensor sensor, BlueBikeSensor.ConnectionState newState)
            {
                BlueBikeActivity parent = BlueBikeActivity.this;

                if (newState == BlueBikeSensor.ConnectionState.ERROR) {
                    parent.runOnUiThread(new Runnable() {
                        @Override
                        public void run()
                        {
                            Toast.makeText(BlueBikeActivity.this, "Got error", Toast.LENGTH_LONG).show();
                        }
                    });

                    mSensor = null;

                } else if (newState == BlueBikeSensor.ConnectionState.CONNECTED) {
                    parent.hasSpeed = parent.mSensor.hasSpeed();
                    parent.hasSpeed = parent.mSensor.hasCadence();

                    parent.runOnUiThread(new Runnable() {
                        @Override
                        public void run()
                        {
                            // FIXME: trigger UI update
                        }
                    });

                    parent.mSensor.setNotificationsEnabled(true);
                }
            }

            @Override
            public void onSpeedUpdate(BlueBikeSensor sensor, double distance, double elapsedUs)
            {
                BlueBikeActivity parent = BlueBikeActivity.this;

                // FIXME: don't hardcode metric units
                parent.instSpeed = distance * 36 / (elapsedUs / 100);
                // FIXME: trigger UI update
                Log.d(TAG, "Speed: " + distance + ", " + elapsedUs + " = " + parent.instSpeed + " km/h");
            }

            @Override
            public void onCadenceUpdate(BlueBikeSensor sensor, int rotations, double elapsedUs)
            {
                BlueBikeActivity parent = BlueBikeActivity.this;

                parent.instCadence = rotations * 60 / (elapsedUs / 1000000);
                // FIXME: trigger UI update
                Log.e(TAG, "Cadence: " + rotations + ", " + elapsedUs + " = " + parent.instCadence  + " rpm");
            }
        };

        startScan();
    }

    private void stopScan()
    {
        // FIXME: Can this be called from multiple threads?
        mScanning = false;
        mBluetoothAdapter.stopLeScan(mLeScanCallback);
    }

    private void startScan()
    {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        if (mScanning)
            return;

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.msg_ble_notsupp, Toast.LENGTH_LONG).show();
            return;
        }

        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Toast.makeText(this, R.string.msg_bt_disabled, Toast.LENGTH_LONG).show();
            return;
        }

        mHandler.postDelayed(new Runnable() {
                @Override
                public void run()
                {
                    if (BlueBikeActivity.this.mScanning) {
                        Toast.makeText(BlueBikeActivity.this, R.string.msg_err_scantimeout, Toast.LENGTH_LONG).show();
                        stopScan();
                    }
                }
            }, SCAN_TIMEOUT);

        mScanning = true;
        mBluetoothAdapter.startLeScan(UUIDS, mLeScanCallback);
    }

    private void startRead()
    {
        if (mBluetoothDevice == null) {
            Toast.makeText(this, R.string.msg_err_noscan, Toast.LENGTH_LONG).show();
            return;
        }

        if (mSensor == null)
            mSensor = new BlueBikeSensor(this, mBluetoothDevice, 622 /* FIXME */, mCallback);
    }
}
