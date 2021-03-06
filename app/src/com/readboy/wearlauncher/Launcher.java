package com.readboy.wearlauncher;

import android.Manifest;
import android.app.ActivityManager;
import android.app.readboy.PersonalInfo;
import android.app.readboy.ReadboyWearManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.IPowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.readboy.wearlauncher.contact.ContactsView;
import com.readboy.wearlauncher.application.AppInfo;
import com.readboy.wearlauncher.application.AppsLoader;
import com.readboy.wearlauncher.battery.BatteryController;
import com.readboy.wearlauncher.dialog.ClassDisableDialog;
import com.readboy.wearlauncher.dialog.InstructionsDialog;
import com.readboy.wearlauncher.notification.NotificationActivity;
import com.readboy.wearlauncher.utils.ClassForbidUtils;
import com.readboy.wearlauncher.utils.StartFactoryModeService;
import com.readboy.wearlauncher.utils.Utils;
import com.readboy.wearlauncher.utils.WatchController;
import com.readboy.wearlauncher.view.CameraView;
import com.readboy.wearlauncher.view.DaialParentLayout;
import com.readboy.wearlauncher.view.DialBaseLayout;
import com.readboy.wearlauncher.view.GestureView;
import com.readboy.wearlauncher.view.MyViewPager;
import com.readboy.wearlauncher.view.NegativeScreen;
import com.readboy.wearlauncher.view.WatchAppView;
import com.readboy.wearlauncher.view.WatchDials;
import com.readboy.wetalk.view.WetalkFrameLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import me.everything.android.ui.overscroll.OverScrollDecoratorHelper;


public class Launcher extends FragmentActivity implements BatteryController.BatteryStateChangeCallback,
        GestureView.MyGestureListener, WatchAppView.OnItemClickListener, LoaderManager.LoaderCallbacks<ArrayList<AppInfo>>, WatchController.ClassDisableChangedCallback,
        WatchController.ScreenOff {
    public static final String TAG = Launcher.class.getSimpleName();

    private LauncherApplication mApplication;
    private FragmentManager mFragmentManager;
    private static final int PERMISSIONS_REQUEST_CODE = 0x33;
    private static final int LOADER_ID = 0x10;
    public static final int POSITION_MAIN_PAGE = 1;  //表盘屏的位置
    public static final String ACTION_CLASS_DISABLE_STATUS_CHANGED = "android.intent.action.CLASS_DISABLE_STATUS_CHANGED";
    private static final int WAIT_IRWS = 0;
    private LocalBroadcastManager mLocalBroadcastManager;

    private GestureView mGestureView;
    DialBaseLayout mLowDialBaseLayout;
    private LayoutInflater mInflater;
    private MyViewPager mViewpager;
    private ViewPagerAdpater mViewPagerAdpater;
    private List<View> mViewList = new ArrayList<View>();
    //    private NegativeScreen mNegativeView;
    private ContactsView mContactsView;
    private DaialParentLayout mDaialView;
    private WetalkFrameLayout mWetalkView;
    private CameraView mCameraView;
    private WatchAppView mAppView;
    private WatchDials mWatchDials;
    int mTouchSlopSquare;
    int mViewPagerScrollState = ViewPager.SCROLL_STATE_IDLE;
    private int mWatchType;
    private Toast mToast;

    WatchController mWatchController;
    BatteryController mBatteryController;
    private int mBatteryLevel = -1;
    private int mLastPosition = -1;
    private boolean bIsPaused = false;
    private boolean bIsClassDisable = false;
    private boolean bIsTouchable = false;

    private TelephonyManager mTelephonyManager;
    private ReadboyWearManager rwm;
    private static final String[] sPermissions = {
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "onCreate()");
        setContentView(R.layout.activity_launcher);
        //screen width:240、height:240,density:0.75,densityDpi:120

        mViewpager = (MyViewPager) findViewById(R.id.viewpager);
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        mFragmentManager = getSupportFragmentManager();
        ViewConfiguration configuration = ViewConfiguration.get(Launcher.this);
        int touchSlop = configuration.getScaledTouchSlop();
        mTouchSlopSquare = touchSlop * 20;

        mApplication = (LauncherApplication) getApplication();
        mInflater = LayoutInflater.from(this);
        mWatchType = LauncherSharedPrefs.getWatchType(this);
        mBatteryController = new BatteryController(this);
        mBatteryController.addStateChangedCallback(this);

        mWatchController = mApplication.getWatchController();
        mWatchController.addClassDisableChangedCallback(this);

        mGestureView = (GestureView) findViewById(R.id.content_container);
        mGestureView.setGestureListener(this);
        mLowDialBaseLayout = (DialBaseLayout) findViewById(R.id.low);
        mLowDialBaseLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (mToast == null) {
                        mToast = Toast.makeText(Launcher.this, R.string.notice_low_power_for_phone, Toast.LENGTH_SHORT);
                        mToast.setGravity(Gravity.CENTER, 0, 0);
                        TextView textView = (TextView) mToast.getView().findViewById(android.R.id.message);
                        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                    }
                    mToast.setText(R.string.notice_low_power_for_phone);
                    mToast.show();
                }
                return true;
            }
        });
