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
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.util.Size;

import androidx.annotation.NonNull;
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
import java.util.Locale;

public class SettingsFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "SettingsFragment";

    private Context mContext;

    @Override
    public void onAttach(@NonNull Context context) {
        Log.d(TAG, "onAttach");
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
        Log.d(TAG, "onCreatePreferences");

        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .registerOnSharedPreferenceChangeListener(this);

        ListPreference recordMode = getPreferenceManager().findPreference("prefCameraRecordMode");
        ListPreference lensFacing = getPreferenceManager().findPreference("prefCameraLensFacing");

        if (recordMode.getValue().equals("0")) {
            Log.d(TAG, lensFacing.getValue());
            lensFacing.setEnabled(true);
            updateFrameSize(Integer.parseInt(lensFacing.getValue()));
        } else {
            lensFacing.setValueIndex(1);
            lensFacing.setEnabled(false);
            updateFrameSize(Integer.parseInt(lensFacing.getValue()));
        }

//        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "onSharedPreferenceChanged");

        Log.d(TAG, key);
        if (key.equals("prefCameraLensFacing")) {
            int recordMode = Integer.parseInt(sharedPreferences.getString("prefCameraRecordMode", "0"));
            if (recordMode == 0) {
                int lenFacingType = Integer.parseInt(sharedPreferences.getString(key, "1"));
                updateFrameSize(lenFacingType);
            }
        } else if (key.equals("prefCameraRecordMode")) {
            ListPreference recordMode = getPreferenceManager().findPreference("prefCameraRecordMode");
            ListPreference lensFacing = getPreferenceManager().findPreference("prefCameraLensFacing");

            if (recordMode.getValue().equals("0")) {
                lensFacing.setEnabled(true);
                updateFrameSize(Integer.parseInt(lensFacing.getValue()));
            } else {
                lensFacing.setValueIndex(1);
                lensFacing.setEnabled(false);
                updateFrameSize(Integer.parseInt(lensFacing.getValue()));
            }
        }
    }

    private void updateFrameSize(int lensFacingType) {
        try {
            CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            for (String cameraId : cameraManager.getCameraIdList()) {
                Log.d(TAG, "cameraId:" + cameraId);
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) != lensFacingType) {
                    continue;
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//                Size[] sizes = map.getOutputSizes(ImageReader.class);
//                Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
                Size[] sizes = map.getOutputSizes(MediaRecorder.class);
                Size[] imageSizes = map.getOutputSizes(ImageFormat.JPEG);
                Size[] videoSizes = map.getOutputSizes(MediaRecorder.class);

                // 保留比例为4:3或16:9的分辨率
                List<Size> filterSizes = new ArrayList<>();
                List<Size> filteredImageSizes = new ArrayList<>();
                List<Size> filteredVideoSizes = new ArrayList<>();
                for (Size size : imageSizes) {
                    if (size.getWidth() * 3 == size.getHeight() * 4 || size.getWidth() * 9 == size.getHeight() * 16) {
                        filteredImageSizes.add(size);
                    }
                }
                for (Size size : videoSizes) {
                    if (size.getWidth() * 3 == size.getHeight() * 4 || size.getWidth() * 9 == size.getHeight() * 16) {
                        filteredVideoSizes.add(size);
                    }
                }
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
                Collections.sort(filteredImageSizes, new Comparator<Size>() {
                    @Override
                    public int compare(Size o1, Size o2) {
                        return -(o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight());
                    }
                });
                Collections.sort(filteredVideoSizes, new Comparator<Size>() {
                    @Override
                    public int compare(Size o1, Size o2) {
                        return -(o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight());
                    }
                });

                CharSequence[] imageSizeStrings = new CharSequence[filteredImageSizes.size()];
                CharSequence[] imageSizeValues = new CharSequence[filteredImageSizes.size()];
                int count = 0;
                for (Size size : filteredImageSizes) {
//                    Log.d(TAG, "width:" + size.getWidth() + "," + "height:" + size.getHeight());
                    imageSizeStrings[count] = size.getWidth() + "x" + size.getHeight();
                    imageSizeValues[count] = size.getWidth() + "x" + size.getHeight();
                    count++;
                }

                ListPreference cameraImageSize = getPreferenceManager().findPreference("prefCameraFrameSize");
                assert cameraImageSize != null;

                cameraImageSize.setEntries(imageSizeStrings);
                cameraImageSize.setEntryValues(imageSizeValues);

                CharSequence[] videoSizeStrings = new CharSequence[filteredVideoSizes.size()];
                CharSequence[] videoSizeValues = new CharSequence[filteredVideoSizes.size()];
                for (int i = 0; i < filteredVideoSizes.size(); i++) {
                    videoSizeStrings[i] = filteredVideoSizes.get(i).getWidth() + "x" + filteredVideoSizes.get(i).getHeight();
                    videoSizeValues[i] = filteredVideoSizes.get(i).getWidth() + "x" + filteredVideoSizes.get(i).getHeight();
                }

                ListPreference cameraVideoSize = getPreferenceManager().findPreference("prefCameraVideoSize");
                assert cameraVideoSize != null;

                cameraVideoSize.setEntries(videoSizeStrings);
                cameraVideoSize.setEntryValues(videoSizeValues);

//                Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
//                for (Range<Integer> fpsRange : fpsRanges) {
//                    Log.d(TAG, fpsRange.getLower() + "->" + fpsRange.getUpper());
//                }

                break;
            }
        } catch (CameraAccessException | NullPointerException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDetach() {
        Log.d(TAG, "onDetach");
        super.onDetach();
    }
}