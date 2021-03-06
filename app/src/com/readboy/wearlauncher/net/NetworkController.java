package com.readboy.wearlauncher.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.readboy.wearlauncher.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.android.internal.telephony.cdma.EriInfo;
import com.android.internal.util.AsyncChannel;
import com.android.ims.ImsManager;
import com.android.ims.ImsConfig;

import android.os.SystemProperties;
import android.telephony.SubscriptionManager;
import android.app.readboy.ReadboyWearManager;

import com.android.ims.ImsConnectionStateListener;
import com.android.ims.ImsReasonInfo;
import com.android.ims.ImsServiceClass;
import com.android.ims.ImsException;

public class NetworkController extends BroadcastReceiver {
    // debug
    static final String TAG = "NetworkController";
    static final boolean DEBUG = true;
    static final boolean CHATTY = true; // additional diagnostics, but not logspew

    private static final int FLIGHT_MODE_ICON = R.drawable.stat_sys_signal_flightmode;

    // telephony
    boolean mHspaDataDistinguishable;
    final TelephonyManager mPhone;
    boolean mDataConnected;
    IccCardConstants.State mSimState = IccCardConstants.State.READY;
    int mPhoneState = TelephonyManager.CALL_STATE_IDLE;
    int mDataNetType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    int mDataState = TelephonyManager.DATA_DISCONNECTED;
    int mDataActivity = TelephonyManager.DATA_ACTIVITY_NONE;
    ServiceState mServiceState;
    SignalStrength mSignalStrength;
    int[] mDataIconList = TelephonyIcons.DATA_G[0];
    String mNetworkName;
    String mSimName;
    String mNetworkNameDefault;
    String mNetworkNameSeparator;
    int mPhoneSignalIconId;
    int mQSPhoneSignalIconId;
    int mDataDirectionIconId; // data + data direction on phones
    int mDataSignalIconId;
    int mDataTypeIconId;
    int mQSDataTypeIconId;
    int mAirplaneIconId;
    int mNoSimIconId;
    int mLastSimIconId;
    boolean mDataActive;
    int mLastSignalLevel;
    boolean mNoSim;
    boolean mShowPhoneRSSIForData = false;
    boolean mShowAtLeastThreeGees = false;
    boolean mAlwaysShowCdmaRssi = false;

    String mContentDescriptionPhoneSignal;
    String mContentDescriptionWifi;
    String mContentDescriptionWimax;
    String mContentDescriptionCombinedSignal;
    String mContentDescriptionDataType;

    // wifi
    final WifiManager mWifiManager;
    AsyncChannel mWifiChannel;
    boolean mWifiEnabled, mWifiConnected;
    int mWifiRssi, mWifiLevel;
    String mWifiSsid;
    int mWifiIconId = 0;
    int mQSWifiIconId = 0;
    int mWifiActivity = /*WifiManager.DATA_ACTIVITY_NONE*/0x00;
    int mVolteStatusIcon = 0;
    int mLastVolteStatusIcon = 0;
    // bluetooth
    private boolean mBluetoothTethered = false;
    private int mBluetoothTetherIconId = 0;// R.drawable.stat_sys_tether_bluetooth;

    //wimax
    private boolean mWimaxSupported = false;
    private boolean mIsWimaxEnabled = false;
    private boolean mWimaxConnected = false;
    private boolean mWimaxIdle = false;
    private int mWimaxIconId = 0;
    private int mWimaxSignal = 0;
    private int mWimaxState = 0;
    private int mWimaxExtraState = 0;

    // data connectivity (regardless of state, can we access the internet?)
    // state of inet connection - 0 not connected, 100 connected
    private boolean mConnected = false;
    private int mConnectedNetworkType = ConnectivityManager.TYPE_NONE;
    private String mConnectedNetworkTypeName;
    private int mInetCondition = 0;
    private static final int INET_CONDITION_THRESHOLD = 50;

    private boolean mAirplaneMode = false;
    private boolean mLastAirplaneMode = true;

    private Locale mLocale = null;
    private Locale mLastLocale = null;

    // our ui
    Context mContext;
    ArrayList<TextView> mCombinedLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mMobileLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mWifiLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mEmergencyLabelViews = new ArrayList<TextView>();
    ArrayList<SignalCluster> mSignalClusters = new ArrayList<SignalCluster>();
    ArrayList<NetworkSignalChangedCallback> mSignalsChangedCallbacks =
            new ArrayList<NetworkSignalChangedCallback>();
    int mLastPhoneSignalIconId = -1;
    int mLastDataDirectionIconId = -1;
    int mLastWifiIconId = -1;
    int mLastWimaxIconId = -1;
    int mLastCombinedSignalIconId = -1;
    int mLastDataTypeIconId = -1;
    String mLastCombinedLabel = "";

    private boolean mHasMobileDataFeature;
    boolean mDataAndWifiStacked = false;

    int mImsSubId;
    int mImsRegState = -1;
    boolean mImsRegFlag;
    TelephonyManager mTelephonyManager;
    boolean mIsImsOverWfc;
    private Config mConfig;

    private int mFictitiousMobileSignalIconId = 0;
    private int mLastFictitiousMobileSignalIconId = 0;
    private long mScreenOnTime = 0L;
    private int mDelayTime = 10 * 1000;

    private ReadboyWearManager mRBManager;
    private final ImsManager mImsManager;

    public interface SignalCluster {
        void setWifiIndicators(boolean visible, int strengthIcon,
                               String contentDescription);

        void setMobileDataIndicators(boolean visible, int strengthIcon,
                                     int typeIcon, String contentDescription, String typeContentDescription,
                                     int noSimIcon);

        void setIsAirplaneMode(boolean is, int airplaneIcon);

        void setVolteStatusIcon(int iconId);
    }

    public interface NetworkSignalChangedCallback {
        void onWifiSignalChanged(boolean enabled, int wifiSignalIconId,
                                 boolean activityIn, boolean activityOut,
                                 String wifiSignalContentDescriptionId, String description);

        void onMobileDataSignalChanged(boolean enabled, int mobileSignalIconId,
                                       String mobileSignalContentDescriptionId, int networkType, int dataTypeIconId,
                                       boolean activityIn, boolean activityOut,
                                       String dataTypeContentDescriptionId, String description);

        void onAirplaneModeChanged(boolean enabled);
    }

    /**
     * Construct this controller object and register for updates.
     */
    public NetworkController(Context context) {
        mContext = context;
        final Resources res = context.getResources();
        mConfig = Config.readConfig(context);
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        mRBManager = (ReadboyWearManager) mContext.getSystemService(Context.RBW_SERVICE);
        mHasMobileDataFeature = cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);


        mShowPhoneRSSIForData = res.getBoolean(R.bool.config_showPhoneRSSIForData);
        mShowAtLeastThreeGees = res.getBoolean(R.bool.config_showMin3G);
        mAlwaysShowCdmaRssi = res.getBoolean(R.bool.config_alwaysUseCdmaRssi);

