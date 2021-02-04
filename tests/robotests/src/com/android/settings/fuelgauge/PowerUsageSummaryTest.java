/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.settings.fuelgauge;

import static com.android.settings.fuelgauge.PowerUsageSummary.BATTERY_INFO_LOADER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.loader.app.LoaderManager;
import androidx.preference.PreferenceScreen;

import com.android.internal.os.BatterySipper;
import com.android.internal.os.BatteryStatsHelper;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.fuelgauge.batterytip.BatteryTipPreferenceController;
import com.android.settings.testutils.FakeFeatureFactory;
import com.android.settings.testutils.XmlTestUtils;
import com.android.settings.testutils.shadow.ShadowUtils;
import com.android.settingslib.core.instrumentation.VisibilityLoggerMixin;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

// TODO: Improve this test class so that it starts up the real activity and fragment.
@RunWith(RobolectricTestRunner.class)
public class PowerUsageSummaryTest {

    private static final int UID = 123;
    private static final int POWER_MAH = 100;
    private static final long TIME_SINCE_LAST_FULL_CHARGE_MS = 120 * 60 * 1000;
    private static final long TIME_SINCE_LAST_FULL_CHARGE_US =
            TIME_SINCE_LAST_FULL_CHARGE_MS * 1000;
    private static final long USAGE_TIME_MS = 65 * 60 * 1000;
    private static final double TOTAL_POWER = 200;
    private static Intent sAdditionalBatteryInfoIntent;

    @BeforeClass
    public static void beforeClass() {
        sAdditionalBatteryInfoIntent = new Intent("com.example.app.ADDITIONAL_BATTERY_INFO");
    }

    @Mock
    private BatterySipper mNormalBatterySipper;
    @Mock
    private BatterySipper mScreenBatterySipper;
    @Mock
    private BatterySipper mCellBatterySipper;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private BatteryStatsHelper mBatteryHelper;
    @Mock
    private SettingsActivity mSettingsActivity;
    @Mock
    private LoaderManager mLoaderManager;
    @Mock
    private ContentResolver mContentResolver;
    @Mock
    private BatteryBroadcastReceiver mBatteryBroadcastReceiver;
    @Mock
    private VisibilityLoggerMixin mVisibilityLoggerMixin;
    @Mock
    private PreferenceScreen mPreferenceScreen;

    private List<BatterySipper> mUsageList;
    private Context mRealContext;
    private TestFragment mFragment;
    private FakeFeatureFactory mFeatureFactory;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mRealContext = spy(RuntimeEnvironment.application);
        mFeatureFactory = FakeFeatureFactory.setupForTest();
        mFragment = spy(new TestFragment(mRealContext));
        mFragment.initFeatureProvider();
        doNothing().when(mFragment).restartBatteryStatsLoader(anyInt());
        doReturn(mock(LoaderManager.class)).when(mFragment).getLoaderManager();

        when(mFragment.getActivity()).thenReturn(mSettingsActivity);
        when(mFeatureFactory.powerUsageFeatureProvider.getAdditionalBatteryInfoIntent())
                .thenReturn(sAdditionalBatteryInfoIntent);
        when(mBatteryHelper.getTotalPower()).thenReturn(TOTAL_POWER);
        when(mBatteryHelper.getStats().computeBatteryRealtime(anyLong(), anyInt()))
                .thenReturn(TIME_SINCE_LAST_FULL_CHARGE_US);

        when(mNormalBatterySipper.getUid()).thenReturn(UID);
        mNormalBatterySipper.totalPowerMah = POWER_MAH;
        mNormalBatterySipper.drainType = BatterySipper.DrainType.APP;

        mCellBatterySipper.drainType = BatterySipper.DrainType.CELL;
        mCellBatterySipper.totalPowerMah = POWER_MAH;

        mScreenBatterySipper.drainType = BatterySipper.DrainType.SCREEN;
        mScreenBatterySipper.usageTimeMs = USAGE_TIME_MS;

        mUsageList = new ArrayList<>();
        mUsageList.add(mNormalBatterySipper);
        mUsageList.add(mScreenBatterySipper);
        mUsageList.add(mCellBatterySipper);

