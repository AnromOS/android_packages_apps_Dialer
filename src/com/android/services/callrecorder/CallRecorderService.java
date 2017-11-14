/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.services.callrecorder;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.provider.Settings;
import android.util.Log;

import com.android.services.callrecorder.common.CallRecording;
import com.android.services.callrecorder.common.ICallRecorderService;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.android.dialer.R;

public class CallRecorderService extends Service {
    private static final String TAG = "CallRecorderService";
    private static final boolean DBG = false;

    //add by rom
    private static final String RECORD_FINISHED = "com.android.services.callrecorder.CALL_RECORD_FINISHED";
    private static final String RECORD_FINISHED_PERMISSION = "com.android.dialer.CALL_RECORD_FINISHED";
    private static enum RecorderState {
        IDLE,
        RECORDING
    };

    private MediaRecorder mMediaRecorder = null;
    private RecorderState mState = RecorderState.IDLE;
    private CallRecording mCurrentRecording = null;

    private static final String AUDIO_SOURCE_PROPERTY = "persist.call_recording.src";

    private SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyMMdd_HHmmss");

    private final ICallRecorderService.Stub mBinder = new ICallRecorderService.Stub() {
        @Override
        public CallRecording stopRecording() {
            Log.i(TAG, "jin CallRecorderService stopRecording");
            if (getState() == RecorderState.RECORDING) {
                Log.i(TAG, "jin CallRecorderService RECORDING calling stopRecordingInternal");
                stopRecordingInternal();
                return mCurrentRecording;
            }
            Log.i(TAG, "jin CallRecorderService stopRecording state is NOT RECORDING");
            return null;
        }

        @Override
        public boolean startRecording(String phoneNumber, long creationTime,
                                boolean isOutgoing)
                throws RemoteException {
            String fileName = generateFilename(phoneNumber, isOutgoing);
            mCurrentRecording = new CallRecording(phoneNumber, creationTime,
                    fileName, System.currentTimeMillis());
            Log.i(TAG, "jin CallRecorderService startRecording fileName:[" + fileName + "] new mCurrentRecording"
                + " calling startRecordingInternal");
            return startRecordingInternal(mCurrentRecording.getFile());

        }

        @Override
        public boolean isRecording() throws RemoteException {
             Log.i(TAG, "jin CallRecorderService isRecording calling getState");
            return getState() == RecorderState.RECORDING;
        }

        @Override
        public CallRecording getActiveRecording() throws RemoteException {
            Log.i(TAG, "jin CallRecorderService getActiveRecording");
            return mCurrentRecording;
        }
    };

