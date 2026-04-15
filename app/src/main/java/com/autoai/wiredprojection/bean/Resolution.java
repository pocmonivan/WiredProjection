package com.autoai.wiredprojection.bean;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

public class Resolution implements Parcelable{
    private int mWidth;
    private int mHeight;

    public Resolution(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    protected Resolution(Parcel in) {
        mWidth = in.readInt();
        mHeight = in.readInt();
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public static final Creator<Resolution> CREATOR = new Creator<Resolution>() {
        @Override
        public Resolution createFromParcel(Parcel in) {
            return new Resolution(in);
        }

        @Override
        public Resolution[] newArray(int size) {
            return new Resolution[size];
        }
    };

    @Override
    public String toString() {
        return mWidth + "x" + mHeight;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(mWidth);
        parcel.writeInt(mHeight);
    }

    public void readFromParcel(Parcel source) {
        mWidth = source.readInt();
        mHeight = source.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Resolution)) return false;
        Resolution that = (Resolution) o;
        return mWidth == that.mWidth && mHeight == that.mHeight;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mWidth, mHeight);
    }
}
