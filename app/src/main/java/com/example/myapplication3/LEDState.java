package com.example.myapplication3;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * LED状态类，实现Parcelable接口用于跨进程传
 */
public class LEDState implements Parcelable {
    public boolean powerOn = false;
    public int brightness = 50;
    public String mode = "NORMAL";
    public int workBrightness = 0;
    public int mmc2Brightness = 0;
    public boolean workFound = false;
    public boolean mmc2Found = false;
    
    public LEDState() {
    }
    
    protected LEDState(Parcel in) {
        powerOn = in.readByte() != 0;
        brightness = in.readInt();
        mode = in.readString();
        workBrightness = in.readInt();
        mmc2Brightness = in.readInt();
        workFound = in.readByte() != 0;
        mmc2Found = in.readByte() != 0;
    }
    
    public static final Creator<LEDState> CREATOR = new Creator<LEDState>() {
        @Override
        public LEDState createFromParcel(Parcel in) {
            return new LEDState(in);
        }
        
        @Override
        public LEDState[] newArray(int size) {
            return new LEDState[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (powerOn ? 1 : 0));
        dest.writeInt(brightness);
        dest.writeString(mode);
        dest.writeInt(workBrightness);
        dest.writeInt(mmc2Brightness);
        dest.writeByte((byte) (workFound ? 1 : 0));
        dest.writeByte((byte) (mmc2Found ? 1 : 0));
    }
    
    @Override
    public String toString() {
        return "LEDState{" +
                "powerOn=" + powerOn +
                ", brightness=" + brightness +
                ", mode='" + mode + "'" +
                ", workBrightness=" + workBrightness +
                ", mmc2Brightness=" + mmc2Brightness +
                ", workFound=" + workFound +
                ", mmc2Found=" + mmc2Found +
                '}';
    }
}