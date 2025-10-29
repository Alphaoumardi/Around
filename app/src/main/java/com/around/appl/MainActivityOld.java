package com.around.appl;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.ar.core.Frame;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Camera;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.FloatBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class MainActivityOld extends AppCompatActivity {

    private static final String TAG = "ARCapture";
    private ArFragment arFragment;
    private boolean capturing = false;

    // Accumulate world-space points
    private final ArrayList<float[]> accumulatedPoints = new ArrayList<>();

    private Button btnToggleCapture;
    private Button btnUpload;

    private ActivityResultLauncher<String> requestCameraPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.arcore);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);

        btnToggleCapture = findViewById(R.id.btnToggleCapture);
        btnUpload = findViewById(R.id.btnUpload);

        btnUpload.setEnabled(false);

        // request camera permission
        requestCameraPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                    if (!granted) {
                        // handle
                    }
                });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }

        btnToggleCapture.setOnClickListener(v -> {
            capturing = !capturing;
            if (capturing) {
                accumulatedPoints.clear();
                btnToggleCapture.setText("Stop Capture");
                btnUpload.setEnabled(false);
            } else {
                btnToggleCapture.setText("Start Capture");
                btnUpload.setEnabled(true);
                // When stopped, save to file
                File outFile = writePLYFile(accumulatedPoints);
                if (outFile != null) {
                    Log.d(TAG, "Saved PLY: " + outFile.getAbsolutePath());
                    // optionally show local preview
                    // Save reference for upload
                    lastSavedFile = outFile;
                }
            }
        });

        btnUpload.setOnClickListener(v -> {
            if (lastSavedFile != null) {
                uploadFileToFirebase(lastSavedFile);
            }
        });

        // Per-frame listener
        arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            if (!capturing) return;
            Frame frame = arFragment.getArSceneView().getArFrame();
            if (frame == null) return;

            // Get current camera pose to transform points into world
            Camera camera = frame.getCamera();
            Pose cameraPose = camera.getPose();

            try (PointCloud pointCloud = frame.acquirePointCloud()) {
                // pointCloud.getPoints() is a FloatBuffer with (x,y,z,confidence) repeated
                FloatBuffer pts = pointCloud.getPoints();
                pts.rewind();
                int numFloats = pts.limit(); // floats count
                // Each point is 4 floats: x,y,z,confidence
                for (int i = 0; i < numFloats; i += 4) {
                    float cx = pts.get(i);
                    float cy = pts.get(i + 1);
                    float cz = pts.get(i + 2);
                    // Convert from camera-local to world space:
                    float[] worldPoint = cameraPose.transformPoint(new float[]{cx, cy, cz});
                    accumulatedPoints.add(worldPoint);
                }
            } catch (Exception ex) {
                Log.w(TAG, "PointCloud acquisition error: " + ex.getMessage());
            }
        });

    }

    private File lastSavedFile = null;

    /** Write a simple ASCII PLY file from a list of float[3] points **/
    private File writePLYFile(ArrayList<float[]> points) {
        try {
            String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File dir = new File(getExternalFilesDir(null), "scans");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "scan_" + time + ".ply");

            FileOutputStream fos = new FileOutputStream(out);
            OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");

            // header (ASCII PLY)
            w.write("ply\n");
            w.write("format ascii 1.0\n");
            w.write("element vertex " + points.size() + "\n");
            w.write("property float x\n");
            w.write("property float y\n");
            w.write("property float z\n");
            w.write("end_header\n");

            // points
            for (float[] p : points) {
                w.write(p[0] + " " + p[1] + " " + p[2] + "\n");
            }

            w.flush();
            w.close();
            fos.close();
            return out;
        } catch (Exception e) {
            Log.e(TAG, "Failed to write PLY: " + e.getMessage(), e);
            return null;
        }
    }

    /** Upload file to Firebase Storage and record metadata in Firestore **/
    private void uploadFileToFirebase(File file) {
        try {
            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference storageRef = storage.getReference().child("models/" + file.getName());

            UploadTask uploadTask = storageRef.putFile(Uri.fromFile(file));
            uploadTask.addOnSuccessListener(taskSnapshot -> {
                storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String downloadUrl = uri.toString();
                    Log.d(TAG, "File uploaded, url: " + downloadUrl);

                    // Save metadata to Firestore
                    FirebaseFirestore db = FirebaseFirestore.getInstance();
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("name", file.getName());
                    doc.put("storagePath", "models/" + file.getName());
                    doc.put("downloadUrl", downloadUrl);
                    doc.put("createdAt", new Date());
                    db.collection("models").add(doc)
                            .addOnSuccessListener(documentReference -> {
                                Log.d(TAG, "Model metadata saved: " + documentReference.getId());
                            }).addOnFailureListener(e -> Log.e(TAG,"Firestore save failed", e));
                });
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Upload failed", e);
            });
        } catch (Exception e) {
            Log.e(TAG, "uploadFileToFirebase error: " + e.getMessage(), e);
        }
    }
}



//
//
//import android.net.Uri;
//import android.os.Bundle;
//import androidx.appcompat.app.AppCompatActivity;
//import com.google.ar.sceneform.ux.ArFragment;
//import com.google.ar.sceneform.rendering.ModelRenderable;
//import com.google.ar.sceneform.AnchorNode;
//import com.google.ar.core.Anchor;
//
//import static com.google.ar.sceneform.rendering.ModelRenderable.*;
//
//public class MainActivity extends AppCompatActivity {
//
//    private ArFragment arFragment;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.arcore);
//
//        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);
//
//        builder()
//                .setSource(this, Uri.parse("model.glb"))
//                .setIsFilamentGltf(true)
//                .build()
//                .thenAccept(renderable -> {
//                    arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
//                        if (arFragment.getArSceneView().getSession() == null) return;
//
//                        // Position fixe dans l’espace (1 mètre devant)
//                        Anchor anchor = arFragment.getArSceneView().getSession()
//                                .createAnchor(new com.google.ar.core.Pose(
//                                        new float[]{0, 0, -1},
//                                        new float[]{0, 0, 0, 1}));
//
//                        AnchorNode node = new AnchorNode(anchor);
//                        node.setRenderable(renderable);
//                        arFragment.getArSceneView().getScene().addChild(node);
//                    });
//                });
//    }
//}
//
//
