package com.kaweapp.simpleplayer.java;

import android.media.MediaMetadata;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.media.MediaMetadataCompat;
import android.text.TextUtils;

import java.io.File;

/**
 * Holder class that encapsulates a MediaMetadata and allows the actual metadata to be modified
 * without requiring to rebuild the collections the metadata is in.
 */
public class Song implements Parcelable {
    private MediaMetadataCompat mMetadata;
    private long mSongId;
    private long mSortKey;

    public Song(long songId, MediaMetadataCompat metadata, Long sortKey) {
        mMetadata = metadata;
        mSongId = songId;
        if (sortKey != null) {
            mSortKey = sortKey;
        }
    }

    public long getSongId() {
        return mSongId;
    }
    public long getSortKey() {
        return mSortKey;
    }
    public void setSortKey(long sortKey) {
        mSortKey = sortKey;
    }

    public MediaMetadataCompat getMetadata() {
        return mMetadata;
    }
    public void setMetadata(MediaMetadataCompat metadata) {
        mMetadata = metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != Song.class) {
            return false;
        }

        Song that = (Song) o;

        return mSongId == that.getSongId();
    }



    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(mSortKey);
        out.writeLong(mSongId);
        out.writeParcelable(mMetadata, flags);
    }

    public static final Parcelable.Creator<Song> CREATOR = new Parcelable.Creator<Song>() {
        public Song createFromParcel(Parcel in) {
            MediaMetadataCompat metadata = in.readParcelable(null);
            long songId = in.readLong();
            long sortKey = in.readLong();
            return new Song(songId, metadata, sortKey);
        }

        public Song[] newArray(int size) {
            return new Song[size];
        }
    };


}