        // set up the default wifi icon, used when no radios have ever appeared
        updateWifiIcons();
        updateWimaxIcons();

        // telephony
        mPhone = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mPhone.listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_SERVICE_STATE
                        | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                        | PhoneStateListener.LISTEN_CALL_STATE
                        | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                        | PhoneStateListener.LISTEN_DATA_ACTIVITY);
        mHspaDataDistinguishable = mContext.getResources().getBoolean(
                R.bool.config_hspa_data_distinguishable);
        mNetworkNameSeparator = mContext.getString(R.string.status_bar_network_name_separator);
        mNetworkNameDefault = mContext.getString(R.string.lockscreen_carrier_default);
        mNetworkName = mNetworkNameDefault;

        mImsManager = ImsManager.getInstance(context, SubscriptionManager.getPhoneId(SubscriptionManager.getDefaultSubscriptionId()));
        try {
            mImsManager.addRegistrationListener(ImsServiceClass.MMTEL, mImsConnectionStateListener);
        } catch (ImsException e) {
            // Could not get the ImsService.
            Log.w(TAG, "could not get the ImsService!");
        }

        // wifi
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        Handler handler = new WifiHandler();
        mWifiChannel = new AsyncChannel();
        Messenger wifiMessenger = mWifiManager.getWifiServiceMessenger();
        if (wifiMessenger != null) {
            mWifiChannel.connect(mContext, handler, wifiMessenger);
        }

        // broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(ConnectivityManager.INET_CONDITION_ACTION);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);

