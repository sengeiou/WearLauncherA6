#
# Copyright (C) 2013 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH := $(call my-dir)

#
# Build app code.
#
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_ANDROID_LIBRARIES := \
    android-support-v4 \
    android-support-v7-appcompat \
    android-support-v7-recyclerview \
    android-support-v7-palette \
    android-support-v13 \

LOCAL_STATIC_JAVA_LIBRARIES := \
	wearlauncher-picasso \
	launcher-glide \
    	launcher-baseadapter
    
LOCAL_JAVA_LIBRARIES := telephony-common
LOCAL_JAVA_LIBRARIES += ims-common

wetalk_support_dir := ../../../../../../packages/apps/WeTalk/support

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src) \
    $(call all-renderscript-files-under, src) \
    $(call all-java-files-under, $(wetalk_support_dir)/src)

LOCAL_RESOURCE_DIR := \
    $(LOCAL_PATH)/res \
    frameworks/support/v7/recyclerview/res \
    frameworks/support/v7/cardview/res \
    packages/apps/WeTalk/support/res

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages com.android.keyguard \
    --extra-packages com.readboy.wetalk.support

LOCAL_CERTIFICATE := platform
#LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 21
LOCAL_PACKAGE_NAME := WearLauncher
LOCAL_PRIVILEGED_MODULE := true
LOCAL_DEX_PREOPT := false
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_USE_AAPT2 := true

LOCAL_OVERRIDES_PACKAGES += Launcher3 Launcher3Go

ifeq ($(strip $(CENON_SIMPLIFY_VERSION)), yes)
LOCAL_OVERRIDES_PACKAGES += \
    DataTransfer \
    AutoDialer \
    BasicDreams \
    BSPTelephonyDevTool \
    BookmarkProvider \
    BluetoothMidiService \
    BtTool \
    BackupRestoreConfirmation \
    CallLogBackup \
    WallpaperCropper \
    WallpaperBackup \
    PhotoTable \
    PicoTts \
    LiveWallpapers \
    LiveWallpapersPicker \
    MagicSmokeWallpapers \
    VisualizationWallpapers \
    Galaxy4 \
    HoloSpiralWallpaper \
    NoiseField \
    PhaseBeam \
    YahooNewsWidget \
    CarrierConfig \
    MtkQuickSearchBox \
    QuickSearchBox \
    MtkFloatMenu \
    MTKThermalManager \
    TouchPal \
    Development \
    MultiCoreObserver \
    CtsShimPrebuilt \
    EasterEgg \
    DuraSpeed \
    EmergencyInfo \
    MtkEmergencyInfo \
    ExactCalculator \
    PrintRecommendationService \
    CallLogBackup \
    CtsShimPrivPrebuilt \
    SimRecoveryTestTool \
    FileManagerTest \
    WiFiTest \
    SensorHub \
    BuiltInPrintService \
    CompanionDeviceManager \
    MtkWallpaperPicker \
    EmCamera \
    Stk \
    Stk1

## set MTK_BASIC_PACKAGE

LOCAL_OVERRIDES_PACKAGES += \
    Email \
    Exchange2 \
    MtkEmail \
    VpnDialogs \
    SharedStorageBackup \
    Calculator \
    MtkBrowser \
    MtkCalendar \
    MusicBspPlus \
    MusicFX \
    FMRadio \
    MtkDeskClock \
    SimProcessor \
    WiFiTest \
    SensorHub \
    QuickSearchBox \
    SchedulePowerOnOff \
    DownloadProviderUi \
    FileManager \
    MtkContacts  \
    PrintSpooler \
    MtkGallery2 \
    MtkLatinIME \
    CellBroadcastReceiver

endif

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
	launcher-glide:libs/glide-3.7.0.jar \
    	launcher-baseadapter:/libs/baseadapter.jar \
	wearlauncher-picasso:libs/picasso.jar

include $(BUILD_MULTI_PREBUILT)

#include $(BUILD_HOST_JAVA_LIBRARY)

# ==================================================
include $(call all-makefiles-under,$(LOCAL_PATH))

