package com.kaweapp.simpleplayer.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.kaweapp.simpleplayer.R;
import com.kaweapp.simpleplayer.adapter.SongAdapter;
import com.kaweapp.simpleplayer.java.Constants;
import com.kaweapp.simpleplayer.java.MediaIDHelper;
import com.kaweapp.simpleplayer.service.MusicService;
import com.kaweapp.simpleplayer.util.MusicUtils;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SongAdapter.OnItemClicked, View.OnClickListener {

    public static final String EXTRA_CURRENT_MEDIA_DESCRIPTION = "com.kaweapp.simpleplayer.CURRENT_MEDIA_DESCRIPTION";
    public static final String EXTRA_START_FULLSCREEN = "com.kaweapp.simpleplayer.EXTRA_START_FULLSCREEN";

    RecyclerView recyclerView;
    Toolbar toolbar;
    SongAdapter mSongAdapter;

    MediaBrowserCompat mMediaBrowser;

    TextView title;
    TextView description;
    ImageView image;
    ImageView mPlayPause;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerview);
        toolbar = findViewById(R.id.toolbar);
        title = findViewById(R.id.title);
        description = findViewById(R.id.description);
        image = findViewById(R.id.image);
        mPlayPause = findViewById(R.id.play);
        mPlayPause.setOnClickListener(this);

        mSongAdapter = new SongAdapter(this, new ArrayList<MediaBrowserCompat.MediaItem>(), this);
        recyclerView.setAdapter(mSongAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        mMediaBrowser = new MediaBrowserCompat(this, new ComponentName(this, MusicService.class), mConnectionCallbacks, null);
    }

    @Override
    public void onStart() {
        super.onStart();
        mMediaBrowser.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        // (see "stay in sync with the MediaSession")
        MediaControllerCompat controllerCompat = MediaControllerCompat.getMediaController(this);
        if (controllerCompat != null) {
            controllerCompat.unregisterCallback(controllerCallback);
        }

        mMediaBrowser.disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MediaControllerCompat controllerCompat=MediaControllerCompat.getMediaController(this);
        if( controllerCompat!=null && controllerCompat.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING ) {
            // controllerCompat.getTransportControls().pause();
        }

        mMediaBrowser.disconnect();
    }

    @Override
    public void itemClicked(MediaBrowserCompat.MediaItem item, int queueid) {
        if (item.isPlayable()) {
            Bundle b = MusicUtils.getMetadataBundle(item, queueid);

            MediaControllerCompat.getMediaController(MainActivity.this).getTransportControls().
                    playFromUri(item.getDescription().getMediaUri(), b);
        }
    }

    @Override
    public void onClick(View v) {
        if(v.getId() == mPlayPause.getId()) {
            PlaybackStateCompat state = MediaControllerCompat.getMediaController(MainActivity.this).getPlaybackState();
            if (state != null) {
                MediaControllerCompat.TransportControls ts = MediaControllerCompat.getMediaController(MainActivity.this).getTransportControls();
                int pbState = state.getState();
                if (pbState == PlaybackStateCompat.STATE_PLAYING) {
                    ts.pause();
                }
                else {
                    ts.play();
                }
            }

        }
    }

    MediaBrowserCompat.SubscriptionCallback mSubscriptionCallback = new MediaBrowserCompat.SubscriptionCallback() {
        @Override
        public void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowserCompat.MediaItem> children) {
            super.onChildrenLoaded(parentId, children);

            if(children.isEmpty()) return;

            ArrayList<MediaBrowserCompat.MediaItem> i = new ArrayList<>();

            for (MediaBrowserCompat.MediaItem item : children) {
                i.add(item);
            }

            mSongAdapter.setItems(i);

            initPlayer(i.get(0));

        }
    };


    private final MediaBrowserCompat.ConnectionCallback mConnectionCallbacks =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    // Get the token for the MediaSession
                    MediaSessionCompat.Token token = mMediaBrowser.getSessionToken();

                    try {
                        MediaControllerCompat mediaController = new MediaControllerCompat(MainActivity.this, token);
                        MediaControllerCompat.setMediaController(MainActivity.this, mediaController);
                        mediaController.registerCallback(controllerCallback);

                        mMediaBrowser.subscribe(MediaIDHelper.MEDIA_ID_MUSICS_BY_SONG, mSubscriptionCallback);

                    } catch (RemoteException r) {
                        Log.e(MainActivity.class.getSimpleName(), r.getMessage());
                    }
                }

                @Override
                public void onConnectionSuspended() {
                    // The Service has crashed. Disable transport controls until it automatically reconnects

                }

                @Override
                public void onConnectionFailed() {
                    // The Service has refused our connection
                }
            };

    MediaControllerCompat.Callback controllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {
                    title.setText(metadata.getDescription().getTitle());
                    description.setText(metadata.getDescription().getSubtitle());
                    Bitmap b = metadata.getDescription().getIconBitmap();
                    if (b == null) {
                        image.setImageDrawable(getResources().getDrawable(R.drawable.albumart_mp_unknown));
                    } else {
                        image.setImageBitmap(b);
                    }
                }

                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    if (state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                        mPlayPause.setImageDrawable(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_pause_white_24dp));
                        mPlayPause.setContentDescription(getString(R.string.btn_pause));
                    }
                    else {
                        mPlayPause.setImageDrawable(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_play_arrow_white_24dp));
                        mPlayPause.setContentDescription(getString(R.string.btn_play));
                    }
                }
            };


    private void initPlayer(MediaBrowserCompat.MediaItem item) {
        MediaMetadataCompat m = MediaControllerCompat.getMediaController(MainActivity.this).getMetadata();
        PlaybackStateCompat state = MediaControllerCompat.getMediaController(MainActivity.this).getPlaybackState();

        if (state != null) {
            int pbState = state.getState();
            if (pbState == PlaybackStateCompat.STATE_PLAYING) {
                mPlayPause.setImageDrawable(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_pause_white_24dp));
                mPlayPause.setContentDescription(getString(R.string.btn_pause));

            } else {
                mPlayPause.setImageDrawable(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_play_arrow_white_24dp));
                mPlayPause.setContentDescription(getString(R.string.btn_play));

            }
        }

        MediaDescriptionCompat c;

        if (m != null) {
            c = m.getDescription();
        }
        else {
            c = item.getDescription();
        }

        title.setText(c.getTitle());
        description.setText(c.getSubtitle());

        Bitmap b = c.getIconBitmap();

        if (b == null) {
            image.setImageDrawable(getResources().getDrawable(R.drawable.albumart_mp_unknown));
        } else {
            image.setImageBitmap(b);
        }

    }
}
