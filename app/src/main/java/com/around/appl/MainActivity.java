package com.around.appl;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.around.appl.adapters.ModelAdapters;
import com.around.appl.models.ModelItem;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private RecyclerView recyclerView;
    private ModelAdapters adapter;
    private List<ModelItem> modelList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();
        recyclerView = findViewById(R.id.modelRecycler);
        FloatingActionButton addModelButton = findViewById(R.id.addModelButton);

        modelList = new ArrayList<>();
        adapter = new ModelAdapters(modelList, model -> {
            Intent intent = new Intent(this, ModelViewerActivity.class);
            intent.putExtra("modelUrl", model.getFileUrl());
            intent.putExtra("modelName", model.getName());
            startActivity(intent);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadModels();

        addModelButton.setOnClickListener(v -> {
            startActivity(new Intent(this, CaptureActivity.class));
            Log.i("AddModel","Feature coming soon");
        });
    }

    private void loadModels() {
        db.collection("models")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    modelList.clear();
                    assert value != null;
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        ModelItem model = doc.toObject(ModelItem.class);
                        modelList.add(model);
                    }
                    adapter.notifyDataSetChanged();
                });
    }
}