        mFragment.mStatsHelper = mBatteryHelper;
        when(mBatteryHelper.getUsageList()).thenReturn(mUsageList);
        mFragment.mBatteryUtils = spy(new BatteryUtils(mRealContext));
        ReflectionHelpers.setField(mFragment, "mVisibilityLoggerMixin", mVisibilityLoggerMixin);
        ReflectionHelpers.setField(mFragment, "mBatteryBroadcastReceiver",
                mBatteryBroadcastReceiver);
        doReturn(mPreferenceScreen).when(mFragment).getPreferenceScreen();
        when(mFragment.getContentResolver()).thenReturn(mContentResolver);
    }

    @Test
    @Config(shadows = ShadowUtils.class)
    public void nonIndexableKeys_MatchPreferenceKeys() {
        final Context context = RuntimeEnvironment.application;
        final List<String> niks =
                PowerUsageSummary.SEARCH_INDEX_DATA_PROVIDER.getNonIndexableKeys(context);

        final List<String> keys =
                XmlTestUtils.getKeysFromPreferenceXml(context, R.xml.power_usage_summary);

        assertThat(keys).containsAtLeastElementsIn(niks);
    }

    @Test
    public void restartBatteryTipLoader() {
        //TODO: add policy logic here when BatteryTipPolicy is implemented
        doReturn(mLoaderManager).when(mFragment).getLoaderManager();

        mFragment.restartBatteryTipLoader();

        verify(mLoaderManager)
                .restartLoader(eq(PowerUsageSummary.BATTERY_TIP_LOADER), eq(Bundle.EMPTY), any());
    }

    @Test
    public void refreshUi_deviceRotate_doNotUpdateBatteryTip() {
        mFragment.mBatteryTipPreferenceController = mock(BatteryTipPreferenceController.class);
        when(mFragment.mBatteryTipPreferenceController.needUpdate()).thenReturn(false);
        mFragment.updateBatteryTipFlag(new Bundle());

        mFragment.refreshUi(BatteryBroadcastReceiver.BatteryUpdateType.MANUAL);

        verify(mFragment, never()).restartBatteryTipLoader();
    }

    @Test
    public void refreshUi_batteryLevelChanged_doNotUpdateBatteryTip() {
        mFragment.mBatteryTipPreferenceController = mock(BatteryTipPreferenceController.class);
        when(mFragment.mBatteryTipPreferenceController.needUpdate()).thenReturn(true);
        mFragment.updateBatteryTipFlag(new Bundle());

        mFragment.refreshUi(BatteryBroadcastReceiver.BatteryUpdateType.BATTERY_LEVEL);

        verify(mFragment, never()).restartBatteryTipLoader();
    }

    @Test
    public void refreshUi_tipNeedUpdate_updateBatteryTip() {
        mFragment.mBatteryTipPreferenceController = mock(BatteryTipPreferenceController.class);
        when(mFragment.mBatteryTipPreferenceController.needUpdate()).thenReturn(true);
        mFragment.updateBatteryTipFlag(new Bundle());

        mFragment.refreshUi(BatteryBroadcastReceiver.BatteryUpdateType.MANUAL);

        verify(mFragment).restartBatteryTipLoader();
    }

    @Test
    public void onResume_registerContentObserver() {
        mFragment.onResume();

        verify(mContentResolver).registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.BATTERY_ESTIMATES_LAST_UPDATE_TIME),
                false,
                mFragment.mSettingsObserver);
    }

    @Test
    public void onPause_unregisterContentObserver() {
        mFragment.onPause();

        verify(mContentResolver).unregisterContentObserver(
                mFragment.mSettingsObserver);
    }

    @Test
    public void restartBatteryInfoLoader_contextNull_doNothing() {
        when(mFragment.getContext()).thenReturn(null);
        when(mFragment.getLoaderManager()).thenReturn(mLoaderManager);

        mFragment.restartBatteryInfoLoader();

        verify(mLoaderManager, never()).restartLoader(BATTERY_INFO_LOADER, Bundle.EMPTY,
                mFragment.mBatteryInfoLoaderCallbacks);
    }

    public static class TestFragment extends PowerUsageSummary {
        private Context mContext;

        public TestFragment(Context context) {
            mContext = context;
        }

        @Override
        public Context getContext() {
            return mContext;
        }

        @Override
        protected ContentResolver getContentResolver() {
            // Override it so we can access this method in test
            return super.getContentResolver();
        }
    }
}
