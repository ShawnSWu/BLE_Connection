package com.example.user.ble_connection;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothLeScanner mBluetoothLeScanner;
    private boolean isScanning = false;
    private ScanCallback mScanCallback;
    private ArrayList<BluetoothDevice> mBluetoothDevices = new ArrayList<>();
    private ArrayList<String> scanResultList;
    private static final long SCAN_PERIOD = 60000;
    private Handler mHandler = new Handler();

    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCallback mBluetoothGattCallBack;
    public static final UUID BATTERY_CHAR_UUID =
            UUID.fromString("0000ff0c-0000-1000-8000-00805f9b34fb");
    enum Status {
        UNKNOWN, LOW, FULL, CHARGING, NOT_CHARGING;
        public static Status fromByte(byte b) {
            switch (b) {
                case 1:
                    return LOW;
                case 2:
                    return CHARGING;
                case 3:
                    return FULL;
                case 4:
                    return NOT_CHARGING;
                default:
                    return UNKNOWN;
            }
        }
    }

    private static String[] PERMISSIONS_ACCESS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION };
    private static final int REQUEST_ACCESS_FINE_LOCATION = 1;
    private Dialog dialogPermission = null;
    private int REQUEST_ENABLE_BT = 1;

    private TextView textView_info;
    private ListView listView_scanResult;
    private TextView textView_data;

    private BroadcastReceiver updateUIReceiver;
    private String ACTION_UI_STATE = "state";
    private String ACTION_UI_DATA = "data";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkBluetoothLowEnergyFeature();
        initBluetoothService();
        initScanCallback();
        initBluetoothGattCallback();
        initUI();
        initBroadcastReceiver();
    }

    private void checkBluetoothLowEnergyFeature() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initBluetoothService() {
        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.bt_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initScanCallback() {
        mScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                mBluetoothDevices.clear();
                scanResultList.clear();
                saveScanResult(result);
                setAndUpdateListView();
            }
            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
                mBluetoothDevices.clear();
                scanResultList.clear();
                for (ScanResult result : results) {
                    saveScanResult(result);
                }
                setAndUpdateListView();
            }
            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Toast.makeText(MainActivity.this
                        , "Error scanning devices: " + errorCode
                        , Toast.LENGTH_LONG).show();
            }
        };
    }
    private void saveScanResult(ScanResult result) {
        if (!mBluetoothDevices.contains(result.getDevice())) {
            mBluetoothDevices.add(result.getDevice());
            if (result.getScanRecord() != null) {
                ScanRecord mScanRecord = result.getScanRecord();
                if (mScanRecord.getServiceUuids() != null) {
                    String services = getServicesUuid(mScanRecord);
                    scanResultList.add(mScanRecord.getDeviceName() + " rssi:" + result.getRssi() + "\r\n"
                            + result.getDevice().getAddress() + "\r\n"
                            + services);
                }
            }
        }
    }
    private String getServicesUuid(ScanRecord mScanRecord) {
        List<ParcelUuid> mServiceList = mScanRecord.getServiceUuids();
        StringBuilder stringBuilder = new StringBuilder();
        if (mServiceList != null) {
            for (ParcelUuid uuid : mServiceList) {
                byte[] data = mScanRecord.getServiceData(uuid);
                if (data != null) {
                    stringBuilder.append(Arrays.toString(data)).append("\n");
                }
            }
        }
        return stringBuilder.toString();
    }
    private void setAndUpdateListView() {
        ListAdapter listAdapter = new ArrayAdapter<>(getBaseContext()
                , android.R.layout.simple_expandable_list_item_1, scanResultList);
        listView_scanResult.setAdapter(listAdapter);
    }

    private void initBluetoothGattCallback() {
        mBluetoothGattCallBack = new BluetoothGattCallback() {
            //偵測藍芽連線狀態
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        Log.d("onConnectionStateChange", "onConnectionStateChange CONNECTED status:" + status);
                        stopScanLeDevice();
                        notifyUI(ACTION_UI_STATE, getResources().getString(R.string.bt_connected));
                        mBluetoothGatt = gatt;
                        gatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        notifyUI(ACTION_UI_STATE, getResources().getString(R.string.bt_disconnected));
                        Log.d("onConnectionStateChange", "onConnectionStateChange DISCONNECTED status:" + status);
                        break;
                    default:
                        notifyUI(ACTION_UI_STATE, getResources().getString(R.string.bt_state_other));
                }
            }
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                Log.d("ServicesDiscovered", "onServicesDiscovered status:" + status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    findBatteryCharacteristicInServices(gatt.getServices());
                }
            }
            @Override
            public void onCharacteristicRead(BluetoothGatt gatt
                    , BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                if (characteristic.getUuid().equals(BATTERY_CHAR_UUID)) {
                    showBatteryInfo(characteristic.getValue());
                }
            }
        };
    }
    private void notifyUI(String type, String data) {
        Intent intent = new Intent();
        intent.setAction(type);
        intent.putExtra(type, data);
        sendBroadcast(intent);
    }
    private void findBatteryCharacteristicInServices(List<BluetoothGattService> services) {
        if (services != null) {
            Log.d("Services size", "服務數量:" + services.size());
            for (BluetoothGattService bluetoothGattService : services) {
                Log.d("Services", bluetoothGattService.getUuid().toString());
                List<BluetoothGattCharacteristic> characteristics = bluetoothGattService.getCharacteristics();
                for (BluetoothGattCharacteristic chara : characteristics) {
                    if (chara.getUuid().equals(BATTERY_CHAR_UUID)) {
                        Log.d("ServicesDiscovered", "battery found!");
                        mBluetoothGatt.readCharacteristic(chara);
                    }
                }
            }
        }
    }
    private void showBatteryInfo(byte[] value) {
        String showData = "";
        showData += "剩餘電量: " + getElectricity(value) + "%\n";
        showData += "充電次數: " + getChargingTimes(value) + "\n";
        showData += "充電狀態: " + getChargingStatus(value) + "\n";
        showData += "上一次充電時間: " + getLastChargeDate(value) + "\n";
        notifyUI(ACTION_UI_DATA, showData);
    }
    private int getElectricity(byte[] value) {
        return (int)value[0];
    }
    private int getChargingTimes(byte[] value) {
        return 0xffff & (0xff & value[7] | (0xff & value[8]) << 8);
    }
    private String getChargingStatus(byte[] value) {
        return Status.fromByte(value[9]).toString();
    }
    private String getLastChargeDate(byte[] value) {
        Calendar lastChargedDate = Calendar.getInstance();
        lastChargedDate.set(Calendar.YEAR, value[1] + 2000);
        lastChargedDate.set(Calendar.MONTH, value[2]);
        lastChargedDate.set(Calendar.DATE, value[3]);
        lastChargedDate.set(Calendar.HOUR_OF_DAY, value[4]);
        lastChargedDate.set(Calendar.MINUTE, value[5]);
        lastChargedDate.set(Calendar.SECOND, value[6]);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(lastChargedDate.getTime());
    }

    private void initUI() {
        textView_info = findViewById(R.id.textView_info);
        listView_scanResult = findViewById(R.id.listView_scanResult);
        listView_scanResult.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final BluetoothDevice mBluetoothDevice = mBluetoothDevices.get(position);
                mBluetoothGatt = mBluetoothDevice.connectGatt(getApplicationContext()
                        , false, mBluetoothGattCallBack);
            }
        });
        scanResultList = new ArrayList<>();
        setAndUpdateListView();
        textView_data = findViewById(R.id.textView_data);
    }

    private void initBroadcastReceiver() {
        updateUIReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_UI_STATE.equals(intent.getAction())) {
                    textView_info.setText(intent.getStringExtra(ACTION_UI_STATE));
                } else if (ACTION_UI_DATA.equals(intent.getAction())) {
                    textView_data.setText(intent.getStringExtra(ACTION_UI_DATA));
                }
            }
        };
        IntentFilter filter_main = new IntentFilter();
        filter_main.addAction(ACTION_UI_STATE);
        filter_main.addAction(ACTION_UI_DATA);
        registerReceiver(updateUIReceiver, filter_main);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(updateUIReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            checkPermission();
        } else {
            checkBluetoothEnableThenStartScan();
        }
    }

    private void checkPermission() {
        int permission = ActivityCompat.checkSelfPermission(this
                , Manifest.permission.ACCESS_FINE_LOCATION);
        if (permission == PackageManager.PERMISSION_GRANTED) {
            checkBluetoothEnableThenStartScan();
        } else {
            showDialogForPermission();
        }
    }

    private void showDialogForPermission() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MainActivity.this);
        dialogBuilder.setTitle(getResources().getString(R.string.dialog_permission_title));
        dialogBuilder.setMessage(getResources().getString(R.string.dialog_permission_message));
        dialogBuilder.setPositiveButton(getResources().getString(R.string.dialog_permission_ok)
                , new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                PERMISSIONS_ACCESS,
                                REQUEST_ACCESS_FINE_LOCATION);
                    }
                });
        dialogBuilder.setNegativeButton(getResources().getString(R.string.dialog_permission_no)
                , new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Toast.makeText(MainActivity.this
                                , getResources().getString(R.string.dialog_permission_toast_negative)
                                , Toast.LENGTH_LONG).show();
                    }
                });
        if (dialogPermission == null) {
            dialogPermission = dialogBuilder.create();
        }
        if (!dialogPermission.isShowing()) {
            dialogPermission.show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_ACCESS_FINE_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkBluetoothEnableThenStartScan();
                } else {
                    Toast.makeText(MainActivity.this
                            , getResources().getString(R.string.dialog_permission_toast_negative)
                            , Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    private void checkBluetoothEnableThenStartScan() {
        if (mBluetoothAdapter.isEnabled()) {
            startScanLeDevice();
        } else {
            openBluetoothSetting();
        }
    }

    private void startScanLeDevice() {
        if (isScanning) {
            return;
        }
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScanLeDevice();
            }
        }, SCAN_PERIOD);
        isScanning = true;
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        int reportDelay = 0;
        if (mBluetoothAdapter.isOffloadedScanBatchingSupported()) {
            reportDelay = 1000;
        }
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(reportDelay)
                .build();
        mBluetoothLeScanner.startScan(null, settings, mScanCallback);
        textView_info.setText(getResources().getString(R.string.bt_scanning));
    }

    private void stopScanLeDevice() {
        if (isScanning) {
            mBluetoothLeScanner.stopScan(mScanCallback);
            isScanning = false;
            textView_info.setText(getResources().getString(R.string.bt_stop_scan));
        }
    }

    private void openBluetoothSetting() {
        Intent bluetoothSettingIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(bluetoothSettingIntent, REQUEST_ENABLE_BT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            checkBluetoothEnableThenStartScan();
        }
    }

    public void btnClick(View v) {
        switch (v.getId()) {
            case R.id.button_scan:
                startScanLeDevice();
                break;
            case R.id.button_stop:
                stopScanLeDevice();
                break;
        }
    }
}
