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

package com.android.services.romcallrecorder.common;

import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

public final class RomCallRecording implements Parcelable {
    public String phoneNumber;
    public long creationTime;
    public String fileName;
    public long startRecordingTime;

    private static final String PUBLIC_DIRECTORY_NAME = "RomCallRecordings";

    public static final Parcelable.Creator<RomCallRecording> CREATOR = new
            Parcelable.Creator<RomCallRecording>() {
                public RomCallRecording createFromParcel(Parcel in) {
                    return new RomCallRecording(in);
                }

                public RomCallRecording[] newArray(int size) {
                    return new RomCallRecording[size];
                }
            };

    public RomCallRecording(String phoneNumber, long creationTime,
            String fileName, long startRecordingTime) {
        this.phoneNumber = phoneNumber;
        this.creationTime = creationTime;
        this.fileName = fileName;
        this.startRecordingTime = startRecordingTime;
    }

    public RomCallRecording(Parcel in) {
        phoneNumber = in.readString();
        creationTime = in.readLong();
        fileName = in.readString();
        startRecordingTime = in.readLong();
    }

    public File getFile() {
        File dir = Environment.getExternalStoragePublicDirectory(PUBLIC_DIRECTORY_NAME);
        return new File(dir, fileName);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(phoneNumber);
        out.writeLong(creationTime);
        out.writeString(fileName);
        out.writeLong(startRecordingTime);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "jin phoneNumber=" + phoneNumber + ", creationTime=" + creationTime +
                ", fileName=" + fileName + ", startRecordingTime=" + startRecordingTime;
    }
}
