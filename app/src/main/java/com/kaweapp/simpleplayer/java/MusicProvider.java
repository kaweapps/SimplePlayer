package com.kaweapp.simpleplayer.java;


import android.content.ContentUris;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.kaweapp.simpleplayer.R;
import com.kaweapp.simpleplayer.util.MusicUtils;

/**
 * Created by ayo on 1/27/2018.
 */

public class MusicProvider {
    Context c;
    Context cc;
    private final String TAG=LogHelper.makeLogTag(MusicProvider.class);
    private ArrayList<ProviderCallBack>mProviderCallBacks;
    // Public constants
    public static final String UNKOWN = "UNKNOWN";
    enum State { NON_INITIALIZED, INITIALIZING, INITIALIZED }
    public volatile State mCurrentState = State.NON_INITIALIZED;
    // Content select criteria
    private static final String MUSIC_SELECT_FILTER = MediaStore.Audio.Media.IS_MUSIC + " != 0";
    private static final String MUSIC_SORT_ORDER = MediaStore.Audio.Media.TITLE + " ASC";
    // Uri source of this track
    public static final String CUSTOM_METADATA_TRACK_SOURCE = "__SOURCE__";
    // Sort key for this tack


    // Playlist Name --> list of Metadata
    private ConcurrentMap<String, List<MediaMetadataCompat>> mMusicListByPlaylist;

    private List<MediaMetadataCompat> mMusicList;
    private final ConcurrentMap<Long,Song>mMusicListById;
    private ConcurrentMap<String, List<MediaMetadataCompat>> mMusicListByGenre;
    Handler h;

    public MusicProvider(Context c){
        this.c = c;
        cc = c;
        mMusicListByGenre = new ConcurrentHashMap<>();
        mMusicListByPlaylist = new ConcurrentHashMap<>();
        mMusicListById = new ConcurrentHashMap<>();
        mMusicList = new ArrayList<>();
        mProviderCallBacks = new ArrayList<ProviderCallBack>();
        h= new Handler(Looper.getMainLooper());
    }

    public void getMedia(@Nullable ProviderCallBack callback){
        if (mCurrentState == State.INITIALIZED) {
            // Nothing to do, execute callback immediately
            if(callback!=null) {
                callback.onMusicCatalogReady(true);
            }
            return;
        }
        // Asynchronously load the music catalog in a separate thread
        new Thread(new BackgroundTask(callback)).start();

    }

