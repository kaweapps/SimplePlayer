package com.kaweapp.simpleplayer.util;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;

import com.kaweapp.simpleplayer.java.Constants;

import java.io.ByteArrayOutputStream;

public class MusicUtils {

    public static Bitmap resizeBitmap(Bitmap bitmap, Bitmap ref) {
        int w = ref.getWidth();
        int h = ref.getHeight();
        return Bitmap.createScaledBitmap(bitmap, w, h, false);
    }

    public static Bundle getMetadataBundle(MediaBrowserCompat.MediaItem item, int queueId) {
        Bundle b = new Bundle();
        b.putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getDescription().getTitle().toString());
        b.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, item.getDescription().getSubtitle().toString());
        b.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID,item.getMediaId());
        b.putInt(Constants.QUEUE_ID,queueId);

        Bundle bundle = item.getDescription().getExtras();
        long duration = bundle.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        b.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        Bitmap p = item.getDescription().getIconBitmap();
        if (p != null) {
            byte[] y = MusicUtils.convertBitmapToByteArray(p);
            b.putByteArray(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, y);
        } else {
            b.putByteArray(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, null);
        }

        return b;
    }


    public  static byte[] convertBitmapToByteArray(Bitmap b) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        return byteArray;
    }


}