    @Override
    public void onCreate() {
        if (DBG) Log.d(TAG, "Creating CallRecorderService");
        Log.i(TAG, "jin CallRecorderService Creating CallRecorderService");
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "jin CallRecorderService onBind");
        return mBinder;
    }

    private int getAudioSource() {
        int defaultValue = getResources().getInteger(R.integer.call_recording_audio_source);
        return SystemProperties.getInt(AUDIO_SOURCE_PROPERTY, defaultValue);
    }

    private int getAudioFormatChoice() {
        // This replicates PreferenceManager.getDefaultSharedPreferences, except
        // that we need multi process preferences, as the pref is written in a separate
        // process (com.android.dialer vs. com.android.incallui)
        final String prefName = getPackageName() + "_preferences";
        final SharedPreferences prefs = getSharedPreferences(prefName, MODE_MULTI_PROCESS);

        try {
            String value = prefs.getString(getString(R.string.call_recording_format_key), null);
            if (value != null) {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            // ignore and fall through
        }
        return 0;
    }

    private synchronized boolean startRecordingInternal(File file) {
        if (mMediaRecorder != null) {
            if (DBG) {
                Log.d(TAG, "Start called with recording in progress, stopping  current recording");
            }
            Log.i(TAG, "jin CallRecorderService Start called with recording in progress, stopping  current recording."
            + " calling stopRecordingInternal");
            stopRecordingInternal();
        } else {
            Log.i(TAG, "jin CallRecorderService startRecordingInternal mMediaRecorder is null");
        }

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Record audio permission not granted, can't record call");
            Log.i(TAG, "jin CallRecorderService Record audio permission not granted, can't record call");
            return false;
        }
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "External storage permission not granted, can't save recorded call");
            Log.i(TAG, "jin CallRecorderService External storage permission not granted, can't save recorded call");
            return false;
        }

        if (DBG) Log.d(TAG, "Starting recording");
        Log.i(TAG, "jin CallRecorderService Starting recording new MediaRecorder");

        mMediaRecorder = new MediaRecorder();
        try {
            int audioSource = getAudioSource();
            int formatChoice = getAudioFormatChoice();
            Log.i(TAG, "jin CallRecorderService Creating media recorder with audio source " + audioSource
                    + "  formatChoice " + formatChoice);
            if (DBG) Log.d(TAG, "Creating media recorder with audio source " + audioSource);
            mMediaRecorder.setAudioSource(audioSource);
            mMediaRecorder.setOutputFormat(formatChoice == 0
                    ? MediaRecorder.OutputFormat.AMR_WB : MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setAudioEncoder(formatChoice == 0
                    ? MediaRecorder.AudioEncoder.AMR_WB : MediaRecorder.AudioEncoder.AAC);
        } catch (IllegalStateException e) {
            Log.w(TAG, "Error initializing media recorder", e);
            Log.w(TAG, "jin CallRecorderService Error initializing media recorder", e);
            Log.i(TAG, "jin CallRecorderService Error initializing media recorder IllegalStateException", e);
            return false;
        }

        file.getParentFile().mkdirs();
        String outputPath = file.getAbsolutePath();
        if (DBG) Log.d(TAG, "Writing output to file " + outputPath);
        Log.i(TAG, "jin CallRecorderService Writing output to file " + outputPath);

        try {
            mMediaRecorder.setOutputFile(outputPath);
            mMediaRecorder.prepare();
            mMediaRecorder.start();
            Log.i(TAG, "jin CallRecorderService set mState RECORDING");
            mState = RecorderState.RECORDING;
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Could not start recording for file " + outputPath, e);
            Log.w(TAG, "Deleting failed recording " + outputPath);
            Log.w(TAG, "jin CallRecorderService Could not start recording for file " + outputPath, e);
            Log.w(TAG, "jin CallRecorderService Deleting failed recording " + outputPath);
            Log.i(TAG, "jin CallRecorderService Could not start recording for file IOException" + outputPath, e);
            Log.i(TAG, "jin CallRecorderService Deleting failed recording IOException" + outputPath);
            file.delete();
        } catch (IllegalStateException e) {
            Log.w(TAG, "Could not start recording for file " + outputPath, e);
            Log.w(TAG, "Deleting failed recording " + outputPath);
            Log.i(TAG, "jin CallRecorderService Could not start recording for file " + outputPath, e);
            Log.i(TAG, "jin CallRecorderService Deleting failed recording " + outputPath);
            file.delete();
        } catch (RuntimeException e) {
            // only catch exceptions thrown by the MediaRecorder JNI code
            if (e.getMessage().indexOf("start failed") >= 0) {
                Log.w(TAG, "Could not start recording for file " + outputPath, e);
                Log.w(TAG, "Deleting failed recording " + outputPath);
                file.delete();
            } else {
                throw e;
            }
        }

        mMediaRecorder.reset();
        mMediaRecorder.release();
        mMediaRecorder = null;

        return false;
    }

    //add by rom
    private void sendRecordFinishedBroadcast(String fileName) {
        Intent intent = new Intent();  
        intent.setAction(RECORD_FINISHED);  
        intent.putExtra("filename", fileName);  
        sendBroadcast(intent, RECORD_FINISHED_PERMISSION);
    }

    private synchronized void stopRecordingInternal() {
        if (DBG) Log.d(TAG, "Stopping current recording");
        Log.i(TAG, "jin CallRecorderService Stopping current recording");
        if (mMediaRecorder != null) {
            try {
                if (getState() == RecorderState.RECORDING) {
                    Log.i(TAG, "jin CallRecorderService stopRecordingInternal state is RECORDING "
                    + "mMediaRecorder stop reset release");
                    mMediaRecorder.stop();
                    mMediaRecorder.reset();
                    mMediaRecorder.release();
                    sendRecordFinishedBroadcast(mCurrentRecording.fileName);
                } else {
                    Log.i(TAG, "jin CallRecorderService stopRecordingInternal state is NOT RECORDING");
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Exception closing media recorder", e);
                Log.i(TAG, "jin CallRecorderService Exception closing media recorder", e);
            }
            MediaScannerConnection.scanFile(this, new String[] {
                mCurrentRecording.fileName
            }, null, null);
            mMediaRecorder = null;
            Log.i(TAG, "jin CallRecorderService set mState IDLE");
            mState = RecorderState.IDLE;
        } else {
            Log.i(TAG, "jin CallRecorderService stopRecordingInternal mMediaRecorder is null");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DBG) Log.d(TAG, "Destroying CallRecorderService");
        Log.i(TAG, "jin CallRecorderService Destroying CallRecorderService");
    }

    private synchronized RecorderState getState() {
        Log.i(TAG, "jin CallRecorderService getState");
        return mState;
    }

    private String generateFilename(String number, boolean isOutgoing) {
        String timestamp = DATE_FORMAT.format(new Date());

        if (TextUtils.isEmpty(number)) {
            number = "unknown";
        }

        //add by rom -jin
        String direction = isOutgoing? "outgoing":"incoming";

        int formatChoice = getAudioFormatChoice();
        String extension = formatChoice == 0 ? ".amr" : ".m4a";
        Log.i(TAG, "jin CallRecorderService generateFilename : " +
                direction + "_"  + number + "_" + timestamp + extension);
        return direction + "_" + number + "_" + timestamp + extension;
    }

    public static boolean isEnabled(Context context) {
        Log.i(TAG, "jin CallRecorderService isEnabled : " + context.getResources().getBoolean(R.bool.call_recording_enabled));
        return context.getResources().getBoolean(R.bool.call_recording_enabled);
    }
}
