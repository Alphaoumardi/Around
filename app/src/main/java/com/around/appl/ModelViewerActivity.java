package com.around.appl;

import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.rendering.ModelRenderable;

public class ModelViewerActivity extends AppCompatActivity {

    private ArFragment arFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_viewer);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        String modelUrl = getIntent().getStringExtra("url");
        if (modelUrl == null || modelUrl.isEmpty()) {
            Toast.makeText(this, "Model URL missing!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadModel(modelUrl);
    }

    private void loadModel(String modelUrl) {
        ModelRenderable.builder()
                .setSource(this, Uri.parse(modelUrl))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(renderable -> {
                    // Let user tap to place model
                    arFragment.setOnTapArPlaneListener((hitResult, plane, motionEvent) -> {
                        Anchor anchor = hitResult.createAnchor();
                        AnchorNode anchorNode = new AnchorNode(anchor);
                        anchorNode.setRenderable(renderable);
                        arFragment.getArSceneView().getScene().addChild(anchorNode);
                    });
                })
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Failed to load model: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                    return null;
                });
    }
}