//        filter .addAction(ImsManager.ACTION_IMS_STATE_CHANGED);

        mWimaxSupported = mContext.getResources().getBoolean(R.bool.config_wimaxEnabled);
        if (mWimaxSupported) {
            filter.addAction(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION);
            filter.addAction(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION);
            filter.addAction(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION);
        }

        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        context.registerReceiver(this, filter);

        // AIRPLANE_MODE_CHANGED is sent at boot; we've probably already missed it
        updateAirplaneMode();

        mLastLocale = mContext.getResources().getConfiguration().locale;
    }

    public void unregisterReceiver() {
        mContext.unregisterReceiver(this);
        try {
            mImsManager.removeRegistrationListener(mImsConnectionStateListener);
        } catch (ImsException e) {
            // Could not remove ImsService.
            Log.w(TAG, "could not remove ImsService!");
        }
    }

    public boolean hasMobileDataFeature() {
        return mHasMobileDataFeature;
    }

    public boolean hasVoiceCallingFeature() {
        return mPhone.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
    }

    public boolean isEmergencyOnly() {
        //return  false;
        return (mServiceState != null && mServiceState.isEmergencyOnly());
    }

    public void addCombinedLabelView(TextView v) {
        mCombinedLabelViews.add(v);
    }

    public void addMobileLabelView(TextView v) {
        mMobileLabelViews.add(v);
    }

    public void addWifiLabelView(TextView v) {
        mWifiLabelViews.add(v);
    }

    public void addEmergencyLabelView(TextView v) {
        mEmergencyLabelViews.add(v);
    }

    public void addSignalCluster(SignalCluster cluster) {
        mSignalClusters.add(cluster);
        refreshSignalCluster(cluster);
    }

    public void addNetworkSignalChangedCallback(NetworkSignalChangedCallback cb) {
        mSignalsChangedCallbacks.add(cb);
        notifySignalsChangedCallbacks(cb);
    }

    public void refreshSignalCluster(SignalCluster cluster) {
        cluster.setWifiIndicators(
                // only show wifi in the cluster if connected or if wifi-only
                mWifiEnabled && (mWifiConnected || !mHasMobileDataFeature),
                mWifiIconId,
                mContentDescriptionWifi);
        cluster.setVolteStatusIcon(mVolteStatusIcon);
        int dataTypeIconId = mDataTypeIconId;
        int phoneSignalIconId = mPhoneSignalIconId;
        int dataSignalIconId = mDataSignalIconId;
        if (mNoSimIconId == 0 && mFictitiousMobileSignalIconId != 0/* && Math.abs(System.currentTimeMillis() - mScreenOnTime) <= mDelayTime*/) {
            dataTypeIconId = mFictitiousMobileSignalIconId;
            if (phoneSignalIconId == R.drawable.stat_sys_signal_null) {
                phoneSignalIconId = R.drawable.stat_sys_signal_4;
            }

            if (dataSignalIconId == R.drawable.stat_sys_signal_null) {
                dataSignalIconId = R.drawable.stat_sys_signal_4;
            }
        }
        Log.i(TAG, String.format("dataTypeIconId=%d,mDataTypeIconId=%d,mFictitiousMobileSignalIconId=%d",
                dataTypeIconId, mDataTypeIconId, mFictitiousMobileSignalIconId));
        if (mIsWimaxEnabled && mWimaxConnected) {
            // wimax is special
            cluster.setMobileDataIndicators(
                    true,
                    mAlwaysShowCdmaRssi ? phoneSignalIconId : mWimaxIconId,
                    dataTypeIconId,
                    mContentDescriptionWimax,
                    mContentDescriptionDataType, mNoSimIconId);
        } else {
            // normal mobile data
            cluster.setMobileDataIndicators(
                    mHasMobileDataFeature,
                    mShowPhoneRSSIForData ? phoneSignalIconId : dataSignalIconId,
                    dataTypeIconId,
                    mContentDescriptionPhoneSignal,
                    mContentDescriptionDataType, mNoSimIconId);
        }
        cluster.setIsAirplaneMode(mAirplaneMode, mAirplaneIconId);
    }

    void notifySignalsChangedCallbacks(NetworkSignalChangedCallback cb) {
        // only show wifi in the cluster if connected or if wifi-only
        boolean wifiEnabled = mWifiEnabled && (mWifiConnected || !mHasMobileDataFeature);
        String wifiDesc = wifiEnabled ?
                mWifiSsid : null;
        boolean wifiIn = wifiEnabled && mWifiSsid != null
                && (mWifiActivity == WifiManager.DATA_ACTIVITY_INOUT
                || mWifiActivity == WifiManager.DATA_ACTIVITY_IN);
        boolean wifiOut = wifiEnabled && mWifiSsid != null
                && (mWifiActivity == WifiManager.DATA_ACTIVITY_INOUT
                || mWifiActivity == WifiManager.DATA_ACTIVITY_OUT);
        cb.onWifiSignalChanged(wifiEnabled, mQSWifiIconId, wifiIn, wifiOut,
                mContentDescriptionWifi, wifiDesc);

        boolean mobileIn = mDataConnected && (mDataActivity == TelephonyManager.DATA_ACTIVITY_INOUT
                || mDataActivity == TelephonyManager.DATA_ACTIVITY_IN);
        boolean mobileOut = mDataConnected && (mDataActivity == TelephonyManager.DATA_ACTIVITY_INOUT
                || mDataActivity == TelephonyManager.DATA_ACTIVITY_OUT);
        int networkType = NetworkTypeUtils.getNetworkTypeIcon(mServiceState, mConfig, hasService());
        if (isEmergencyOnly()) {
            cb.onMobileDataSignalChanged(false, mQSPhoneSignalIconId,
                    mContentDescriptionPhoneSignal, networkType, mQSDataTypeIconId, mobileIn, mobileOut,
                    mContentDescriptionDataType, null);
        } else {
            if (mIsWimaxEnabled && mWimaxConnected) {
                // Wimax is special
                cb.onMobileDataSignalChanged(true, mQSPhoneSignalIconId,
                        mContentDescriptionPhoneSignal, networkType, mQSDataTypeIconId, mobileIn, mobileOut,
                        mContentDescriptionDataType, mSimName/*mNetworkName*/);
            } else {
                // Normal mobile data
                cb.onMobileDataSignalChanged(mHasMobileDataFeature, mQSPhoneSignalIconId,
                        mContentDescriptionPhoneSignal, networkType, mQSDataTypeIconId, mobileIn, mobileOut,
                        mContentDescriptionDataType, mSimName/*mNetworkName*/);
            }
        }
        cb.onAirplaneModeChanged(mAirplaneMode);
    }

    public void setStackedMode(boolean stacked) {
        mDataAndWifiStacked = true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(WifiManager.RSSI_CHANGED_ACTION)
                || action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)
                || action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            updateWifiState(intent);
            refreshViews();
        } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
            getSimName(context, 0);
            updateSimState(intent);
            updateSimIcon();
            updateDataIcon();
            refreshViews();
        } else if (action.equals(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION)) {
            updateNetworkName(intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false),
                    intent.getStringExtra(TelephonyIntents.EXTRA_SPN),
                    intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false),
                    intent.getStringExtra(TelephonyIntents.EXTRA_PLMN));
            refreshViews();
        } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION) ||
                action.equals(ConnectivityManager.INET_CONDITION_ACTION)) {
            updateConnectivity(intent);
            refreshViews();
        } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
            refreshLocale();
            refreshViews();
        } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
            refreshLocale();
            updateAirplaneMode();
            updateSimIcon();
            refreshViews();
        } else if (action.equals(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION) ||
                action.equals(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION) ||
                action.equals(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION)) {
            updateWimaxState(intent);
            refreshViews();
//        }else if (action.equals(ImsManager.ACTION_IMS_STATE_CHANGED)) {
//            handleIMSAction(intent);
//            getVolteStatusIcon();
//            refreshViews();
        } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
            mScreenOnTime = System.currentTimeMillis();
            refreshViews();
            mHandler.removeMessages(0x110);
            mHandler.sendEmptyMessageDelayed(0x110, mDelayTime);
        } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
            mHandler.removeMessages(0x110);
            if (mFictitiousMobileSignalIconId == R.drawable.stat_sys_data_fully_connected_4g ||
                    mDataTypeIconId == R.drawable.stat_sys_data_fully_connected_4g) {
                mFictitiousMobileSignalIconId = R.drawable.stat_sys_data_fully_connected_4g;
                refreshViews();
            }
        }
    }

    Handler mHandler = new Handler() {

        @Override
        public void dispatchMessage(Message msg) {
            mFictitiousMobileSignalIconId = 0;
            refreshViews();
        }
    };

    // ===== Telephony ==============================================================

    PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            mSignalStrength = signalStrength;
            updateTelephonySignalStrength();
            refreshViews();
        }

        @Override
        public void onServiceStateChanged(ServiceState state) {
            mServiceState = state;
            mDataNetType =
                    NetworkTypeUtils.getDataNetTypeFromServiceState(mDataNetType, mServiceState);
            getVolteStatusIcon();
            updateTelephonySignalStrength();
            updateDataNetType();
            updateDataIcon();
            refreshViews();
        }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (DEBUG) {
                Log.d(TAG, "onCallStateChanged state=" + state);
            }
            // In cdma, if a voice call is made, RSSI should switch to 1x.
            if (isCdma()) {
                updateTelephonySignalStrength();
                refreshViews();
            }
        }

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            if (DEBUG) {
                Log.d(TAG, "onDataConnectionStateChanged: state=" + state
                        + " type=" + networkType);
            }
            mDataState = state;
            mDataNetType = networkType;
            mDataNetType =
                    NetworkTypeUtils.getDataNetTypeFromServiceState(mDataNetType, mServiceState);
            getVolteStatusIcon();
            updateDataNetType();
            updateDataIcon();
            refreshViews();
        }

        @Override
        public void onDataActivity(int direction) {
            if (DEBUG) {
                Log.d(TAG, "onDataActivity: direction=" + direction);
            }
            mDataActivity = direction;
            updateDataIcon();
            refreshViews();
        }
    };

    private final void updateSimState(Intent intent) {
        String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
            mSimState = IccCardConstants.State.ABSENT;
        } else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
            mSimState = IccCardConstants.State.READY;
        } else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
            final String lockedReason =
                    intent.getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
            if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                mSimState = IccCardConstants.State.PIN_REQUIRED;
            } else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                mSimState = IccCardConstants.State.PUK_REQUIRED;
            } else {
                mSimState = IccCardConstants.State.NETWORK_LOCKED;
            }
        } else {
            mSimState = IccCardConstants.State.UNKNOWN;
        }
    }

    private boolean isCdma() {
        return (mSignalStrength != null) && !mSignalStrength.isGsm();
    }

    /**
     * 该开是否正常，是否可以语音通话？ 要源码编译
     *
     * @return
     */
    private boolean hasService() {
        if (mServiceState != null) {
            // Consider the device to be in service if either voice or data service is available.
            // Some SIM cards are marketed as data-only and do not support voice service, and on
            // these SIM cards, we want to show signal bars for data service as well as the "no
            // service" or "emergency calls only" text that indicates that voice is not available.
            switch (mServiceState.getVoiceRegState()) {
                case ServiceState.STATE_POWER_OFF:
                    return false;
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    return mServiceState.getDataRegState() == ServiceState.STATE_IN_SERVICE;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }

    private void updateAirplaneMode() {
        mAirplaneMode = (Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1);
    }

    private void refreshLocale() {
        mLocale = mContext.getResources().getConfiguration().locale;
    }


    private final void updateTelephonySignalStrength() {
        if (!hasService()) {
            if (CHATTY) Log.d(TAG, "updateTelephonySignalStrength: !hasService()");
            mPhoneSignalIconId = R.drawable.stat_sys_signal_null;
            mQSPhoneSignalIconId = R.drawable.ic_qs_signal_no_signal;
            mDataSignalIconId = R.drawable.stat_sys_signal_null;
        } else {
            if (mSignalStrength == null) {
                if (CHATTY) Log.d(TAG, "updateTelephonySignalStrength: mSignalStrength == null");
                mPhoneSignalIconId = R.drawable.stat_sys_signal_null;
                mQSPhoneSignalIconId = R.drawable.ic_qs_signal_no_signal;
                mDataSignalIconId = R.drawable.stat_sys_signal_null;
                mContentDescriptionPhoneSignal = mContext.getString(
                        AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0]);
            } else {
                int iconLevel = 0;
                int[] iconList;
                if (isCdma() && mAlwaysShowCdmaRssi) {
                    mLastSignalLevel = iconLevel = mSignalStrength.getCdmaLevel();
                } else {
                    mLastSignalLevel = iconLevel = mSignalStrength.getLevel();
                }
                //mLastSignalLevel = iconLevel = mSignalStrength.getLevel();
                if (isCdma()) {
                    if (isCdmaEri()) {
                        iconList = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_ROAMING[mInetCondition];
                    } else {
                        iconList = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH[mInetCondition];
                    }
                } else {
                    // Though mPhone is a Manager, this call is not an IPC
                    if (mPhone.isNetworkRoaming()) {
                        iconList = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_ROAMING[mInetCondition];
                    } else {
                        iconList = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH[mInetCondition];
                    }
                }
                {//add by lzx
                    iconList = TelephonyIcons.SIGNAL_STRENGTH[mInetCondition];
                    iconLevel = iconLevel < iconList.length ? iconLevel : iconList.length - 1;
                }
                mPhoneSignalIconId = iconList[iconLevel];
                mQSPhoneSignalIconId =
                        TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH[mInetCondition][iconLevel];
                mContentDescriptionPhoneSignal = mContext.getString(
                        AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[iconLevel]);
                mDataSignalIconId = TelephonyIcons.DATA_SIGNAL_STRENGTH[mInetCondition][iconLevel];
            }
        }
    }

    private final void updateDataNetType() {
        if (mIsWimaxEnabled && mWimaxConnected) {
            // wimax is a special 4g network not handled by telephony
            mDataIconList = TelephonyIcons.DATA_4G[mInetCondition];
            mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_4g;
            mQSDataTypeIconId = TelephonyIcons.QS_DATA_4G[mInetCondition];
            mContentDescriptionDataType = mContext.getString(
                    R.string.accessibility_data_connection_4g);
        } else if (!hasService() || mDataState != TelephonyManager.DATA_CONNECTED) {
            mDataTypeIconId = 0;
        } else {
            switch (mDataNetType) {
                case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                    if (!mShowAtLeastThreeGees) {
                        mDataIconList = TelephonyIcons.DATA_G[mInetCondition];
                        mDataTypeIconId = 0;
                        mQSDataTypeIconId = 0;
                        mContentDescriptionDataType = mContext.getString(
                                R.string.accessibility_data_connection_gprs);
                        break;
                    } else {
                        // fall through
                    }
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    if (!mShowAtLeastThreeGees) {
                        mDataIconList = TelephonyIcons.DATA_E[mInetCondition];
                        mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_e;
                        mQSDataTypeIconId = TelephonyIcons.QS_DATA_E[mInetCondition];
                        mContentDescriptionDataType = mContext.getString(
                                R.string.accessibility_data_connection_edge);
                        break;
                    } else {
                        // fall through
                    }
                case TelephonyManager.NETWORK_TYPE_UMTS:
                    mDataIconList = TelephonyIcons.DATA_3G[mInetCondition];
                    mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_3g;
                    mQSDataTypeIconId = TelephonyIcons.QS_DATA_3G[mInetCondition];
                    mContentDescriptionDataType = mContext.getString(
                            R.string.accessibility_data_connection_3g);
                    break;
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                    //case TelephonyManager.NETWORK_TYPE_HSPAP:
                    if (mHspaDataDistinguishable) {
                        mDataIconList = TelephonyIcons.DATA_H[mInetCondition];
                        mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_h;
                        mQSDataTypeIconId = TelephonyIcons.QS_DATA_H[mInetCondition];
                        mContentDescriptionDataType = mContext.getString(
                                R.string.accessibility_data_connection_3_5g);
                    } else {
                        mDataIconList = TelephonyIcons.DATA_3G[mInetCondition];
                        mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_3g;
                        mQSDataTypeIconId = TelephonyIcons.QS_DATA_3G[mInetCondition];
                        mContentDescriptionDataType = mContext.getString(
                                R.string.accessibility_data_connection_3g);
                    }
                    break;
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    if (mHspaDataDistinguishable) {
                        mDataIconList = TelephonyIcons.DATA_HP[mInetCondition];
                        mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_hp;
                        mQSDataTypeIconId = TelephonyIcons.QS_DATA_HP[mInetCondition];
                        mContentDescriptionDataType = mContext.getString(
                                R.string.accessibility_data_connection_3_5g);
                    } else {
                        mDataIconList = TelephonyIcons.DATA_3G[mInetCondition];
                        mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_3g;
                        mQSDataTypeIconId = TelephonyIcons.QS_DATA_3G[mInetCondition];
                        mContentDescriptionDataType = mContext.getString(
                                R.string.accessibility_data_connection_3g);
                    }
                    break;
                case TelephonyManager.NETWORK_TYPE_CDMA:
                    if (!mShowAtLeastThreeGees) {
                        // display 1xRTT for IS95A/B
                        mDataIconList = TelephonyIcons.DATA_1X[mInetCondition];
                        mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_1x;
                        mQSDataTypeIconId = TelephonyIcons.QS_DATA_1X[mInetCondition];
                        mContentDescriptionDataType = mContext.getString(
                                R.string.accessibility_data_connection_cdma);
                        break;
                    } else {
                        // fall through
                    }
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                    if (!mShowAtLeastThreeGees) {
                        mDataIconList = TelephonyIcons.DATA_1X[mInetCondition];
                        mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_1x;
                        mQSDataTypeIconId = TelephonyIcons.QS_DATA_1X[mInetCondition];
                        mContentDescriptionDataType = mContext.getString(
                                R.string.accessibility_data_connection_cdma);
                        break;
                    } else {
                        // fall through
                    }
                case TelephonyManager.NETWORK_TYPE_EVDO_0: //fall through
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                    mDataIconList = TelephonyIcons.DATA_3G[mInetCondition];
                    mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_3g;
                    mQSDataTypeIconId = TelephonyIcons.QS_DATA_3G[mInetCondition];
                    mContentDescriptionDataType = mContext.getString(
                            R.string.accessibility_data_connection_3g);
                    break;
                case TelephonyManager.NETWORK_TYPE_LTE:
                    boolean show4GforLTE = mContext.getResources().getBoolean(R.bool.config_show4GForLTE);
                    if (show4GforLTE) {
                        mDataIconList = TelephonyIcons.DATA_4G[mInetCondition];
                        mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_4g;
                        mQSDataTypeIconId = TelephonyIcons.QS_DATA_4G[mInetCondition];
                        mContentDescriptionDataType = mContext.getString(
                                R.string.accessibility_data_connection_4g);
                    } else {
                        mDataIconList = TelephonyIcons.DATA_LTE[mInetCondition];
                        mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_lte;
                        mQSDataTypeIconId = TelephonyIcons.QS_DATA_LTE[mInetCondition];
                        mContentDescriptionDataType = mContext.getString(
                                R.string.accessibility_data_connection_lte);
                    }
                    break;
                default:
                    if (!mShowAtLeastThreeGees) {
                        mDataIconList = TelephonyIcons.DATA_G[mInetCondition];
                        mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_g;
                        mQSDataTypeIconId = TelephonyIcons.QS_DATA_G[mInetCondition];
                        mContentDescriptionDataType = mContext.getString(
                                R.string.accessibility_data_connection_gprs);
                    } else {
                        mDataIconList = TelephonyIcons.DATA_3G[mInetCondition];
                        mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_3g;
                        mQSDataTypeIconId = TelephonyIcons.QS_DATA_3G[mInetCondition];
                        mContentDescriptionDataType = mContext.getString(
                                R.string.accessibility_data_connection_3g);
                    }
                    break;
            }
        }

        if (isCdma()) {
            if (isCdmaEri()) {
                mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
                mQSDataTypeIconId = TelephonyIcons.QS_DATA_R[mInetCondition];
            }
        } else if (mPhone.isNetworkRoaming()) {
            mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
            mQSDataTypeIconId = TelephonyIcons.QS_DATA_R[mInetCondition];
        }
    }

    boolean isCdmaEri() {
        if (mServiceState != null) {
            final int iconIndex = mServiceState.getCdmaEriIconIndex();
            if (iconIndex != EriInfo.ROAMING_INDICATOR_OFF) {
                final int iconMode = mServiceState.getCdmaEriIconMode();
                if (iconMode == EriInfo.ROAMING_ICON_MODE_NORMAL
                        || iconMode == EriInfo.ROAMING_ICON_MODE_FLASH) {
                    return true;
                }
            }
        }
        return false;
    }

    private final void updateSimIcon() {
        if (DEBUG) Log.d(TAG, "In updateSimIcon simState= " + mSimState);
        if (mSimState == IccCardConstants.State.ABSENT) {
            mNoSimIconId = R.drawable.stat_sys_no_sim;
        } else {
            mNoSimIconId = 0;
        }
    }

    private final void updateDataIcon() {
        int iconId;
        boolean visible = true;

        if (!isCdma()) {
            // GSM case, we have to check also the sim state
            if (mSimState == IccCardConstants.State.READY ||
                    mSimState == IccCardConstants.State.UNKNOWN) {
                mNoSim = false;
                if (hasService() && mDataState == TelephonyManager.DATA_CONNECTED) {
                    switch (mDataActivity) {
                        case TelephonyManager.DATA_ACTIVITY_IN:
                            iconId = mDataIconList[1];
                            break;
                        case TelephonyManager.DATA_ACTIVITY_OUT:
                            iconId = mDataIconList[2];
                            break;
                        case TelephonyManager.DATA_ACTIVITY_INOUT:
                            iconId = mDataIconList[3];
                            break;
                        default:
                            iconId = mDataIconList[0];
                            break;
                    }
                    mDataDirectionIconId = iconId;
                } else {
                    iconId = 0;
                    visible = false;
                }
            } else {
                iconId = R.drawable.stat_sys_no_sim;
                mNoSim = true;
                visible = false; // no SIM? no data
            }
        } else {
            // CDMA case, mDataActivity can be also DATA_ACTIVITY_DORMANT
            if (hasService() && mDataState == TelephonyManager.DATA_CONNECTED) {
                switch (mDataActivity) {
                    case TelephonyManager.DATA_ACTIVITY_IN:
                        iconId = mDataIconList[1];
                        break;
                    case TelephonyManager.DATA_ACTIVITY_OUT:
                        iconId = mDataIconList[2];
                        break;
                    case TelephonyManager.DATA_ACTIVITY_INOUT:
                        iconId = mDataIconList[3];
                        break;
                    case TelephonyManager.DATA_ACTIVITY_DORMANT:
                    default:
                        iconId = mDataIconList[0];
                        break;
                }
            } else {
                iconId = 0;
                visible = false;
            }
        }

        mDataDirectionIconId = iconId;
        mDataConnected = visible;
    }

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn) {
        if (true) {
            Log.d("CarrierLabel", "updateNetworkName showSpn=" + showSpn + " spn=" + spn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn);
        }
        StringBuilder str = new StringBuilder();
        boolean something = false;
        if (showPlmn && plmn != null) {
            str.append(plmn);
            something = true;
        }
        if (showSpn && spn != null) {
            if (something) {
                str.append(mNetworkNameSeparator);
            }
            str.append(spn);
            something = true;
        }
        if (something) {
            mNetworkName = str.toString();
        } else {
            mNetworkName = mNetworkNameDefault;
        }
    }

    public String getSimName(Context context, int slotId) {
        mSimName = null;
        try {
            SubscriptionManager mSubscriptionManager = SubscriptionManager.from(context);
            SubscriptionInfo sir = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slotId);
            if (sir != null) {
                String operator = String.format("%03d%02d", sir.getMcc(), sir.getMnc());
                if (!TextUtils.isEmpty(operator)) {
                    if (operator.equals("46000") || operator.equals("46002") || operator.equals("46004") ||
                            operator.equals("46007") || operator.equals("46008") || operator.equals("46020")
                            || operator.equals("41004")) {
                        mSimName = context.getResources().getString(R.string.sim_name_mobile);
                    } else if (operator.equals("46001") || operator.equals("46006") || operator.equals("46009")
                            || operator.equals("46010")) {
                        mSimName = context.getResources().getString(R.string.sim_name_unicom);
                    } else if (operator.equals("46003") || operator.equals("46005") || operator.equals("46011")) {
                        mSimName = context.getResources().getString(R.string.sim_name_telecom);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return mSimName;
    }

    public int getVolteIconId(int slotId) {
//        final TelephonyManager tm =
//                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
//        int slotCount = tm.getSimCount();
//        int iconId = R.drawable.stat_sys_volte;
//        if (slotCount > 1 && slotId < NetworkTypeUtils.VOLTEICON.length) {
//            iconId = NetworkTypeUtils.VOLTEICON[slotId];
//        }
        int iconId = R.drawable.stat_sys_hd;
        if (mRBManager.getPersonalInfo() != null) {
            int volteSwitchStatus = mRBManager.getPersonalInfo().getVolte();
            if (volteSwitchStatus == 0) {
                return 0;
            } else if (volteSwitchStatus == 1) {
                return iconId;
            }
        }
        return 0;
    }

    private boolean isImsOverWfc(Intent intent) {
//        boolean[] enabledFeatures =
//                intent.getBooleanArrayExtra(ImsManager.EXTRA_IMS_ENABLE_CAP_KEY);
        boolean wfcCapabilities = false;
//        if (enabledFeatures != null && (enabledFeatures.length > 1)) {
        //Check if voice over wifi capability is available
//            wfcCapabilities =
//                    (enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI] == true);
//        }
//        Log.d(TAG,"wfcCapabilities = " + wfcCapabilities);
        return wfcCapabilities;
    }

    public int getWfcIconId(int slotId) {
        final TelephonyManager tm =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        int slotCount = tm.getSimCount();
        int iconId = R.drawable.stat_sys_wfc;
        if (slotCount > 1 && slotId < NetworkTypeUtils.WFCICON.length) {
            iconId = NetworkTypeUtils.WFCICON[slotId];
        }
        return iconId;
    }

    public boolean isLteNetWork() {
        return (mDataNetType == TelephonyManager.NETWORK_TYPE_LTE);
//                || mDataNetType == TelephonyManager.NETWORK_TYPE_LTEA);
    }

    /// M: Support [Volte icon status]
    private void handleIMSAction(Intent intent) {
//        mImsRegState = intent.getIntExtra(ImsManager.EXTRA_IMS_REG_STATE_KEY,
//                ServiceState.STATE_OUT_OF_SERVICE);
        int phoneId = intent.getIntExtra(ImsManager.EXTRA_PHONE_ID,
                SubscriptionManager.INVALID_PHONE_INDEX);
//        mImsSubId = SubscriptionManager.getSubIdUsingPhoneId(phoneId);
        Log.d(TAG, "handleIMSAction mImsRegState = " + mImsRegState + " phoneId = " + phoneId
                + "mImsSubId = " + mImsSubId);
        int iconId = 0;
        if (isImsOverWfc(intent) == true) {
            //If IMS is registered over WFC, no need to show volte icon
            iconId = 0;
            mIsImsOverWfc = true;
            Log.d(TAG, "WFC reset ims register state and remove volte icon");
            //check if the load is dsds
            if ((SystemProperties.get("persist.radio.multisim.config", "ss").
                    equals("dsds"))) {
                iconId = getWfcIconId(phoneId);
            }
            Log.d(TAG, "Set IMS regState with iconId = " + iconId);
        } else {
            iconId = mImsRegState == ServiceState.STATE_IN_SERVICE &&
                    isLteNetWork() ?
                    getVolteIconId(phoneId) : 0;
            mIsImsOverWfc = false;
            Log.d(TAG, "Set IMS regState with iconId = " + iconId);
        }
        // In case hot plug, so always to refresh icon state
        mVolteStatusIcon = iconId;
    }

    public void getVolteStatusIcon() {
        int phoneId = 0;
        if (mImsRegState == ServiceState.STATE_IN_SERVICE && isLteNetWork()) {
            if (mImsRegFlag) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mImsRegState == ServiceState.STATE_IN_SERVICE && isLteNetWork()) {
                            mVolteStatusIcon = getVolteIconId(0);
                        } else {
                            mVolteStatusIcon = 0;
                        }
                        for (SignalCluster cluster : mSignalClusters) {
                            cluster.setVolteStatusIcon(mVolteStatusIcon);
                        }
                        mImsRegFlag = false;
                    }
                }, 1000);
            } else {
                mVolteStatusIcon = getVolteIconId(0);
            }
        } else {
            mVolteStatusIcon = 0;
        }
    }

    /**
     * Listen to the IMS service state change
     */
    ImsConnectionStateListener mImsConnectionStateListener =
            new ImsConnectionStateListener() {
                @Override
                public void onImsConnected(int imsRadioTech) {
                    mImsRegState = ServiceState.STATE_IN_SERVICE;
                    mImsRegFlag = true;
                }

                @Override
                public void onImsDisconnected(ImsReasonInfo imsReasonInfo) {
                    mImsRegState = ServiceState.STATE_OUT_OF_SERVICE;
                }

                @Override
                public void onFeatureCapabilityChanged(int serviceClass,
                                                       int[] enabledFeatures, int[] disabledFeatures) {
                }
            };

