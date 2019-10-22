package com.kaweapp.simpleplayer.adapter;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.media.MediaBrowserCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kaweapp.simpleplayer.R;
import com.kaweapp.simpleplayer.util.MusicUtils;

import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {

    private List<MediaBrowserCompat.MediaItem> items;

    private final Bitmap mDefaultAlbumIcon;

    private OnItemClicked onItemClicked;

    public SongAdapter(Activity activity, List<MediaBrowserCompat.MediaItem> items, OnItemClicked onItemClicked) {
        super();

        this.items = items;
        mDefaultAlbumIcon = BitmapFactory.decodeResource(activity.getResources(), R.drawable.ic_default_art);
        this.onItemClicked = onItemClicked;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.song_adapter_layout, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.title.setText(items.get(position).getDescription().getSubtitle());
        holder.description.setText(items.get(position).getDescription().getTitle());

        Bitmap b = items.get(position).getDescription().getIconBitmap();
        if (b == null) {
            holder.imageView.setImageBitmap(mDefaultAlbumIcon);
        } else {
            holder.imageView.setImageBitmap(MusicUtils.resizeBitmap(b, mDefaultAlbumIcon));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }


    public void setItems(ArrayList<MediaBrowserCompat.MediaItem>items){
        this.items.clear();
        this.items.addAll(items);
        notifyDataSetChanged();
    }




    public class ViewHolder extends RecyclerView.ViewHolder {

        ImageView imageView;
        TextView title;
        TextView description;

        public ViewHolder(View view) {
            super(view);

            imageView = view.findViewById(R.id.image);
            title = view.findViewById(R.id.title);
            description = view.findViewById(R.id.description);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    if(position == RecyclerView.NO_POSITION) return;

                    MediaBrowserCompat.MediaItem m = items.get(position);
                    onItemClicked.itemClicked(m, position);
                }
            });
        }
    }

    public interface OnItemClicked {
        void itemClicked(MediaBrowserCompat.MediaItem item, int position);
    }
}
