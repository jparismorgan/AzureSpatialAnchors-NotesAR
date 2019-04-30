// Copyright (c) Paris Morgan. All rights reserved.
// Licensed under the MIT license.

package com.example.notesar;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchor;

/**
 * Helper class that we use to render a sphere.
 */
public class SphereVisual {
    private Renderable nodeRenderable = null;
    private Anchor localAnchor = null;
    private AnchorNode anchorNode;
    public CloudSpatialAnchor cloudAnchor = null;
    public String identifier = "";
    public String note = "";

    public SphereVisual() {
        anchorNode = new AnchorNode();
    }

    public Anchor getLocalAnchor() {
        return localAnchor;
    }

    public AnchorNode getAnchorNode() {
        return anchorNode;
    }

    public void setLocalAnchor(Anchor value) {
        localAnchor = value;
        anchorNode.setAnchor(value);
    }

    public void render(ArFragment arFragment) {
        anchorNode.setParent(arFragment.getArSceneView().getScene());
    }

    public void setColor(Material material) {
        if (nodeRenderable == null) {
            nodeRenderable =  ShapeFactory.makeSphere(0.05f, new Vector3(0.0f, 0.0f, 0.0f), material);
            anchorNode.setRenderable(nodeRenderable);
        } else {
            nodeRenderable.setMaterial(material);
        }
    }

    public void destroy()
    {
        anchorNode.setRenderable(null);
        anchorNode.setParent(null);
    }

}
