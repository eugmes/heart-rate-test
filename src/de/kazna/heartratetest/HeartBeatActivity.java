package de.kazna.heartratetest;

import java.util.List;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity showing the heart rate.
 */
public class HeartBeatActivity extends Activity {
    private static final String TAG = HeartBeatActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_ADDRESS = "address";

    public static final UUID HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");

    private String mDeviceAddress;

    private BluetoothLeService mBluetoothLeService;

    private boolean mConnected = false;

    TextView mHeartRateView;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBluetoothLeService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder)service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

            mBluetoothLeService.connect(mDeviceAddress);
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Toast.makeText(HeartBeatActivity.this, R.string.toast_disconnected, Toast.LENGTH_SHORT).show();
                finish();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                final List<BluetoothGattService> services = mBluetoothLeService.getSupportedGattServices();

                for (BluetoothGattService serv : services) {
                    if (HEART_RATE_SERVICE.equals(serv.getUuid())) {
                        BluetoothGattCharacteristic c = serv.getCharacteristic(BluetoothLeService.UUID_HEART_RATE_MEASUREMENT);
                        if (c != null) {
                            Log.e(TAG, "Characteristic found!");
                            mBluetoothLeService.setCharacteristicNotification(c, true);
                            mHeartRateView.setKeepScreenOn(true);
                            return;
                        }
                    }
                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                mHeartRateView.setText(data);
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.heart_beat, menu);

        if (mConnected)
            menu.findItem(R.id.menu_refresh).setVisible(false);
        else
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_indeterminate_progress);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.heart_beat);

        final Intent intent = getIntent();

        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        mHeartRateView = (TextView)findViewById(R.id.heart_rate_view);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null)
            mBluetoothLeService.connect(mDeviceAddress);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