    public synchronized boolean retrieveAllPlayLists() {
        Cursor cursor = c.getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, null, null, null, null);
        if (cursor == null) {
            Log.e(TAG, "Failed to retreive playlist: cursor is null");
            return false;
        }
        if (!cursor.moveToFirst()) {
            Log.d(TAG, "Failed to move cursor to first row (no query result)");
            cursor.close();
            return true;
        }
        int idColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists._ID);
        int nameColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists.NAME);
        int pathColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists.DATA);
        do {
            long thisId = cursor.getLong(idColumn);
            String thisPath = cursor.getString(pathColumn);
            String thisName = cursor.getString(nameColumn);
            Log.i(TAG, "PlayList ID: " + thisId + " Name: " + thisName);
            List<MediaMetadataCompat> songList = retreivePlaylistMetadata(thisId, thisPath);
            if(songList!=null){
                LogHelper.i(TAG, "Found ", songList.size(), " items for playlist name: ", thisName);
                mMusicListByPlaylist.put(thisName, songList);}
        } while (cursor.moveToNext());
        cursor.close();
        return true;
    }

    public synchronized List<MediaMetadataCompat> retreivePlaylistMetadata(
            long playlistId, String playlistPath) {
        Cursor cursor = c.getContentResolver().query(Uri.parse(playlistPath), null,
                MediaStore.Audio.Playlists.Members.PLAYLIST_ID + " == " + playlistId, null, null);
        if (cursor == null) {
            Log.e(TAG, "Failed to retreive individual playlist: cursor is null");
            return null;
        }
        if (!cursor.moveToFirst()) {
            Log.d(TAG, "Failed to move cursor to first row (no query result for playlist)");
            cursor.close();
            return null;
        }
        List<Song> songList = new ArrayList<>();
        int idColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists.Members._ID);
        int audioIdColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists.Members.AUDIO_ID);
        int orderColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists.Members.PLAY_ORDER);
        int audioPathColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists.Members.DATA);
        int audioNameColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists.Members.TITLE);
        do {
            long thisId = cursor.getLong(idColumn);
            long thisAudioId = cursor.getLong(audioIdColumn);
            long thisOrder = cursor.getLong(orderColumn);
            String thisAudioPath = cursor.getString(audioPathColumn);
            Log.i(TAG,
                    "Playlist ID: " + playlistId + " Music ID: " + thisAudioId
                            + " Name: " + audioNameColumn);
            if (!mMusicListById.containsKey(thisAudioId)) {
                LogHelper.d(TAG, "Music does not exist");
                continue;
            }
            Song song = mMusicListById.get(thisAudioId);
            song.setSortKey(thisOrder);
            songList.add(song);
        } while (cursor.moveToNext());
        cursor.close();

        List<MediaMetadataCompat> metadataList = new ArrayList<>();
        for (Song song : songList) {
            metadataList.add(song.getMetadata());
        }
        return metadataList;
    }

    private synchronized boolean retrieveMedia(){
        Cursor cursor =
                c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        null, MUSIC_SELECT_FILTER, null, MUSIC_SORT_ORDER);
        if (cursor == null) {
            Log.e(TAG, "Failed to retreive music: cursor is null");
            mCurrentState = State.NON_INITIALIZED;
            return false;
        }
        if (!cursor.moveToFirst()) {
            Log.d(TAG, "Failed to move cursor to first row (no query result)");
            cursor.close();
            return true;
        }
        int idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
        int titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
        int pathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
        do {
            Log.i(TAG,
                    "Music ID: " + cursor.getString(idColumn)
                            + " Title: " + cursor.getString(titleColumn));
            long thisId = cursor.getLong(idColumn);
            String thisPath = cursor.getString(pathColumn);
            MediaMetadataCompat metadata = retrievMediaMetadata(thisId, thisPath);
            Log.i(TAG, "MediaMetadata: " + metadata);
            if (metadata == null) {
                continue;
            }

            Song thisSong = new Song(thisId, metadata, null);
            // Construct per feature database
            mMusicList.add(metadata);
        } while (cursor.moveToNext());
        cursor.close();
        retrieveAllPlayLists();
        return true;
    }

    private synchronized MediaMetadataCompat retrievMediaMetadata(long musicId, String musicPath) {
        LogHelper.d(TAG, "getting metadata for music: ", musicPath);
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        Uri contentUri = ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, musicId);
        File file = new File(musicPath);
        if (!file.exists()) {
            LogHelper.d(TAG, "Does not exist, deleting item");
            c.getContentResolver().delete(contentUri, null, null);
            return null;
        }
        String t=file.getName();
        if(t.isEmpty()){
            t=UNKOWN;
        }
        Log.d(TAG,musicPath);
        retriever.setDataSource(c, contentUri);
        String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        String album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
        String artist =retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        String durationString =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        String genre=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
        long duration = durationString != null ? Long.parseLong(durationString) : 0;
        MediaMetadataCompat.Builder metadataBuilder =
                new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, String.valueOf(musicId))
                        .putString(CUSTOM_METADATA_TRACK_SOURCE, musicPath)
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title != null ? title : t)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album != null ? album : UNKOWN)
                        .putString(MediaMetadataCompat.METADATA_KEY_GENRE,genre !=null?genre:UNKOWN)
                        .putString(
                                MediaMetadataCompat.METADATA_KEY_ARTIST, artist != null ? artist :UNKOWN)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        byte[] albumArtData = retriever.getEmbeddedPicture();
        Bitmap bitmap;
        if (albumArtData != null) {
            bitmap = BitmapFactory.decodeByteArray(albumArtData, 0, albumArtData.length);
            bitmap = MusicUtils.resizeBitmap(bitmap, getDefaultAlbumArt());
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap);
        }
        retriever.release();
        return metadataBuilder.build();
    }

    private Bitmap getDefaultAlbumArt() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeResource(
                c.getResources(),R.drawable.albumart_mp_unknown,
                opts);
    }


    public class BackgroundTask extends Thread {

        ProviderCallBack c;
        public BackgroundTask(@Nullable ProviderCallBack c){

            mCurrentState = MusicProvider.State.INITIALIZING;
            this.c = c;
        }

        public void run(){
            if (retrieveMedia()) {
                mCurrentState = MusicProvider.State.INITIALIZED;
                buildListsByGenre();
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        if(c != null) {
                            c.onMusicCatalogReady(true);
                        }

                        for(ProviderCallBack p:mProviderCallBacks){
                            p.onMusicCatalogReady(true);
                        }
                    }
                });
            } else {
                mCurrentState = MusicProvider.State.NON_INITIALIZED;
            }

        }

    }



    public boolean isInitialized() {
        return mCurrentState == State.INITIALIZED;
    }

    public boolean isInitializing() {
        return mCurrentState == State.INITIALIZING;
    }


    public Iterable<MediaMetadataCompat> getMusicList() {
        return mMusicList;
    }

    public List<MediaBrowserCompat.MediaItem> getChildren(String mediaId) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        if (!MediaIDHelper.isBrowseable(mediaId)) {
            return mediaItems;
        }

        if (MediaIDHelper.MEDIA_ID_MUSICS_BY_SONG.equals(mediaId)) {
            mediaItems.addAll(getAllSongs(getMusicList(),mediaId));
        }

        else {
            LogHelper.w(TAG, "Skipping unmatched mediaId: ", mediaId);
        }
        return mediaItems;
    }


    private List<MediaBrowserCompat.MediaItem> getAllSongs(Iterable<MediaMetadataCompat> songList, String parentId) {
        List<MediaBrowserCompat.MediaItem>mediaItems = new ArrayList<>();

        for (MediaMetadataCompat metadata : songList) {
            String hierarchyAwareMediaID = MediaIDHelper.createMediaID(metadata.getDescription().getMediaId(), parentId);
            Bundle songExtra = new Bundle();
            songExtra.putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                    metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));
            String title = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
            String artistName = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
            MediaBrowserCompat.MediaItem item = new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                    .setMediaId(hierarchyAwareMediaID)
                    .setTitle(title)
                    .setSubtitle(artistName)
                    .setExtras(songExtra)
                    .setIconBitmap(metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
                    .setDescription(metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION))
                    .setMediaUri(Uri.parse(metadata.getString(CUSTOM_METADATA_TRACK_SOURCE )))
                    .build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
            mediaItems.add(item);
        }

        return mediaItems;
    }




    private synchronized void buildListsByGenre() {
        ConcurrentMap<String, List<MediaMetadataCompat>> newMusicListByGenre = new ConcurrentHashMap<>();

        for (Song m : mMusicListById.values()) {
            String genre = m.getMetadata().getString(MediaMetadataCompat.METADATA_KEY_GENRE);
            List<MediaMetadataCompat> list = newMusicListByGenre.get(genre);
            if (list == null) {
                list = new ArrayList<>();
                newMusicListByGenre.put(genre, list);
            }
            list.add(m.getMetadata());
        }
        mMusicListByGenre = newMusicListByGenre;
    }


    public interface ProviderCallBack{
        public void onMusicCatalogReady(boolean success);
    }


    public void addCallBacks(ProviderCallBack p){
        mProviderCallBacks.add(p);

    }



}
