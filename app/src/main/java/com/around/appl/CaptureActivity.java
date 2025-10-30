package com.around.appl;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.ar.core.Frame;
import com.google.ar.core.PointCloud;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CaptureActivity extends AppCompatActivity {

    private ArFragment arFragment;
    private MaterialButton btnCapture;
    private ProgressBar progressBar;

    private FirebaseStorage storage;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);
        btnCapture = findViewById(R.id.btnCapture);
        progressBar = findViewById(R.id.uploadProgress);

        storage = FirebaseStorage.getInstance();
        db = FirebaseFirestore.getInstance();

        btnCapture.setOnClickListener(v -> captureAndUpload());
    }

    private void captureAndUpload() {
        Frame frame = arFragment.getArSceneView().getArFrame();
        if (frame == null) {
            Toast.makeText(this, "No AR frame available yet", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(android.view.View.VISIBLE);
        btnCapture.setEnabled(false);

        // Capture RGB image from AR view
        SurfaceView surfaceView = (SurfaceView) arFragment.getArSceneView();
        Bitmap bitmap = Bitmap.createBitmap(surfaceView.getWidth(), surfaceView.getHeight(), Bitmap.Config.ARGB_8888);
        PixelCopy.request(surfaceView, bitmap, copyResult -> {
            if (copyResult == PixelCopy.SUCCESS) {
                saveAndUpload(bitmap, frame);
            } else {
                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(android.view.View.GONE);
                btnCapture.setEnabled(true);
            }
        }, new android.os.Handler());
    }

    private void saveAndUpload(Bitmap bitmap, Frame frame) {
        // Save RGB capture locally
        try {
            File imageFile = new File(getCacheDir(), "capture_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream out = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.close();

            // Optional: also save raw point cloud
            PointCloud pointCloud = frame.acquirePointCloud();
            File plyFile = savePointCloud(pointCloud);
            pointCloud.release();

            uploadToFirebase(imageFile, plyFile);

        } catch (IOException e) {
            Toast.makeText(this, "Error saving file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(android.view.View.GONE);
            btnCapture.setEnabled(true);
        }
    }

    private File savePointCloud(PointCloud pointCloud) throws IOException {
        File plyFile = new File(getCacheDir(), "cloud_" + System.currentTimeMillis() + ".ply");
        FileOutputStream out = new FileOutputStream(plyFile);

        float[] points = pointCloud.getPoints().array();
        int pointCount = pointCloud.getPoints().remaining() / 4;

        // Basic PLY header
        String header = "ply\nformat ascii 1.0\n" +
                "element vertex " + pointCount + "\n" +
                "property float x\nproperty float y\nproperty float z\nend_header\n";
        out.write(header.getBytes());

        // Write XYZ points
        for (int i = 0; i < pointCount; i++) {
            int base = i * 4;
            String line = points[base] + " " + points[base + 1] + " " + points[base + 2] + "\n";
            out.write(line.getBytes());
        }

        out.close();
        return plyFile;
    }

    private void uploadToFirebase(File imageFile, File plyFile) {
        String uid = UUID.randomUUID().toString();
        var storageRef = storage.getReference().child("models/" + uid + ".ply");

        storageRef.putFile(Uri.fromFile(plyFile))
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    saveMetadata(uri.toString(), imageFile);
                }))
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    progressBar.setVisibility(android.view.View.GONE);
                    btnCapture.setEnabled(true);
                });
    }

    private void saveMetadata(String modelUrl, File imageFile) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Captured Model");
        data.put("description", "Created at " + System.currentTimeMillis());
        data.put("url", modelUrl);

        db.collection("models").add(data)
                .addOnSuccessListener(doc -> {
                    progressBar.setVisibility(android.view.View.GONE);
                    btnCapture.setEnabled(true);
                    Toast.makeText(this, "Model uploaded!", Toast.LENGTH_SHORT).show();
                    finish(); // Return to MainActivity
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save metadata", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(android.view.View.GONE);
                    btnCapture.setEnabled(true);
                });
    }
}
