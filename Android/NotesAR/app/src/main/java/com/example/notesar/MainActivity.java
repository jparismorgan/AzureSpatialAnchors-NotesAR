// Copyright (c) Paris Morgan. All rights reserved.
// Licensed under the MIT license.

package com.example.notesar;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import com.microsoft.azure.spatialanchors.AnchorLocateCriteria;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchor;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchorSession;
import com.microsoft.azure.spatialanchors.SessionLogLevel;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // Set this string to the account ID provided for the Azure Spatial Service resource.
    private static final String SpatialAnchorsAccountId = "Set me";

    // Set this string to the account key provided for the Azure Spatial Service resource.
    private static final String SpatialAnchorsAccountKey = "Set me";

    // Set this string to the URL created when publishing your ASP.NET Core web app, with index.html replacec with /api/anchors. Format should be: https://<app_name>.azurewebsites.net/api/anchors
    private static final String SharingAnchorsServiceUrl = "Set me";

    // The key we use for a the properties dictionary in a CloudSpatialAnchor to set a note.
    private static final String CLOUDSPATIALANCHOR_PROPERTIES_NOTE_KEY = "note";

    // In the sharing service, we group anchor ids based on this key.
    // In a production app, you will want to give the user an option to select this.
    private static final String SHARING_SERVICE_GROUPING_KEY = "helloworld";

    private ArSceneView sceneView;
    private ArFragment arFragment;
    private CloudSpatialAnchorSession cloudSession;

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    // When recommendedSessionProgress > 1, we have enough information about the environment to save an anchor.
    private float recommendedSessionProgress = 0f;
    // Used as a lock to synchronize recommendedSessionProgress.
    private final Object syncSessionProgress = new Object();
    // The anchor currently being created. We used the SphereVisual class to hold information about the anchor
    private SphereVisual anchorBeingCreated = null;

    // True if we have tapped to place an anchor and are 1) currently waiting for user to type in their note
    //                                                or 2) scanning for upload (waiting for recommendedSessionProgress > 1)
    //                                                or 3) saving the anchor (waiting for the createAnchorAsync callback)
    private boolean currentlySavingAnchor = false;

    // Used as a lock to synchronize taps.
    private final Object syncTaps = new Object();

    private Material blue;
    private Material green;
    private Material white;
    private Material yellow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.BLUE)).thenAccept(material -> { blue = material; });
        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.GREEN)).thenAccept(material -> { green = material; });
        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.WHITE)).thenAccept(material -> { white = material; });
        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.YELLOW)).thenAccept(material -> { yellow = material; });

        this.arFragment = (ArFragment)getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        this.arFragment.getPlaneDiscoveryController().hide();
        this.arFragment.getPlaneDiscoveryController().setInstructionView(null);

        // Register handleTap() as a callback to be invoked when an ARCore Plane is tapped.
        this.arFragment.setOnTapArPlaneListener(this::handleTap);

        // Set up the ARCore session
        this.sceneView = arFragment.getArSceneView();
        setupSessionForSceneView(this, sceneView);

        Scene scene = sceneView.getScene();
        scene.addOnUpdateListener(frameTime -> {
            if (this.cloudSession != null) {
                // Pass frames to Spatial Anchors for processing.
                this.cloudSession.processFrame(this.sceneView.getArFrame());
            }
        });

        // Initialize the ASA session
        initializeSession();

        // Get the spatial anchor ids from the ASP.NET service
        AnchorGetter anchorExchanger = new AnchorGetter(SharingAnchorsServiceUrl, this);
        anchorExchanger.execute(SHARING_SERVICE_GROUPING_KEY);
    }

    /**
     * Initializes the Azure Spatial Anchors CloudSpatialAnchorSession.
     */
    private void initializeSession() {
        if (this.cloudSession != null){
            this.cloudSession.stop();
        }
        this.cloudSession = new CloudSpatialAnchorSession();
        this.cloudSession.getConfiguration().setAccountId(SpatialAnchorsAccountId);
        this.cloudSession.getConfiguration().setAccountKey(SpatialAnchorsAccountKey);
        this.cloudSession.setLogLevel(SessionLogLevel.Information);

        // Set the ARCore Session on the CloudSpatialAnchorSession.
        this.cloudSession.setSession(sceneView.getSession());

        // Callback that prints log messages.
        this.cloudSession.addOnLogDebugListener(args -> Log.d("ASAInfo", args.getMessage()));

        // Callback that prints error messages.
        this.cloudSession.addErrorListener(args -> Log.e("NotesAR-ASAError", String.format("%s: %s", args.getErrorCode().name(), args.getErrorMessage())));

        // Callback that gets called when the session has detected a change in the frames passed into it.
        // When recommendedSessionProgress is > 1, we can save a CloudSpatialAnchor.
        this.cloudSession.addSessionUpdatedListener(args -> {
            synchronized (this.syncSessionProgress) {
                this.recommendedSessionProgress = args.getStatus().getRecommendedForCreateProgress();
                Log.i("NotesAR-ASAInfo", String.format("[SessionUpdatedListener] Session progress: %f", this.recommendedSessionProgress));
            }
        });

        // Callback that is called when we locate an anchor. It will create a green sphere and a sticky note with the text a user previously inputted.
        this.cloudSession.addAnchorLocatedListener(args -> {
            String identifier = args.getIdentifier();
            switch (args.getStatus())
            {
                case Located:
                    runOnUiThread(()->{
                        SphereVisual visual = new SphereVisual();
                        visual.identifier = identifier;
                        // Get the Anchor from the CloudSpatialAnchor and set it on SphereVisual.
                        visual.setLocalAnchor(args.getAnchor().getLocalAnchor());
                        visual.cloudAnchor = args.getAnchor();

                        // Get the note, which we stored as a property on the CloudSpatialAnchor.
                        Map<String, String> properties = visual.cloudAnchor.getAppProperties();
                        if (properties.containsKey(CLOUDSPATIALANCHOR_PROPERTIES_NOTE_KEY)) {
                            visual.note = properties.get(CLOUDSPATIALANCHOR_PROPERTIES_NOTE_KEY);
                        }
                        Log.i("NotesAR-ASAInfo","[AnchorLocatedListener] Anchor located! Identifier: " + identifier + ". Note: " + visual.note);

                        visual.setColor(green);
                        visual.render(arFragment);

                        addStickyNote(visual);
                    });
                    break;
                case NotLocated:
                    break;
                case AlreadyTracked:
                    Log.i("NotesAR-ASAInfo","[AnchorLocatedListener] Anchor already tracked. Identifier" + identifier);
                    break;
                case NotLocatedAnchorDoesNotExist:
                    Log.i("NotesAR-ASAInfo","[AnchorLocatedListener] Anchor does not exist. Identifier: " + identifier);
                    break;
            }
        });

        // Start the CloudSpatialAnchor session.
        this.cloudSession.start();
    }

    /**
     * A helper method to set up the ARCore Session.
     * @param context
     * @param sceneView
     */
    private void setupSessionForSceneView(Context context, ArSceneView sceneView) {
        try {
            Session session = new Session(context);
            Config config = new Config(session);
            config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
            session.configure(config);
            sceneView.setupSession(session);
        }
        catch (UnavailableException e) {
            Log.e("NotesAR-ASAError", e.toString());
        }
    }

    /**
     * Called by AnchorGetter when the sharing service returns a list of CloudSpatialAnchor identifiers.
     * Uses those identifiers to create a Watcher that will locate those CloudSpatialAnchors.
     * @param identifiers The CloudSpatialAnchor identifiers to look for.
     */
    public void AnchorIdsRetrievedFromSharingService(String[] identifiers) {
        // Get anchor identifier from sharing service
        if (identifiers.length > 0) {
            Log.i("NotesAR-ASAInfo", "[LocateAnchors] Creating a watcher to look for anchors: " + Arrays.toString(identifiers));
            AnchorLocateCriteria criteria = new AnchorLocateCriteria();
            criteria.setIdentifiers(identifiers);
            this.cloudSession.createWatcher(criteria);
        }
        else {
            Log.i("NotesAR-ASAInfo", "[LocateAnchors] Didn't retrieve any anchors from the service, so not creating a Watcher.");
        }
    }

    /**
     * Called by AnchorPoster when we successfully saved a CloudSpatialAnchor identifier to the sharing service.
     */
    public void AnchorPosted() {
        Log.i("NotesAR-SharingService", "[AnchorPosted] Anchor posted to service");
    }

    /**
     * Called whenever an ARCore Plane is tapped.
     * @param hitResult
     * @param plane
     * @param motionEvent
     */
    private void handleTap(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
        synchronized (this.syncTaps) {
            if (this.currentlySavingAnchor) {
                return;
            }

            this.currentlySavingAnchor = true;
        }
        SphereVisual visual = new SphereVisual();

        // Create the ARCore Anchor.
        Anchor localAnchor = hitResult.createAnchor();
        visual.setLocalAnchor(localAnchor);

        // Create the Azure Spatial Anchors CloudSpatialAnchor.
        CloudSpatialAnchor cloudAnchor = new CloudSpatialAnchor();

        // Set the Anchor as the localAnchor of the CloudSpatialAnchor.
        cloudAnchor.setLocalAnchor(localAnchor);
        visual.cloudAnchor = cloudAnchor;

        // Render the white sphere.
        visual.setColor(white);
        visual.render(arFragment);

        // Keep track of the anchor we are creating.
        this.anchorBeingCreated = visual;

        // Pop up a text box for the user to input a note.
        showInputDialog();
    }

    /**
     * Shows a pop up text box to enter information for the sticky note.
     * When the user enters input text, calls createAndUploadAnchor().
     */
    private void showInputDialog() {
        // get prompts.xml view
        LayoutInflater li = LayoutInflater.from(MainActivity.this);
        View promptsView = li.inflate(R.layout.prompts, null);

        final EditText userInput = (EditText) promptsView.findViewById(R.id.edit_text);

        final AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                .setView(promptsView)
                .setTitle(null)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                Button positiveButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                positiveButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String note = userInput.getText().toString();
                        if (note == null || note.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Please enter a note!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        MainActivity.this.anchorBeingCreated.note = note;

                        Map<String, String> properties = MainActivity.this.anchorBeingCreated.cloudAnchor.getAppProperties();
                        properties.put(CLOUDSPATIALANCHOR_PROPERTIES_NOTE_KEY, note);

                        uploadAnchorToAzureSpatialAnchorsAndSharingService();

                        dialog.dismiss();
                    }
                });

                Button negativeButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEGATIVE);
                negativeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        synchronized (MainActivity.this.syncTaps) {
                            anchorBeingCreated.destroy();
                            anchorBeingCreated = null;
                            MainActivity.this.currentlySavingAnchor = false;
                            dialog.cancel();
                        }
                    }
                });
            }
        });
        dialog.getWindow().setDimAmount(0.0f);
        dialog.show();
    }

    /**
     * Uploads the anchorBeingCreated to Azure Spatial Anchors, and then to the sharing service.
     */
    private void uploadAnchorToAzureSpatialAnchorsAndSharingService() {
        // Add sticky note UI element to the anchor
        addStickyNote(anchorBeingCreated);

        // Save the CloudSpatialAnchor to Azure Spatial Anchors
        uploadCloudAnchorAsync(anchorBeingCreated.cloudAnchor)
                .thenAccept(identifier -> {
                    // We get this callback when the anchor has saved to Azure Spatial Anchors
                    this.anchorBeingCreated.identifier = identifier;

                    // Log identifier
                    Log.i("NotesAR-ASAInfo", String.format("[createAndUploadAnchor] Cloud Anchor created. Id: %s", identifier));

                    // Update sphere color to blue
                    runOnUiThread(() -> {
                        this.anchorBeingCreated.setColor(blue);
                        synchronized (this.syncTaps) {
                            this.currentlySavingAnchor = false;
                        }
                        // We don't keep a reference to the object.
                        // You may want to so you can modify it.
                        this.anchorBeingCreated = null;
                    });

                    // Save anchor id to service
                    Log.d("NotesAR-ASAInfo", "[createAndUploadAnchor] Will save anchor to the service. Id: " + identifier);
                    AnchorPoster poster = new AnchorPoster(SharingAnchorsServiceUrl, this);
                    poster.execute(identifier, SHARING_SERVICE_GROUPING_KEY);
                });
    }

    /**
     * Helper method to save an anchor to Azure Spatial Anchors.
     * Asynchronously waits until enough frames are collected from your device.
     * When that happens, switch the color of the sphere to yellow and starts uploading the CloudSpatialAnchor.
     * @param anchor The CloudSpatialAnchor to save.
     * @return The CloudSpatialAnchor identifier (it is assigned by the service once an anchor is saved)
     */
    private CompletableFuture<String> uploadCloudAnchorAsync(CloudSpatialAnchor anchor) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Wait until recommendedSessionProgress gets to 1
                float currentSessionProgress;
                do {
                    synchronized (this.syncSessionProgress) {
                        currentSessionProgress = this.recommendedSessionProgress;
                    }
                    if (currentSessionProgress < 1.0) {
                        Thread.sleep(500);
                    }
                }
                while (currentSessionProgress < 1.0);

                // Set the sphere to yellow to indicate saving has started
                runOnUiThread(() -> {
                    this.anchorBeingCreated.setColor(yellow);
                });

                // Save the anchor to the cloud
                this.cloudSession.createAnchorAsync(anchor).get();
            } catch (InterruptedException | ExecutionException e) {
                Log.e("NotesAR-ASAError", "[uploadCloudAnchorAsync] " + e.toString());
                throw new RuntimeException(e);
            }
        }, executor).thenApply(ignore -> anchor.getIdentifier()); // Return the cloud spatial anchor identifier
    }

    /**
     * Adds a sticky note UI element.
     * @param anchor Places the note on the AnchorNode. The note comes from the note property on the anchor.
     */
    private void addStickyNote(SphereVisual anchor) {
        if (anchor.getAnchorNode() == null) {
            Log.e("NotesAR-UI", "[addStickyNote] Attempting to creating a sticky note with a null anchorNode");
            assert(anchor.getAnchorNode() != null);
            return;
        }

        runOnUiThread(() -> {
            ViewRenderable.builder().setView(this, R.layout.sticky_note).build()
                .thenAccept(viewRenderable -> {
                    Node noteText = new Node();
                    noteText.setParent(arFragment.getArSceneView().getScene());
                    noteText.setParent(anchor.getAnchorNode());
                    noteText.setRenderable(viewRenderable);
                    TextView tv = ((ViewRenderable) noteText.getRenderable()).getView().findViewById(R.id.postItNoteTextView);
                    tv.setText(anchor.note);
                    noteText.setLocalPosition(new Vector3(0.0f, 0.20f, 0f));
                });
        });
    }
}
