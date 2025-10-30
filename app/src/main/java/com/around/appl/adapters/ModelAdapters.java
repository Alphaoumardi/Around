package com.around.appl.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.around.appl.R;
import com.around.appl.models.ModelItem;
import com.squareup.picasso.Picasso;
import java.util.List;

public class ModelAdapters extends RecyclerView.Adapter<ModelAdapters.ViewHolder> {

    private List<ModelItem> modelList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ModelItem model);
    }

    public ModelAdapters(List<ModelItem> modelList, OnItemClickListener listener) {
        this.modelList = modelList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_model, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ModelItem model = modelList.get(position);
        holder.modelName.setText(model.getName());
        Picasso.get().load(R.drawable.ic_camera_roll).into(holder.thumbnail); // placeholder
        holder.itemView.setOnClickListener(v -> listener.onItemClick(model));
    }

    @Override
    public int getItemCount() {
        return modelList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView modelName;
        ImageView thumbnail;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            modelName = itemView.findViewById(R.id.modelName);
            thumbnail = itemView.findViewById(R.id.modelThumbnail);
        }
    }
}