// ===== Wifi ===================================================================

    class WifiHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                    if (msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                        mWifiChannel.sendMessage(Message.obtain(this,
                                AsyncChannel.CMD_CHANNEL_FULL_CONNECTION));
                    } else {
                        Log.e(TAG, "Failed to connect to wifi");
                    }
                    break;
                case WifiManager.DATA_ACTIVITY_NOTIFICATION:
                    if (msg.arg1 != mWifiActivity) {
                        mWifiActivity = msg.arg1;
                        refreshViews();
                    }
                    break;
                default:
                    //Ignore
                    break;
            }
        }
    }

    private void updateWifiState(Intent intent) {
        final String action = intent.getAction();
        if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            mWifiEnabled = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN) == WifiManager.WIFI_STATE_ENABLED;

        } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            final NetworkInfo networkInfo = (NetworkInfo)
                    intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            boolean wasConnected = mWifiConnected;
            mWifiConnected = networkInfo != null && networkInfo.isConnected();
            // If we just connected, grab the inintial signal strength and ssid
            if (mWifiConnected && !wasConnected) {
                // try getting it out of the intent first
                WifiInfo info = (WifiInfo) intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                if (info == null) {
                    info = mWifiManager.getConnectionInfo();
                }
                if (info != null) {
                    mWifiSsid = huntForSsid(info);
                } else {
                    mWifiSsid = null;
                }
            } else if (!mWifiConnected) {
                mWifiSsid = null;
            }
        } else if (action.equals(WifiManager.RSSI_CHANGED_ACTION)) {
            mWifiRssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, -200);
            mWifiLevel = WifiManager.calculateSignalLevel(
                    mWifiRssi, WifiIcons.WIFI_LEVEL_COUNT);
        }

        updateWifiIcons();
    }

    private void updateWifiIcons() {
        if (mWifiConnected) {
            mWifiIconId = WifiIcons.WIFI_SIGNAL_STRENGTH[mInetCondition][mWifiLevel];
            mQSWifiIconId = WifiIcons.QS_WIFI_SIGNAL_STRENGTH[mInetCondition][mWifiLevel];
            mContentDescriptionWifi = mContext.getString(
                    AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH[mWifiLevel]);
        } else {
            if (mDataAndWifiStacked) {
                mWifiIconId = 0;
                mQSWifiIconId = 0;
            } else {
                mWifiIconId = mWifiEnabled ? R.drawable.stat_sys_wifi_signal_null : 0;
                mQSWifiIconId = mWifiEnabled ? R.drawable.ic_qs_wifi_no_network : 0;
            }
            mContentDescriptionWifi = mContext.getString(R.string.accessibility_no_wifi);
        }
    }

    private String huntForSsid(WifiInfo info) {
        String ssid = info.getSSID();
        if (ssid != null) {
            return ssid;
        }
        // OK, it's not in the connectionInfo; we have to go hunting for it
        List<WifiConfiguration> networks = mWifiManager.getConfiguredNetworks();
        for (WifiConfiguration net : networks) {
            if (net.networkId == info.getNetworkId()) {
                return net.SSID;
            }
        }
        return null;
    }


    // ===== Wimax ===================================================================
    private final void updateWimaxState(Intent intent) {
        final String action = intent.getAction();
        boolean wasConnected = mWimaxConnected;
        if (action.equals(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION)) {
            int wimaxStatus = intent.getIntExtra(WimaxManagerConstants.EXTRA_4G_STATE,
                    WimaxManagerConstants.NET_4G_STATE_UNKNOWN);
            mIsWimaxEnabled = (wimaxStatus ==
                    WimaxManagerConstants.NET_4G_STATE_ENABLED);
        } else if (action.equals(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION)) {
            mWimaxSignal = intent.getIntExtra(WimaxManagerConstants.EXTRA_NEW_SIGNAL_LEVEL, 0);
        } else if (action.equals(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION)) {
            mWimaxState = intent.getIntExtra(WimaxManagerConstants.EXTRA_WIMAX_STATE,
                    WimaxManagerConstants.NET_4G_STATE_UNKNOWN);
            mWimaxExtraState = intent.getIntExtra(
                    WimaxManagerConstants.EXTRA_WIMAX_STATE_DETAIL,
                    WimaxManagerConstants.NET_4G_STATE_UNKNOWN);
            mWimaxConnected = (mWimaxState ==
                    WimaxManagerConstants.WIMAX_STATE_CONNECTED);
            mWimaxIdle = (mWimaxExtraState == WimaxManagerConstants.WIMAX_IDLE);
        }
        updateDataNetType();
        updateWimaxIcons();
    }

    private void updateWimaxIcons() {
        if (mIsWimaxEnabled) {
            if (mWimaxConnected) {
                if (mWimaxIdle)
                    mWimaxIconId = WimaxIcons.WIMAX_IDLE;
                else
                    mWimaxIconId = WimaxIcons.WIMAX_SIGNAL_STRENGTH[mInetCondition][mWimaxSignal];
                mContentDescriptionWimax = mContext.getString(
                        AccessibilityContentDescriptions.WIMAX_CONNECTION_STRENGTH[mWimaxSignal]);
            } else {
                mWimaxIconId = WimaxIcons.WIMAX_DISCONNECTED;
                mContentDescriptionWimax = mContext.getString(R.string.accessibility_no_wimax);
            }
        } else {
            mWimaxIconId = 0;
        }
    }

    // ===== Full or limited Internet connectivity ==================================

    private void updateConnectivity(Intent intent) {
        if (CHATTY) {
            Log.d(TAG, "updateConnectivity: intent=" + intent);
        }

        final ConnectivityManager connManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo info = connManager.getActiveNetworkInfo();

        // Are we connected at all, by any interface?
        mConnected = info != null && info.isConnected();
        if (mConnected) {
            mConnectedNetworkType = info.getType();
            mConnectedNetworkTypeName = info.getTypeName();
        } else {
            mConnectedNetworkType = ConnectivityManager.TYPE_NONE;
            mConnectedNetworkTypeName = null;
        }

        int connectionStatus = intent.getIntExtra(ConnectivityManager.EXTRA_INET_CONDITION, 0);

        if (CHATTY) {
            Log.d(TAG, "updateConnectivity: mConnectedNetworkType=" + mConnectedNetworkType);
            Log.d(TAG, "updateConnectivity: mConnectedNetworkTypeName=" + mConnectedNetworkTypeName);
            Log.d(TAG, "updateConnectivity: networkInfo=" + info);
            Log.d(TAG, "updateConnectivity: connectionStatus=" + connectionStatus);
        }

        mInetCondition = (connectionStatus > INET_CONDITION_THRESHOLD ? 1 : 0);

        if (info != null && info.getType() == ConnectivityManager.TYPE_BLUETOOTH) {
            mBluetoothTethered = info.isConnected();
        } else {
            mBluetoothTethered = false;
        }

        // We want to update all the icons, all at once, for any condition change
        updateDataNetType();
        updateWimaxIcons();
        updateDataIcon();
        updateTelephonySignalStrength();
        updateWifiIcons();
    }

    public void fireCallbacks() {
        refreshViews();
    }

    public static class Config {
        public boolean showAtLeast3G = false;
        public boolean alwaysShowCdmaRssi = false;
        public boolean show4gForLte = false;
        public boolean hspaDataDistinguishable;

        static Config readConfig(Context context) {
            Config config = new Config();
            Resources res = context.getResources();

            config.showAtLeast3G = res.getBoolean(R.bool.config_showMin3G);
            config.alwaysShowCdmaRssi =
                    res.getBoolean(com.android.internal.R.bool.config_alwaysUseCdmaRssi);
            config.show4gForLte = res.getBoolean(R.bool.config_show4GForLTE);
            config.hspaDataDistinguishable =
                    res.getBoolean(R.bool.config_hspa_data_distinguishable);
            return config;
        }

    }

    // ===== Update the views =======================================================

    void refreshViews() {
        Context context = mContext;

        int combinedSignalIconId = 0;
        String combinedLabel = "";
        String wifiLabel = "";
        String mobileLabel = "";
        int N;
        final boolean emergencyOnly = isEmergencyOnly();

        if (!mHasMobileDataFeature) {
            mDataSignalIconId = mPhoneSignalIconId = 0;
            mQSPhoneSignalIconId = 0;
            mobileLabel = "";
        } else {
            // We want to show the carrier name if in service and either:
            //   - We are connected to mobile data, or
            //   - We are not connected to mobile data, as long as the *reason* packets are not
            //     being routed over that link is that we have better connectivity via wifi.
            // If data is disconnected for some other reason but wifi (or ethernet/bluetooth)
            // is connected, we show nothing.
            // Otherwise (nothing connected) we show "No internet connection".

            if (mDataConnected) {
                mobileLabel = mNetworkName;
            } else if (mConnected || emergencyOnly) {
                if (hasService() || emergencyOnly) {
                    // The isEmergencyOnly test covers the case of a phone with no SIM
                    mobileLabel = mNetworkName;
                } else {
                    // Tablets, basically
                    mobileLabel = "";
                }
            } else {
                if (mNetworkName != null && mNetworkName.length() != 0) {
                    mobileLabel = mNetworkName;
                } else {
                    mobileLabel
                            = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
                }
            }

            // Now for things that should only be shown when actually using mobile data.
            if (mDataConnected) {
                combinedSignalIconId = mDataSignalIconId;

                combinedLabel = mobileLabel;
                combinedSignalIconId = mDataSignalIconId; // set by updateDataIcon()
                mContentDescriptionCombinedSignal = mContentDescriptionDataType;
            }
        }

        if (mWifiConnected) {
            if (mWifiSsid == null) {
                wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_wifi_nossid);
            } else {
                wifiLabel = mWifiSsid;
                if (DEBUG) {
                    wifiLabel += "xxxxXXXXxxxxXXXX";
                }
            }

            combinedLabel = wifiLabel;
            combinedSignalIconId = mWifiIconId; // set by updateWifiIcons()
            mContentDescriptionCombinedSignal = mContentDescriptionWifi;
        } else {
            if (mHasMobileDataFeature) {
                wifiLabel = "";
            } else {
                wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            }
        }

        if (mBluetoothTethered) {
            combinedLabel = mContext.getString(R.string.bluetooth_tethered);
            combinedSignalIconId = mBluetoothTetherIconId;
            mContentDescriptionCombinedSignal = mContext.getString(
                    R.string.accessibility_bluetooth_tether);
        }

        final boolean ethernetConnected = (mConnectedNetworkType == ConnectivityManager.TYPE_ETHERNET);
        if (ethernetConnected) {
            combinedLabel = context.getString(R.string.ethernet_label);
        }

        if (mAirplaneMode &&
                (mServiceState == null || (!hasService() /*&& !mServiceState.isEmergencyOnly()*/))) {
            // Only display the flight-mode icon if not in "emergency calls only" mode.

            // look again; your radios are now airplanes
            mContentDescriptionPhoneSignal = mContext.getString(
                    R.string.accessibility_airplane_mode);
            mAirplaneIconId = FLIGHT_MODE_ICON;
            mPhoneSignalIconId = mDataSignalIconId = mDataTypeIconId = mQSDataTypeIconId = 0;
            mQSPhoneSignalIconId = 0;

            // combined values from connected wifi take precedence over airplane mode
            if (mWifiConnected) {
                // Suppress "No internet connection." from mobile if wifi connected.
                mobileLabel = "";
            } else {
                if (mHasMobileDataFeature) {
                    // let the mobile icon show "No internet connection."
                    wifiLabel = "";
                } else {
                    wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
                    combinedLabel = wifiLabel;
                }
                mContentDescriptionCombinedSignal = mContentDescriptionPhoneSignal;
                combinedSignalIconId = mDataSignalIconId;
            }
        } else if (!mDataConnected && !mWifiConnected && !mBluetoothTethered && !mWimaxConnected && !ethernetConnected) {
            // pretty much totally disconnected

            combinedLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            // On devices without mobile radios, we want to show the wifi icon
            combinedSignalIconId =
                    mHasMobileDataFeature ? mDataSignalIconId : mWifiIconId;
            mContentDescriptionCombinedSignal = mHasMobileDataFeature
                    ? mContentDescriptionDataType : mContentDescriptionWifi;

            mDataTypeIconId = 0;
            mQSDataTypeIconId = 0;
            if (isCdma()) {
                if (isCdmaEri()) {
                    mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
                    mQSDataTypeIconId = TelephonyIcons.QS_DATA_R[mInetCondition];
                }
            } else if (mPhone.isNetworkRoaming()) {
                mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
                mQSDataTypeIconId = TelephonyIcons.QS_DATA_R[mInetCondition];
            }
        }

        // update QS
        for (NetworkSignalChangedCallback cb : mSignalsChangedCallbacks) {
            notifySignalsChangedCallbacks(cb);
        }

        getVolteStatusIcon();

        if (mLastPhoneSignalIconId != mPhoneSignalIconId
                || mLastWifiIconId != mWifiIconId
                || mLastWimaxIconId != mWimaxIconId
                || mLastDataTypeIconId != mDataTypeIconId
                || mFictitiousMobileSignalIconId != mLastFictitiousMobileSignalIconId
                || mLastAirplaneMode != mAirplaneMode
                || mLastSimIconId != mNoSimIconId
                || mLastLocale != mLocale
                || mLastVolteStatusIcon != mVolteStatusIcon) {
            // NB: the mLast*s will be updated later
            for (SignalCluster cluster : mSignalClusters) {
                refreshSignalCluster(cluster);
            }
        }

        if (mFictitiousMobileSignalIconId != mLastFictitiousMobileSignalIconId) {
            mLastFictitiousMobileSignalIconId = mFictitiousMobileSignalIconId;
        }

        if (mLastAirplaneMode != mAirplaneMode) {
            mLastAirplaneMode = mAirplaneMode;
        }

        if (mLastLocale != mLocale) {
            mLastLocale = mLocale;
        }

        // the phone icon on phones
        if (mLastPhoneSignalIconId != mPhoneSignalIconId) {
            mLastPhoneSignalIconId = mPhoneSignalIconId;
        }

        // the data icon on phones
        if (mLastDataDirectionIconId != mDataDirectionIconId) {
            mLastDataDirectionIconId = mDataDirectionIconId;
        }

        // the wifi icon on phones
        if (mLastWifiIconId != mWifiIconId) {
            mLastWifiIconId = mWifiIconId;
        }

        // the wimax icon on phones
        if (mLastWimaxIconId != mWimaxIconId) {
            mLastWimaxIconId = mWimaxIconId;
        }
        // the combined data signal icon
        if (mLastCombinedSignalIconId != combinedSignalIconId) {
            mLastCombinedSignalIconId = combinedSignalIconId;
        }

        // the data network type overlay
        if (mLastDataTypeIconId != mDataTypeIconId) {
            mLastDataTypeIconId = mDataTypeIconId;
        }

        if (mLastSimIconId != mNoSimIconId) {
            mLastSimIconId = mNoSimIconId;
        }

        if (mLastVolteStatusIcon != mVolteStatusIcon) {
            mLastVolteStatusIcon = mVolteStatusIcon;
        }
        // the combinedLabel in the notification panel
        if (!mLastCombinedLabel.equals(combinedLabel)) {
            mLastCombinedLabel = combinedLabel;
            N = mCombinedLabelViews.size();
            for (int i = 0; i < N; i++) {
                TextView v = mCombinedLabelViews.get(i);
                v.setText(combinedLabel);
            }
        }


        // wifi label
        N = mWifiLabelViews.size();
        for (int i = 0; i < N; i++) {
            TextView v = mWifiLabelViews.get(i);
            v.setText(wifiLabel);
            if ("".equals(wifiLabel)) {
                v.setVisibility(View.GONE);
            } else {
                v.setVisibility(View.VISIBLE);
            }
        }

        // mobile label
        N = mMobileLabelViews.size();
        for (int i = 0; i < N; i++) {
            TextView v = mMobileLabelViews.get(i);
            v.setText(mobileLabel);
            if ("".equals(mobileLabel)) {
                v.setVisibility(View.GONE);
            } else {
                v.setVisibility(View.VISIBLE);
            }
        }
        if (N > 0) {
            String string = "combinedLabel:" + combinedLabel + ",wifiLabel:" + wifiLabel + ",mobileLabel:" + mobileLabel + ",mobileLabel:" + mobileLabel;
            TextView textView = mMobileLabelViews.get(0);
            textView.setText(string);
            textView.setVisibility(View.VISIBLE);
        }

        // e-call label
        N = mEmergencyLabelViews.size();
        for (int i = 0; i < N; i++) {
            TextView v = mEmergencyLabelViews.get(i);
            if (!emergencyOnly) {
                v.setVisibility(View.GONE);
            } else {
                v.setText(mobileLabel); // comes from the telephony stack
                v.setVisibility(View.VISIBLE);
            }
        }

    }

    private int getImsEnableCap(int[] enabledFeatures) {
        int cap = ImsConfig.FeatureConstants.FEATURE_TYPE_UNKNOWN;
        if (enabledFeatures != null) {
            if (enabledFeatures[
                    ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI]
                    == ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI) {
                cap = ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI;
            } else if (enabledFeatures[
                    ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE]
                    == ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE
                    ) {
                cap = ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE;
            }
        }
        return cap;
    }

}
