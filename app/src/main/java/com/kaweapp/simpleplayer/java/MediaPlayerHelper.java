package com.kaweapp.simpleplayer.java;

import android.media.MediaPlayer;

public class MediaPlayerHelper implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener {

    @Override public void onPrepared(MediaPlayer player) {
        player.start();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mp.reset();
        return true;

    }

}
