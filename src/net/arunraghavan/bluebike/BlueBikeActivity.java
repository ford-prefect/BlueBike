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

    private BlueBikeSensor.Callback mCallback;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

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
                        Toast.makeText(BlueBikeActivity.this, "Using device " + BlueBikeActivity.this.mBluetoothDevice,
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
        };

        mCallback = new BlueBikeSensor.Callback() {
            @Override
            public void onConnectionStateChange(BlueBikeSensor sensor, BlueBikeSensor.ConnectionState newState)
            {
                if (newState == BlueBikeSensor.ConnectionState.ERROR) {
                    BlueBikeActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run()
                        {
                            Toast.makeText(BlueBikeActivity.this, "Got error", Toast.LENGTH_LONG).show();
                        }
                    });

                    mSensor = null;

                } else if (newState == BlueBikeSensor.ConnectionState.CONNECTED) {
                    BlueBikeActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run()
                        {
                            String status = String.format("Speed: %s, Cadence: %s",
                                BlueBikeActivity.this.mSensor.hasSpeed() ? "yes" : "no",
                                BlueBikeActivity.this.mSensor.hasCadence() ? "yes" : "no");
                            Toast.makeText(BlueBikeActivity.this, status, Toast.LENGTH_LONG).show();
                        }
                    });

                    BlueBikeActivity.this.mSensor.setNotificationsEnabled(true);
                }
            }

            @Override
            public void onSpeedUpdate(BlueBikeSensor sensor, double distance, double elapsedUs)
            {
                Log.e(TAG, "Speed: " + distance + ", " + elapsedUs + " = "
                        + distance * 36 / (elapsedUs / 100) + " km/h");
            }

            @Override
            public void onCadenceUpdate(BlueBikeSensor sensor, int rotations, double elapsedUs)
            {
                Log.e(TAG, "Cadence: " + rotations + ", " + elapsedUs + " = "
                        + rotations * 60 / (elapsedUs / 1000000) + " rpm");
            }
        };
    }

    private void stopScan()
    {
        // FIXME: Can this be called from multiple threads?
        mScanning = false;
        mBluetoothAdapter.stopLeScan(mLeScanCallback);
    }

    public void startScan(View view)
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

    public void startRead(View view)
    {
        if (mBluetoothDevice == null) {
            Toast.makeText(this, R.string.msg_err_noscan, Toast.LENGTH_LONG).show();
            return;
        }

        if (mSensor == null)
            mSensor = new BlueBikeSensor(this, mBluetoothDevice, 622, mCallback);
    }
}
