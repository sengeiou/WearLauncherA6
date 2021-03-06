package com.readboy.wearlauncher.utils;

//add by lxx 2019/1/11

import android.app.Service;
import android.app.readboy.PersonalInfo;
import android.app.readboy.ReadboyWearManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.os.IBinder;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class StartFactoryModeService extends Service {
    private WifiManager wm;
    IntentFilter intentFilter;
    List<ScanResult> scanResults = new ArrayList<ScanResult>();
    private boolean isLocationOpen;
    private boolean isWifiOpened;
    public static final String WIFI_NAME = "readboy-factory-watch-test1";
//    public static final String WIFI_NAME = "SoftReadboy2";

    public static final String TAG = "StartFactoryModeService";

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "onCreate()");
        //判断是否开启位置权限，android 8.0需要开启位置，wifiManager才能正常工作
        LocationManager locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        boolean networkProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        boolean gpsProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!networkProvider && !gpsProvider) {
            Log.e(TAG, "开启位置");
            isLocationOpen = false;
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.LOCATION_MODE, 1);  //开启位置
        } else {
            isLocationOpen = true;
        }

        wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        this.registerReceiver(wifiReceiver, intentFilter);
        if (wm.getWifiState() != WifiManager.WIFI_STATE_ENABLED) {
            isWifiOpened = false;
            if (wm.getWifiState() != WifiManager.WIFI_STATE_DISABLING) {
                wm.setWifiEnabled(true);
                Log.e(TAG, "开启wifi");
            }
        } else if (wm.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
            isWifiOpened = true;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "onDestroy()");
        if (wm.isWifiEnabled() && !isWifiOpened) {
            wm.setWifiEnabled(false);
            Log.e(TAG, "关闭wifi");

        }
        if (!isLocationOpen) {
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.LOCATION_MODE, 0);  //关闭位置
        }
        unregisterReceiver(wifiReceiver);
    }

    private BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                if (wm.isWifiEnabled()) {
                    wm.startScan();
                }
            }
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.endsWith(action)) {
                scanResults = wm.getScanResults();
                startFactoryMode(checkWifi());
            }
        }
    };

    private boolean checkWifi() {
        for (ScanResult scanResult : scanResults) {
            if (WIFI_NAME.equals(scanResult.SSID)) {
                return true;
            }
        }
        return false;
    }

    private void startFactoryMode(boolean hasTheWifi) {
        if (!hasTheWifi) {
            Log.e(TAG, "Not found the wifi: " + WIFI_NAME);
            stopSelf();
        } else {
            Log.e(TAG, "Start FactoryMode!");
            Intent intent = new Intent();
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ComponentName componentName = new ComponentName("com.mediatek.factorymode", "com.mediatek.factorymode.FactoryMode");
            intent.setComponent(componentName);
            startActivity(intent);
            stopSelf();
        }
    }

}

