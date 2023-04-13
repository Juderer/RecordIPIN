package com.zhushuli.recordipin.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.zhushuli.recordipin.R;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SettingsFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "SettingsFragment";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
        Log.d(TAG, "onCreatePreferences");

        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .registerOnSharedPreferenceChangeListener(this);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        try {
            Activity activity = getActivity();
            CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

            for (String cameraId : cameraManager.getCameraIdList()) {
                Log.d(TAG, "cameraId:" + cameraId);
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//                Size[] sizes = map.getOutputSizes(ImageReader.class);
//                Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
                Size[] sizes = map.getOutputSizes(ImageFormat.PRIVATE);

                // 保留比例为4:3或16:9的分辨率
                List<Size> filterSizes = new ArrayList<>();
                for (Size size : sizes) {
                    if (size.getWidth() * 3 == size.getHeight() * 4 || size.getWidth() * 9 == size.getHeight() * 16) {
                        filterSizes.add(size);
                    }
                }
//                Collections.sort(filterSizes, new Camera2Utils.CompareSizesByArea());
//                Size[] nSizes = filterSizes.toArray(new Size[filterSizes.size()]);
//
//                CharSequence[] rez = new CharSequence[nSizes.length];
//                CharSequence[] rezValues = new CharSequence[nSizes.length];
//                int defaultIndex = 0;
//                for (int i = 0; i < nSizes.length; i++) {
//                    rez[i] = nSizes[i].getWidth() + "x" + nSizes[i].getHeight();
//                    rezValues[i] = nSizes[i].getWidth() + "x" + nSizes[i].getHeight();
//                    if (nSizes[i].getWidth() == DesiredCameraSetting.mDesiredFrameWidth &&
//                            nSizes[i].getHeight() == DesiredCameraSetting.mDesiredFrameHeight) {
//                        defaultIndex = i;
//                    }
//                }

                // 降序排序（Java默认升序排序）
                Collections.sort(filterSizes, new Comparator<Size>() {
                    @Override
                    public int compare(Size o1, Size o2) {
                        return -(o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight());
                    }
                });

                CharSequence[] rez = new CharSequence[filterSizes.size()];
                CharSequence[] rezValues = new CharSequence[filterSizes.size()];
                int defaultIndex = 0;
                int count = 0;
                for (Size size : filterSizes) {
                    Log.d(TAG, "width:" + size.getWidth() + "," + "height:" + size.getHeight());
                    rez[count] = size.getWidth() + "x" + size.getHeight();
                    rezValues[count] = size.getWidth() + "x" + size.getHeight();
                    if (size.getWidth() == DesiredCameraSetting.mDesiredFrameWidth
                            && size.getHeight() == DesiredCameraSetting.mDesiredFrameHeight) {
                        defaultIndex = count;
                        Log.d(TAG, "defaultIndex:" + defaultIndex);
                    }
                    count++;
                }

                ListPreference cameraRez = getPreferenceManager().findPreference("prefCameraSizeRaw");
                assert cameraRez != null;

                cameraRez.setEntries(rez);
                cameraRez.setEntryValues(rezValues);
                // TODO::没有作用！是因为高版本API？
                cameraRez.setDefaultValue(rezValues[defaultIndex]);
                break;
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "onSharedPreferenceChanged");
    }
}

class DesiredCameraSetting {
    static final int mDesiredFrameWidth = 800;

    static final int mDesiredFrameHeight = 600;

    static final Long mDesiredExposureTime = 5000000L; // nanoseconds

    static final String mDesiredFrameSize = mDesiredFrameWidth + "x" + mDesiredFrameHeight;
}