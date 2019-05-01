//
//  SphereVisual.swift
//  NotesAR
//
//  Copyright © 2019 Paris Morgan. All rights reserved.
//  Licensed under the MIT license.
//

class SphereVisual {
    init() {
        node = nil
        cloudAnchor = nil
        localAnchor = nil
        note = ""
    }
    
    var node : SCNNode? = nil
    var cloudAnchor : ASACloudSpatialAnchor? = nil
    var localAnchor : ARAnchor? = nil
    var note: String
}
