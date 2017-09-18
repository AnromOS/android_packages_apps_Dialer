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

package com.android.incallui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.services.callrecorder.CallRecorderService;
import com.android.services.callrecorder.CallRecordingDataStore;
import com.android.services.callrecorder.common.CallRecording;
import com.android.services.callrecorder.common.ICallRecorderService;

import java.util.Date;
import java.util.HashSet;

/**
 * InCall UI's interface to the call recorder
 *
 * Manages the call recorder service lifecycle.  We bind to the service whenever an active call
 * is established, and unbind when all calls have been disconnected.
 */
public class CallRecorder implements CallList.Listener {
    public static final String TAG = "CallRecorder";

    public static final String[] REQUIRED_PERMISSIONS = new String[] {
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private static CallRecorder sInstance = null;

    private Context mContext;
    private boolean mInitialized = false;
    private ICallRecorderService mService = null;

    private HashSet<RecordingProgressListener> mProgressListeners =
            new HashSet<RecordingProgressListener>();
    private Handler mHandler = new Handler();

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "jin old CallRecorder onServiceConnected");
            mService = ICallRecorderService.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "jin old CallRecorder onServiceDisconnected");
            mService = null;
        }
    };

    public static CallRecorder getInstance() {
        if (sInstance == null) {
            Log.i(TAG, "jin old  CallRecorder getInstance sInstance is null,new it");
            sInstance = new CallRecorder();
        }
        Log.i(TAG, "jin old CallRecorder getInstance return sInstance");
        return sInstance;
    }

    public boolean isEnabled() {
        Log.i(TAG, "jin old  CallRecorder isEnabled calling service's isEnabled");
        return CallRecorderService.isEnabled(mContext);
    }

    private CallRecorder() {
        Log.i(TAG, "jin old CallRecorder calling CallList addListener");
        //CallList.getInstance().addListener(this);
    }

    public void setUp(Context context) {
        Log.i(TAG, "jin old CallRecorder setUp");
        mContext = context.getApplicationContext();
    }

    private void initialize() {
        if (isEnabled() && !mInitialized) {
            Log.i(TAG, "jin old CallRecorder initialize");
            Intent serviceIntent = new Intent(mContext, CallRecorderService.class);
            mContext.bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
            mInitialized = true;
        }
    }

    private void uninitialize() {
        Log.i(TAG, "jin old CallRecorder uninitialize");
        if (mInitialized) {
            Log.i(TAG, "jin old CallRecorder unbindService");
            mContext.unbindService(mConnection);
            mInitialized = false;
        }
    }

    public boolean startRecording(final String phoneNumber, final long creationTime) {
        if (mService == null) {
            Log.i(TAG, "old CallRecorder startRecording mService is null");
            return false;
        }

        try {
            if (mService.startRecording(phoneNumber, creationTime)) {
                for (RecordingProgressListener l : mProgressListeners) {
                    l.onStartRecording();
                }
                mUpdateRecordingProgressTask.run();
                return true;
            } else {
                Toast.makeText(mContext, R.string.call_recording_failed_message,
                        Toast.LENGTH_SHORT).show();
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to start recording " + phoneNumber + ", " +
                    new Date(creationTime), e);
            Log.i(TAG, "jin old CallRecorder startRecording Failed to start recording " + phoneNumber + ", " +
                    new Date(creationTime), e);
        }

        return false;
    }

    public boolean isRecording() {
        if (mService == null) {
            Log.i(TAG, "jin old  CallRecorder isRecording mService is null");
            return false;
        }

        try {
            return mService.isRecording();
        } catch (RemoteException e) {
            Log.w(TAG, "Exception checking recording status", e);
            Log.i(TAG, "jin old  CallRecorder Exception checking recording status", e);
        }
        return false;
    }

    public CallRecording getActiveRecording() {
        if (mService == null) {
            Log.i(TAG, "jin old  CallRecorder getActiveRecording mService is null");
            return null;
        }

        try {
            Log.i(TAG, "jin old  CallRecorder getActiveRecording calling mService.getActiveRecording");
            return mService.getActiveRecording();
        } catch (RemoteException e) {
            Log.w("jin old CallRecording Exception getting active recording", e);
            //~ Log.w("Exception getting active recording", e);
        }
        return null;
    }

    public void finishRecording() {
        if (mService != null) {
            try {
                Log.i(TAG, "jin old CallRecorder finishRecording calling mService.stopRecording");
                final CallRecording recording = mService.stopRecording();
                if (recording != null) {
                    if (!TextUtils.isEmpty(recording.phoneNumber)) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Log.i(TAG, "jin old CallRecorder finishRecording dataStore write");
                                CallRecordingDataStore dataStore = new CallRecordingDataStore();
                                dataStore.open(mContext);
                                dataStore.putRecording(recording);
                                dataStore.close();
                            }
                        }).start();
                    } else {
                        Log.i(TAG, "jin old CallRecorder recording.phoneNum is null");
                        // Data store is an index by number so that we can link recordings in the
                        // call detail page.  If phone number is not available (conference call or
                        // unknown number) then just display a toast.
                        String msg = mContext.getResources().getString(
                                R.string.call_recording_file_location, recording.fileName);
                        Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.i(TAG, "jin old CallRecorder finishRecording recording is null");
                }
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to stop recording", e);
                Log.i(TAG, "jin old CallRecorder Failed to stop recording", e);
            }
        } else {
            Log.i(TAG, "jin old CallRecorder finishRecording mService is null");
        }

        for (RecordingProgressListener l : mProgressListeners) {
            l.onStopRecording();
        }
        mHandler.removeCallbacks(mUpdateRecordingProgressTask);
    }

    //
    // Call list listener methods.
    //
    @Override
    public void onIncomingCall(Call call) {
        // do nothing
    }

    @Override
    public void onCallListChange(final CallList callList) {
        if (!mInitialized && callList.getActiveCall() != null) {
            // we'll come here if this is the first active call
            Log.i(TAG, "jin old CallRecorder onCallListChange calling initialize()");
            initialize();
        } else {
            // we can come down this branch to resume a call that was on hold
            CallRecording active = getActiveRecording();
            if (active != null) {
                Log.i(TAG, "jin old CallRecorder onCallListChange activeRecorfing is not null");
                Call call = callList.getCallWithStateAndNumber(Call.State.ONHOLD,
                        active.phoneNumber);
                if (call != null) {
                    // The call associated with the active recording has been placed
                    // on hold, so stop the recording.
                    Log.i(TAG, "jin old CallRecorder onCallListChange call is not null,calling finishRecording");
                    finishRecording();
                } else {
                    Log.i(TAG, "jin old CallRecorder onCallListChange call is null");
                }
            } else {
                Log.i(TAG, "jin old CallRecorder onCallListChange activeRecording is null");
            }
        }
    }

    @Override
    public void onDisconnect(final Call call) {
        CallRecording active = getActiveRecording();
        if (active != null && TextUtils.equals(call.getNumber(), active.phoneNumber)) {
            // finish the current recording if the call gets disconnected
            Log.i(TAG, "jin old CallRecorder onDisconnect calling finishRecording");
            finishRecording();
        }

        // tear down the service if there are no more active calls
        if (CallList.getInstance().getActiveCall() == null) {
            Log.i(TAG, "jin old CallRecorder onDisconnect calling uninitialize");
            uninitialize();
        }
    }

    @Override
    public void onUpgradeToVideo(Call call) {}

    // allow clients to listen for recording progress updates
    public interface RecordingProgressListener {
        public void onStartRecording();
        public void onStopRecording();
        public void onRecordingTimeProgress(long elapsedTimeMs);
    }

    public void addRecordingProgressListener(RecordingProgressListener listener) {
        mProgressListeners.add(listener);
    }

    public void removeRecordingProgressListener(RecordingProgressListener listener) {
        mProgressListeners.remove(listener);
    }

    private static final int UPDATE_INTERVAL = 500;

    private Runnable mUpdateRecordingProgressTask = new Runnable() {
        @Override
        public void run() {
            CallRecording active = getActiveRecording();
            if (active != null) {
                long elapsed = System.currentTimeMillis() - active.startRecordingTime;
                for (RecordingProgressListener l : mProgressListeners) {
                    l.onRecordingTimeProgress(elapsed);
                }
            }
            mHandler.postDelayed(mUpdateRecordingProgressTask, UPDATE_INTERVAL);
        }
    };
}