//        mNegativeView = (NegativeScreen) mInflater.inflate(R.layout.negative_screen, null);
        mContactsView = (ContactsView) mInflater.inflate(R.layout.contact_list, null);
        mCameraView = (CameraView) mInflater.inflate(R.layout.camera_layout, null);
        mDaialView = (DaialParentLayout) mInflater.inflate(R.layout.watch_dial_layout, null);
        mDaialView.removeAllViews();
        DialBaseLayout childDaialView = (DialBaseLayout) mInflater.inflate(WatchDials.mDialList.get(mWatchType % WatchDials.mDialList.size()), mDaialView, false);
        childDaialView.addChangedCallback();
        childDaialView.onResume();
        childDaialView.setButtonEnable();
        mDaialView.addView(childDaialView);
        mAppView = (WatchAppView) mInflater.inflate(R.layout.watch_app_recyclerview, null);
        mAppView.addHeader(setTitle(getString(R.string.apps_title)));
        mAppView.setOnItemClickListener(this);
        mViewList.clear();
//        mViewList.add(mNegativeView);
        mContactsView.addHeader(setTitle(getString(R.string.contact_title)));
        mViewList.add(mContactsView);
        mViewList.add(mDaialView);
        mWetalkView = new WetalkFrameLayout(this);
        mWetalkView.setActivity(this);
        mWetalkView.addHeader(setTitle(getString(R.string.wetalk_title)));
        OverScrollDecoratorHelper.setUpOverScroll(mWetalkView.getRecyclerView(), 0);
        mViewList.add(mWetalkView);
        mViewList.add(mCameraView);
        mViewList.add(mAppView);
        loadApps(false);
        mViewpager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                if (position == POSITION_MAIN_PAGE) {
                    mAppView.moveToTop();
//                    mNegativeView.moveToTop();
                    mCameraView.moveToTop();
                    if (!bIsPaused && mDaialView != null) {
                        View view = mDaialView.getChildAt(0);
                        if (view instanceof DialBaseLayout) {
                            ((DialBaseLayout) view).onResume();
                        }
                    }
                } else if (mLastPosition == POSITION_MAIN_PAGE) {
                    if (mDaialView != null) {
                        View view = mDaialView.getChildAt(0);
                        if (view instanceof DialBaseLayout) {
                            ((DialBaseLayout) view).onPause();
                        }
                    }
                }
                if (bIsClassDisable && position != POSITION_MAIN_PAGE) {
                    mHandler.removeMessages(0x10);
                    mHandler.sendEmptyMessageDelayed(0x10, 1000 * 2);
                }

                // add by divhee start
                if (mViewPagerAdpater != null) {
                    mViewPagerAdpater.setNowPagerNumber(position);
                }
                // add by divhee end
                mLastPosition = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                mViewPagerScrollState = state;
            }
        });
        mViewPagerAdpater = new ViewPagerAdpater(mViewList);
        mViewpager.setAdapter(mViewPagerAdpater);
        mViewpager.setOffscreenPageLimit(3);
        mViewpager.setCurrentItem(POSITION_MAIN_PAGE);
        OverScrollDecoratorHelper.setUpOverScroll(mViewpager);
        mWatchController.setScreenOffListener(this);
        //startPowerAnimService();
        //Utils.setFirstBoot(Launcher.this,true);
        if (Utils.isFirstBoot(Launcher.this)) {
            InstructionsDialog.showInstructionsDialog(Launcher.this);
        }

        Settings.Global.putInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1);
        System.out.println("-----------  Provision USER_SETUP_COMPLETE");
        Settings.Secure.putInt(getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 1);

        rwm = (ReadboyWearManager) Launcher.this.getSystemService(Context.RBW_SERVICE);
        waitIRWS();
    }

    //等待IReadboyWearService
    public void waitIRWS() {
        if (rwm.getPersonalInfo() != null) {
            if (rwm.isClassForbidOpen()) {
                onClassDisableChange(true);
            } else {
                startFactoryModeService();
            }
            myHandler.removeMessages(WAIT_IRWS);
            myHandler = null;
        } else {
            myHandler.removeMessages(WAIT_IRWS);
            myHandler.sendEmptyMessageDelayed(WAIT_IRWS, 100);
        }
    }

    //启动服务搜索特定的wifi，來启动工厂模式
    public void startFactoryModeService() {
        if (mBatteryLevel < lowPowerLevel) {
            Log.e(TAG, "Can't start FM because of low lower.");
            return;
        }
        PersonalInfo info = rwm.getPersonalInfo();
        if (info != null) {
            if (info.getAdminUuid() != null && !"".equals(info.getAdminUuid())) {
                Log.e(TAG, "The watch has been bound!");
                return;
            }
        } else {
            return;
        }
        Intent intent = new Intent(Launcher.this, StartFactoryModeService.class);
        startService(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "onDestroy() ");
        mWatchController.removeClassDisableChangedCallback(this);
        mBatteryController.unregisterReceiver();
        mBatteryController.removeStateChangedCallback(this);
        mHandler.removeCallbacksAndMessages(null);
        myHandler.removeCallbacksAndMessages(null);
        mWetalkView.onDestroy();
        ClassDisableDialog.recycle();
    }

    @Override
    protected void onPause() {
        super.onPause();
        bIsTouchable = false;
        bIsPaused = true;
        closeDials(false);
        dialPasue();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps(true);

        // add by divhee start
        if (mViewpager != null && mViewpager.getAdapter() != null) {
            mViewpager.getAdapter().notifyDataSetChanged();
        }
        // add by divhee end

        bIsTouchable = true;
        bIsPaused = false;
        LauncherApplication.setTouchEnable(true);
        requestPermissions(sPermissions);
        forceUpdateDate();
        dialResume();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
        }
        if (!LauncherApplication.isTouchEnable() || !bIsTouchable) {
            return true;
        }
        if (Utils.isFirstBoot(Launcher.this)) {
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                InstructionsDialog.showInstructionsDialog(Launcher.this);
            }
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onClassDisableChange(boolean show) {
        Log.e("cwj", "onClassDisableChange: show=" + show);
        if (bIsClassDisable != show) {
            bIsClassDisable = show;
            mViewpager.setClassDisabled(show);
            ClassForbidUtils.handleClassForbid(bIsClassDisable, Launcher.this);
//            ReadboyWearManager rwm = (ReadboyWearManager)Launcher.this.getSystemService(Context.RBW_SERVICE);
//            rwm.setClassForbidOpen(show);
            if (bIsClassDisable) {
                closeDials(false);
                if (needGoToHome(Launcher.this, 0)) {
                    startActivity(new Intent(Launcher.this, Launcher.class));
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (needGoToHome(Launcher.this, 0)) {
                            startActivity(new Intent(Launcher.this, Launcher.class));
                        }
                    }
                }, 500);
                if (mGestureView != null && mGestureView.getVisibility() == View.VISIBLE
                        && mViewpager.getCurrentItem() != POSITION_MAIN_PAGE) {
                    mViewpager.setCurrentItem(POSITION_MAIN_PAGE);
                }
                mViewpager.scrollTo(0, 0);
                mViewPagerScrollState = ViewPager.SCROLL_STATE_IDLE;
                if (isHome(Launcher.this)) {
                    ClassDisableDialog.showClassDisableDialog(Launcher.this);
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Utils.checkAndDealWithAirPlanMode(Launcher.this);
                    }
                }, 1000);
            } else {
                mHandler.removeMessages(0x10);
            }
            //通知通知栏
            Intent intent = new Intent(ACTION_CLASS_DISABLE_STATUS_CHANGED);
            mLocalBroadcastManager.sendBroadcast(intent);
        }
    }

    int lowPowerLevel = 15;

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        if (mGestureView == null || mLowDialBaseLayout == null) {
            return;
        }
        if (mBatteryLevel == -1 || mBatteryLevel != level) {
            mBatteryLevel = level;
            PowerManager mPowerManager = (PowerManager) Launcher.this.getSystemService(Context.POWER_SERVICE);
            if (mBatteryLevel < lowPowerLevel) {//low powe
                mGestureView.setVisibility(View.GONE);
                mLowDialBaseLayout.setVisibility(View.VISIBLE);
                mLowDialBaseLayout.addChangedCallback();
                mLowDialBaseLayout.onResume();
                mLowDialBaseLayout.setButtonEnable();
                rwm.setLowPowerMode(true);
                if (!mPowerManager.isPowerSaveMode()) {
                    mPowerManager.setPowerSaveMode(true);
                }
                if (needGoToHome(Launcher.this, 1)) {
                    startActivity(new Intent(Launcher.this, Launcher.class));
                }
            } else {
                mLowDialBaseLayout.setVisibility(View.GONE);
                mGestureView.setVisibility(View.VISIBLE);
                rwm.setLowPowerMode(false);
                if (mPowerManager.isPowerSaveMode()) {
                    mPowerManager.setPowerSaveMode(false);
                }
            }
        } else {
            PowerManager mPowerManager = (PowerManager) Launcher.this.getSystemService(Context.POWER_SERVICE);
            if (level < lowPowerLevel && !mPowerManager.isPowerSaveMode()) {
                rwm.setLowPowerMode(true);
                mPowerManager.setPowerSaveMode(true);
            } else if (level >= lowPowerLevel && mPowerManager.isPowerSaveMode()) {
                rwm.setLowPowerMode(false);
                mPowerManager.setPowerSaveMode(false);
            }
        }
    }

    @Override
    public void onPowerSaveChanged() {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (WatchDials.getWatchDialsStatus() == WatchDials.ANIMATE_STATE_OPENING ||
                WatchDials.getWatchDialsStatus() == WatchDials.ANIMATE_STATE_OPENED) {
            return false;
        }
        int cur = mViewpager.getCurrentItem();
        if (e1 == null || e2 == null || cur != POSITION_MAIN_PAGE) {
            return false;
        }
        float vDistance = e1.getY() - e2.getY();
        boolean bVerticalMove = Math.abs(velocityX) - Math.abs(velocityY) < 0;
        if (vDistance > mTouchSlopSquare / 5 && bVerticalMove && mViewPagerScrollState == ViewPager.SCROLL_STATE_IDLE) {
            PersonalInfo info = rwm.getPersonalInfo();
            if (info != null && info.isHasSiri() == 1) {
                Log.e(TAG, "onFling: start Speech activity.");
                Utils.startActivity(Launcher.this, "com.readboy.watch.speech", "com.readboy.watch.speech.Main2Activity");
            } else {
                Utils.startActivity(Launcher.this, "com.android.settings", "com.android.qrcode.MainActivity");
            }
            return true;
        } else if (vDistance < -mTouchSlopSquare / 2 && bVerticalMove && mViewPagerScrollState == ViewPager.SCROLL_STATE_IDLE) {
            /*boolean isEnable = ((LauncherApplication) LauncherApplication.getApplication()).getWatchController().isNowEnable();*/

//            ReadboyWearManager rwm = (ReadboyWearManager) Launcher.this.getSystemService(Context.RBW_SERVICE);
//            boolean isEnable = rwm.isClassForbidOpen();
//            if (isEnable) {
//                ClassDisableDialog.showClassDisableDialog(Launcher.this);
//                Utils.checkAndDealWithAirPlanMode(Launcher.this);
//                return true;
//            }
            if (!isNotificationEnabled()) {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            } else {
                startActivity(new Intent(Launcher.this, NotificationActivity.class));
                Log.d(TAG, "lzx switchToFragment");
                //Utils.startActivity(Launcher.this, "com.readboy.wearlauncher",NotificationActivity.class.getName());
                //((Launcher)getActivity()).switchToFragment(NotificationFragment.class.getName(),null,true,true);
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (WatchDials.getWatchDialsStatus() == WatchDials.ANIMATE_STATE_OPENING ||
                WatchDials.getWatchDialsStatus() == WatchDials.ANIMATE_STATE_OPENED) {
            mGestureView.setIsGestureDrag(true);
            closeDials(true);
            mWatchType = LauncherSharedPrefs.getWatchType(Launcher.this);
            setDialFromType(mWatchType);
        }
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        if (WatchDials.getWatchDialsStatus() == WatchDials.ANIMATE_STATE_OPENING ||
                WatchDials.getWatchDialsStatus() == WatchDials.ANIMATE_STATE_OPENED) {
            return;
        }
        int cur = mViewpager.getCurrentItem();
        if (cur == POSITION_MAIN_PAGE) {
            mGestureView.setIsGestureDrag(true);
            openDials();
        }
    }

    @Override
    public void onItemClick(int position) {
        AppInfo info = mAppView.getAppInfo(position);
        Utils.startActivity(Launcher.this, info.mPackageName, info.mClassName);
    }

    @Override
    public Loader<ArrayList<AppInfo>> onCreateLoader(int id, Bundle args) {
        if (id != LOADER_ID) {
            return null;
        }
        return new AppsLoader(Launcher.this);
    }

    @Override
    public void onLoadFinished(Loader<ArrayList<AppInfo>> loader, ArrayList<AppInfo> appInfos) {
        if (loader.getId() == LOADER_ID) {
            mAppView.refreshData(appInfos);
        }
    }

    @Override
    public void onLoaderReset(Loader<ArrayList<AppInfo>> loader) {

    }

    @Override
    public void onScreenOn() {
        if (mBatteryLevel != -1 && mBatteryLevel < lowPowerLevel) {
            mLowDialBaseLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onScreenOff() {
        if (mGestureView != null && mGestureView.getVisibility() == View.VISIBLE && isHome(Launcher.this)
                && mViewpager.getCurrentItem() != POSITION_MAIN_PAGE) {
            mViewpager.setCurrentItem(POSITION_MAIN_PAGE);
        }
        mLowDialBaseLayout.setVisibility(View.GONE);   //灭屏后不刷新低电电量
    }

    class ViewPagerAdpater extends PagerAdapter {
        public List<View> mViewList;
        // add by divhee start
        private int nowPagerNumber = -1;
        // add by divhee end

        public ViewPagerAdpater(List<View> viewList) {
            this.mViewList = viewList;
        }

        public void setData(List<View> viewList) {
            mViewList = viewList;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mViewList != null ? mViewList.size() : 0;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            if (position >= 0 && position < mViewList.size()) {
                container.removeView(mViewList.get(position));
            }
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            // TODO Auto-generated method stub
            container.addView(mViewList.get(position));
            // add by divhee start
            mViewList.get(position).setTag(R.id.launcher_viewpager_pager_postion_id, position);
            // add by divhee end
            return mViewList.get(position);
        }

        // add by divhee start
        @Override
        public int getItemPosition(Object object) {
            if (object != null && object instanceof View) {
                try {
                    int objPagerIdx = (Integer) ((View) object).getTag(R.id.launcher_viewpager_pager_postion_id);
                    if (nowPagerNumber == objPagerIdx) {
                        return POSITION_NONE;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return super.getItemPosition(object);
        }

        public void setNowPagerNumber(int pagerNumber) {
            nowPagerNumber = pagerNumber;
            //Log.e("", "====divhee==setNowPagerNumber=="+nowPagerNumber);
        }

        public int getNowPagerNumber() {
            return nowPagerNumber;
        }
        // add by divhee end
    }

    public Handler mHandler = new Handler() {
        @Override
        public void dispatchMessage(Message msg) {
            if (mGestureView != null && mGestureView.getVisibility() == View.VISIBLE && mViewpager.getCurrentItem() != POSITION_MAIN_PAGE) {
                mViewpager.setCurrentItem(POSITION_MAIN_PAGE);
            }
            super.dispatchMessage(msg);
        }
    };

    public Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case WAIT_IRWS:
                    waitIRWS();
                    break;
                default:
                    break;
            }
        }
    };


    private void requestPermissions(String[] permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : permissions) {
                int permissionStatus = checkSelfPermission(permission);
                Log.v(TAG, "permissions=" + permissions + ",permissionStatus=" + permissionStatus);
                if (permissionStatus != PackageManager.PERMISSION_GRANTED/* && shouldShowRequestPermissionRationale(permission)*/) {
                    requestPermissions(permissions, PERMISSIONS_REQUEST_CODE);
                    break;
                }
            }
        }
    }

    private boolean isGrantedPermissions(String[] permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : permissions) {
                int permissionStatus = checkSelfPermission(permission);
                if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isNotificationEnabled() {
        String pkgName = this.getPackageName();
        final String flat = Settings.Secure.getString(Launcher.this.getContentResolver(),
                "enabled_notification_listeners");
        Log.d(TAG, "notification flat:" + flat);
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (int i = 0; i < names.length; i++) {
                final ComponentName cn = ComponentName.unflattenFromString(names[i]);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void forceUpdateDate() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int week = (calendar.get(Calendar.DAY_OF_WEEK) - 1) % WatchController.WEEK_NAME_CN_SHORT.length;
        if (mLowDialBaseLayout != null && mLowDialBaseLayout.isShown()) {
            mLowDialBaseLayout.onDateChange(year, month, day, week);
        }
        if (mDaialView != null /*&& mDaialView.isShown()*/) {
            View view = mDaialView.getChildAt(0);
            if (view instanceof DialBaseLayout) {
                ((DialBaseLayout) view).onDateChange(year, month, day, week);
            }
        }
    }

    private void dialPasue() {
        if (mLowDialBaseLayout != null && mLowDialBaseLayout.isShown()) {
            mLowDialBaseLayout.onPause();
            mLowDialBaseLayout.removeChangedCallback();
        }
        if (mDaialView != null /*&& mDaialView.isShown()*/) {
            View view = mDaialView.getChildAt(0);
            if (view instanceof DialBaseLayout) {
                ((DialBaseLayout) view).onPause();
                ((DialBaseLayout) view).removeChangedCallback();
            }
        }
    }

    private void dialResume() {
        Log.d("cwj", "dialResume");
        if (mLowDialBaseLayout != null && mLowDialBaseLayout.isShown()) {
            mLowDialBaseLayout.onResume();
            mLowDialBaseLayout.addChangedCallback();
        }

        if (mDaialView != null /*&& mDaialView.isShown()*/) {
            View view = mDaialView.getChildAt(0);
            if (view instanceof DialBaseLayout) {
                if(mViewpager.getCurrentItem() == POSITION_MAIN_PAGE){
                    ((DialBaseLayout) view).onResume();
                }
                ((DialBaseLayout) view).addChangedCallback();
            }
        }
    }

    private void forceCloseWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "My Tag");
        synchronized (TAG) {
            if (wakeLock != null) {
                Log.v(TAG, "Releasing wakelock");
                try {
                    wakeLock.release();
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            } else {
                Log.e(TAG, "Wakelock reference is null");
            }
        }
    }

    public void startPowerAnimService() {
        Intent intent = new Intent();
        ComponentName component = new ComponentName("com.readboy.floatwindow", "com.readboy.floatwindow.FloatWindowService");
        intent.setComponent(component);
        Intent tmp = Utils.createExplicitFromImplicitIntent(Launcher.this, intent);
        if (tmp != null) {
            try {
                Intent eintent = new Intent(tmp);
                Launcher.this.startService(eintent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void loadApps(boolean reLoad) {
        if (reLoad) {
            getSupportLoaderManager().restartLoader(LOADER_ID, null, this);
        } else {
            getSupportLoaderManager().initLoader(LOADER_ID, null, this);
        }
    }

    private void openDials() {
        mWatchDials = WatchDials.fromXml(Launcher.this);
        mGestureView.cancelLongPress();
        mGestureView.addView(mWatchDials);
        mWatchDials.animateOpen();
    }

    private void closeDials(boolean saveMode) {
        if (mWatchDials != null && mWatchDials.isShown()) {
            mWatchDials.animateClose(saveMode);
        }
    }

    private void setDialFromType(int type) {
        View view = mDaialView.getChildAt(0);
        if (view instanceof DialBaseLayout) {
            ((DialBaseLayout) view).onPause();
            ((DialBaseLayout) view).removeChangedCallback();
        }
        mDaialView.removeAllViews();
        DialBaseLayout childDaialView = (DialBaseLayout) mInflater.inflate(WatchDials.mDialList.get(type % WatchDials.mDialList.size()), mDaialView, false);
        childDaialView.addChangedCallback();
        childDaialView.onResume();
        childDaialView.setButtonEnable();
        mDaialView.addView(childDaialView);
        mViewPagerAdpater.setData(mViewList);
        dialResume();
    }

    private boolean needGoToHome(Context context, int type) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> runningTask = manager.getRunningTasks(1);
        String _pkgName = null;
        String topActivityName = null;
        if (runningTask != null) {
            _pkgName = runningTask.get(0).topActivity.getPackageName();
            topActivityName = runningTask.get(0).topActivity.getClassName();
        } else {
            return false;
        }

        if (_pkgName != null && topActivityName != null) {
            if (type == 1 && _pkgName.equals("com.android.dialer")) {
                return false;
            } else if (_pkgName.equals("com.android.dialer") && topActivityName.equals("com.android.incallui.InCallActivity")) {
                mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
                return false;
            } else if (_pkgName.equals("com.readboy.wearlauncher") && topActivityName.equals("com.readboy.wearlauncher.Launcher")) {
                return false;
            }
        }

        return true;
    }

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (state == TelephonyManager.CALL_STATE_IDLE) {
                mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        /*boolean isEnable = ((LauncherApplication)
                                LauncherApplication.getApplication()).getWatchController().isNowEnable();*/
                        boolean isEnable = rwm.isClassForbidOpen();
                        Log.e("cwj", "needGoToHome isEnable " + isEnable);
                        if (isEnable) {
                            startActivity(new Intent(Launcher.this, Launcher.class));
                            if (isHome(Launcher.this)) {
                                ClassDisableDialog.showClassDisableDialog(Launcher.this);
                            }
                        }
                    }
                }, 500);
            }
        }
    };

    private boolean isHome(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> runningTask = manager.getRunningTasks(1);
        String _pkgName = null;
        String topActivityName = null;
        if (runningTask != null) {
            _pkgName = runningTask.get(0).topActivity.getPackageName();
            topActivityName = runningTask.get(0).topActivity.getClassName();
        }
        if (_pkgName != null && topActivityName != null) {
            if (_pkgName.equals("com.readboy.wearlauncher") && topActivityName.equals("com.readboy.wearlauncher.Launcher")) {
                return true;
            }
        }
        return false;
    }

    private Fragment getVisibleFragment(FragmentManager fragmentManager) {
        List<Fragment> fragments = fragmentManager.getFragments();
        if (fragments == null) {
            return null;
        }
        for (Fragment fragment : fragments) {
            if (fragment != null && fragment.isVisible()
                    && fragment.getUserVisibleHint()) {
                return fragment;
            }
        }

        return null;
    }

    public Fragment switchToFragment(String fragmentName, Bundle args,
                                     boolean addToBackStack, boolean withTransition) {

        Fragment f = Fragment.instantiate(this, fragmentName, args);
        FragmentTransaction transaction = mFragmentManager.beginTransaction();
//        if (withTransition) {
//            transaction.setCustomAnimations(R.anim.push_in_right,
//                    R.anim.push_out_right, R.anim.back_in_left,
//                    R.anim.back_out_left);
//        }
        if (addToBackStack) {
            transaction.addToBackStack(fragmentName.getClass().getSimpleName());
        }
        transaction.replace(R.id.content_container, f, fragmentName);
        transaction.commitAllowingStateLoss();
        mFragmentManager.executePendingTransactions();
        return f;
    }

    public View setTitle(String title) {
        View view = LayoutInflater.from(this).inflate(R.layout.title_common, null, false);
        TextView textView = (TextView) view.findViewById(R.id.title);
        textView.setText(title);
        return view;
    }

}
