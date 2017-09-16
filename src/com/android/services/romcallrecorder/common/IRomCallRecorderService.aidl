package com.android.services.romcallrecorder.common;

import com.android.services.romcallrecorder.common.RomCallRecording;

/**
 * Service for recording phone calls.  Only one recording may be active at a time
 * (i.e. every call to startRecording should be followed by a call to stopRecording).
 */
interface IRomCallRecorderService {

    /**
     * Start a recording.
     *
     * @return true if recording started successfully
     */
    boolean startRecording(String phoneNumber, long creationTime);

    /**
     * stops the current recording
     *
     * @return call recording data including the output filename
     */
    RomCallRecording stopRecording();

    /**
     * Recording status
     *
     * @return true if there is an active recording
     */
    boolean isRecording();

    /**
     * Get recording currently in progress
     *
     * @return call recording object
     */
    RomCallRecording getActiveRecording();

}
