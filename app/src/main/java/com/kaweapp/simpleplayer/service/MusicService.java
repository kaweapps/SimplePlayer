package com.kaweapp.simpleplayer.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import com.kaweapp.simpleplayer.R;
import com.kaweapp.simpleplayer.activity.MainActivity;
import com.kaweapp.simpleplayer.java.Constants;
import com.kaweapp.simpleplayer.java.LogHelper;
import com.kaweapp.simpleplayer.java.MediaPlayerHelper;
import com.kaweapp.simpleplayer.java.MusicProvider;
//import com.kaweapp.simpleplayer.java.QueueManager;
import com.kaweapp.simpleplayer.util.PackageValidator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MusicService extends MediaBrowserServiceCompat {

    private static final String MY_MEDIA_ROOT_ID = "media_root_id";
    private static final String MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id";
    public static final String ACTION_STOP = "com.kaweapp.simpleplayer.stop";
    private static final String CHANNEL_ID = "com.kaweapp.simpleplayer.MUSIC_CHANNEL_ID";
    private static final String TAG = LogHelper.makeLogTag(MusicService.class);

    boolean isPaused;
    private static final int NOTIFICATION_ID = 412;
    private static final int REQUEST_CODE = 100;

    MediaSessionCompat mMediaSession;
    private PlaybackStateCompat.Builder mStateBuilder;
    private PackageValidator mPackageValidator;
    MusicProvider mMusicProvider;
    MediaPlayer mMediaPlayer;

    NotificationManager mNotificationManager;
    private PendingIntent mStopIntent;
    Intent intent;


    @Override
    public void onCreate() {
        super.onCreate();

        initMediaPlayer();

        mMusicProvider = new MusicProvider(this);

        // To make the app more responsive, fetch and cache catalog information now.
        // This can help improve the response time in the method
        // {@link #onLoadChildren(String, Result<List<MediaItem>>) onLoadChildren()}.
        mMusicProvider.getMedia(null);
        mPackageValidator = new PackageValidator(this);
        initMediaSession();
        isPaused = false;
        initNoisyReceiver();

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mStopIntent = PendingIntent.getBroadcast(this, REQUEST_CODE, new Intent(ACTION_STOP).setPackage(this.getPackageName()), PendingIntent.FLAG_CANCEL_CURRENT);
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {

        if (!mPackageValidator.isCallerAllowed(this, clientPackageName, clientUid)) {
            return new MediaBrowserServiceCompat.BrowserRoot(MY_EMPTY_MEDIA_ROOT_ID, null);
        }

        startService(new Intent(getApplicationContext(),MusicService.class));

        return new BrowserRoot(MY_MEDIA_ROOT_ID, null);
    }


    @Override
    public void onLoadChildren(final String parentMediaId, final Result<List<MediaBrowserCompat.MediaItem>> result) {

        if (TextUtils.equals(MY_EMPTY_MEDIA_ROOT_ID, parentMediaId)) {
            result.sendResult(null);
            return;
        }

        if (mMusicProvider.isInitialized()) {

            result.sendResult(mMusicProvider.getChildren(parentMediaId));
        }

        else if(mMusicProvider.isInitializing()){
            result.detach();
            mMusicProvider.addCallBacks(new MusicProvider.ProviderCallBack() {
                @Override
                public void onMusicCatalogReady(boolean success) {
                    if (success) {

                        result.sendResult(mMusicProvider.getChildren(parentMediaId));
                    } else {
                        result.sendResult(new ArrayList<MediaBrowserCompat.MediaItem>());
                    }

                }
            });
        }
        else{
            result.detach();

            mMusicProvider.getMedia(new MusicProvider.ProviderCallBack() {
                @Override
                public void onMusicCatalogReady(boolean success) {
                    if (success) {
                        result.sendResult(mMusicProvider.getChildren(parentMediaId));
                    } else {
                        result.sendResult(new ArrayList<MediaBrowserCompat.MediaItem>());
                    }

                }
            });
        }
    }

    @Override
    public void onDestroy() {

        if (mMediaPlayer != null)
            mMediaPlayer.release();

        mMediaSession.release();

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.abandonAudioFocus(mOnAudioFocusChangeListener);
        unregisterReceiver(mNoisyReceiver);
        NotificationManagerCompat.from(this).cancel(1);
    }

    /*
     * Handle case when user swipes the app away from the recents apps list by
     * stopping the service (and any ongoing playback).
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mMediaSession, intent);
        return super.onStartCommand(intent, flags, startId);
    }


    private class MediaSessionCallback extends MediaSessionCompat.Callback {

        @Override
        public void onPlay() {
            if(mMediaPlayer != null && !mMediaPlayer.isPlaying()){

                if(!successFullyRetrievedAudioFocus()) return;

                mMediaPlayer.start();
                mMediaSession.setActive(true);

                setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);

                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
                    createNotificationChannel();
                }
                setNotification(false);
            }
        }

        @Override
        public void onSkipToQueueItem(long queueId) {}

        @Override
        public void onSeekTo(long position) {}

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {}

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {

            if(!successFullyRetrievedAudioFocus()) return;

            try {
                startService(new Intent(getApplicationContext(), MusicService.class));

                mMediaPlayer.release();
                initMediaPlayer();
                mMediaPlayer.setDataSource(MusicService.this, uri);
                mMediaPlayer.prepareAsync() ;
                mMediaPlayer.setOnPreparedListener(new MediaPlayerHelper());

                setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
                setMetaData(extras);

                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
                    createNotificationChannel();
                }

                setNotification(false);

            }
            catch(IOException e){
                Toast.makeText(MusicService.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onPause() {
            if(mMediaPlayer != null && mMediaPlayer.isPlaying()){
                mMediaPlayer.pause();

                setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);

                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
                    createNotificationChannel();
                }
                setNotification(true);

                stopForeground(false);
            }

        }

        @Override
        public void onStop() {

            if(mMediaPlayer != null){
                mMediaPlayer.stop();
            }
        }

        @Override
        public void onSkipToNext() {}

        @Override
        public void onSkipToPrevious() {}

        @Override
        public void onCustomAction(@NonNull String action, Bundle extras) {}


        @Override
        public void onPlayFromSearch(final String query, final Bundle extras) {}

        @Override
        public void onSetRepeatMode(int repeatMode) {}

        @Override
        public void onSetShuffleMode(int shuffleMode) {}
    }


    void  initMediaSession(){
        mMediaSession = new MediaSessionCompat(this, TAG);

        // Enable callbacks from MediaButtons and TransportControls
        mMediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
        mStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE);

        mStateBuilder.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(Constants.CUSTOM_ACTION_DELETE, Constants.CUSTOM_ACTION_DELETE, R.drawable.ic_delete_black_24dp).build());
        mMediaSession.setPlaybackState(mStateBuilder.build());
        mMediaSession.setCallback(new MediaSessionCallback());
        mMediaSession.setActive(true);
        // Set the session's token so that client activities can communicate with it.
        setSessionToken(mMediaSession.getSessionToken());

    }

    void setNotification(boolean isPaused){
        MediaControllerCompat controller = mMediaSession.getController();
        MediaMetadataCompat mediaMetadata = controller.getMetadata();
        MediaDescriptionCompat description = null;

        if(mediaMetadata != null) {
            description = mediaMetadata.getDescription();
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);

        if(description != null) {
            // Add the metadata for the currently playing track
            builder.setContentTitle(description.getTitle())
                    .setContentText(description.getSubtitle())
                    .setSubText(description.getDescription())
                    .setLargeIcon(description.getIconBitmap());
        }

        //add an onclick event
        builder.setContentIntent(createContentIntent(null))

                // Stop the service when the notification is swiped away
                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                        PlaybackStateCompat.ACTION_STOP))

                // Make the transport controls visible on the lockscreen
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)

                // Add an app icon and set its accent color
                // Be careful about the color
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setColor(ContextCompat.getColor(this, R.color.colorPrimary));

        // Add a pause button

        builder.addAction(new NotificationCompat.Action(R.drawable.ic_skip_previous_white_24dp,"",MediaButtonReceiver.buildMediaButtonPendingIntent(this,PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)))
                .addAction(new NotificationCompat.Action(isPaused ? R.drawable.ic_play_arrow_white_24dp:R.drawable.ic_pause_white_24dp,"",MediaButtonReceiver.buildMediaButtonPendingIntent(this,PlaybackStateCompat.ACTION_PLAY_PAUSE)))
                .addAction(new NotificationCompat.Action(R.drawable.ic_skip_next_white_24dp,"",MediaButtonReceiver.buildMediaButtonPendingIntent(this,PlaybackStateCompat.ACTION_SKIP_TO_NEXT)));
        // Take advantage of MediaStyle features
        builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mMediaSession.getSessionToken())
                .setShowActionsInCompactView(0,1,2)

                // Add a cancel button
                .setShowCancelButton(true)
                .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                        PlaybackStateCompat.ACTION_STOP)));
        startForeground(NOTIFICATION_ID, builder.build());
    }

    private PendingIntent createContentIntent(MediaDescriptionCompat description) {
        Intent openUI = new Intent(this, MainActivity.class);
        openUI.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        openUI.putExtra(MainActivity.EXTRA_START_FULLSCREEN, true);
        if (description != null) {
            openUI.putExtra(MainActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION, description);
        }
        return PendingIntent.getActivity(this, REQUEST_CODE, openUI,
                PendingIntent.FLAG_CANCEL_CURRENT);
    }



    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        if (mNotificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel notificationChannel =
                    new NotificationChannel(CHANNEL_ID,getString(R.string.notification_channel),
                            NotificationManager.IMPORTANCE_LOW);

            notificationChannel.setDescription(getString(R.string.notification_channel_description));
            mNotificationManager.createNotificationChannel(notificationChannel);
        }
    }

    private boolean successFullyRetrievedAudioFocus(){
        AudioManager a = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        int result = a.requestAudioFocus(mOnAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        return result == AudioManager.AUDIOFOCUS_GAIN;
    }



    private BroadcastReceiver mNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( mMediaPlayer != null && mMediaPlayer.isPlaying() ) {
                mMediaPlayer.pause();
                setMediaPlaybackState(PlaybackState.STATE_PAUSED);
                setNotification(true);
            }
        }
    };

    AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener= new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int i) {
            switch( i ) {
                case AudioManager.AUDIOFOCUS_LOSS: {
                    if( mMediaPlayer.isPlaying() ) {
                        mMediaPlayer.pause();
                        setMediaPlaybackState(PlaybackState.STATE_PAUSED);
                        setNotification(true);
                    }
                    break;
                }
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: {
                    if(mMediaPlayer.isPlaying()) {
                        mMediaPlayer.pause();
                        isPaused = true;
                        setMediaPlaybackState(PlaybackState.STATE_PAUSED);
                        setNotification(true);
                    }
                    break;
                }
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: {
                    if( mMediaPlayer != null ) {
                        mMediaPlayer.setVolume(0.3f, 0.3f);
                    }
                    break;
                }
                case AudioManager.AUDIOFOCUS_GAIN: {

                    if( mMediaPlayer != null ) {
                        if( !mMediaPlayer.isPlaying() && isPaused ) {
                            mMediaPlayer.start();
                            setMediaPlaybackState(PlaybackState.STATE_PLAYING);
                            isPaused=false;
                            setNotification(false);
                        }
                        mMediaPlayer.setVolume(1.0f, 1.0f);
                    }
                    break;
                }

            }

        }
    };

    void setMediaPlaybackState(int state){
        PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder();
        if(state == PlaybackStateCompat.STATE_PLAYING){
            b.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE|PlaybackStateCompat.ACTION_PAUSE);
        }
        else{
            b.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE|PlaybackStateCompat.ACTION_PLAY);
        }

        long pos;

        if(mMediaPlayer != null){
            pos = mMediaPlayer.getCurrentPosition();
        }
        else{
            pos = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        }

        b.setState(state,pos,1.0f, SystemClock.elapsedRealtime());
        b.setActions(getAvailableActions());
        mMediaSession.setPlaybackState(b.build());

    }

    void setMetaData(Bundle b){
        byte[]n = b.getByteArray(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);

        Bitmap bitmap = null;

        if(n != null){
            bitmap = BitmapFactory.decodeByteArray(n, 0, n.length);
        }

        MediaMetadataCompat m = new MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,bitmap)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,b.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,b.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,b.getLong(MediaMetadataCompat.METADATA_KEY_DURATION))
                .build();

        mMediaSession.setMetadata(m);
    }


    private void initMediaPlayer(){
        mMediaPlayer=new MediaPlayer();
        mMediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setVolume(1.0f,1.0f);
        mMediaPlayer.setOnErrorListener(new MediaPlayerHelper());
        mMediaPlayer.setOnCompletionListener(  new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                setMediaPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                setNotification(true);
            }
        });
    }

    private void initNoisyReceiver() {
        //Handles headphones coming unplugged.
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(mNoisyReceiver, filter);
    }

    private long getAvailableActions() {
        long actions =
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
                        PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT;

        if (mMediaPlayer.isPlaying()) {
            actions |= PlaybackStateCompat.ACTION_PAUSE;
        } else {
            actions |= PlaybackStateCompat.ACTION_PLAY;
        }

        return actions;
    }


}
