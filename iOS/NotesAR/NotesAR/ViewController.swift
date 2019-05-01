//
//  ViewController.swift
//  NotesAR
//
//  Copyright © 2019 Paris Morgan. All rights reserved.
//  Licensed under the MIT license.
//

import UIKit
import SceneKit
import ARKit

let localColor = UIColor.white.withAlphaComponent(0.9)     // white for a local anchor
let readyColor = UIColor.yellow.withAlphaComponent(0.9)    // yellow for when the local anchor is assigned to a cloud anchor and is ready to save
let savedColor = UIColor.blue.withAlphaComponent(0.9)      // blue when the cloud anchor was saved successfully
let foundColor = UIColor.green.withAlphaComponent(0.9)     // green for a located cloud anchor

class ViewController: UIViewController, UITextFieldDelegate, ARSCNViewDelegate, ASACloudSpatialAnchorSessionDelegate {
    
    // Set this to the account ID provided for the Azure Spatial Service resource.
    let SpatialAnchorsAccountId = "Set me"
    
    // Set this to the account key provided for the Azure Spatial Service resource.
    let SpatialAnchorsAccountKey = "Set me"
    
    // Set this string to the URL created when publishing your ASP.NET Core web app, with index.html replacec with /api/anchors. Format should be: https://<app_name>.azurewebsites.net/api/anchors
    let SharingAnchorsServiceUrl = "Set me";
    
    // The key we use for a the properties dictionary in a CloudSpatialAnchor to set a note. Used in the Android app as well - do not change!
    let CLOUDSPATIALANCHOR_PROPERTIES_NOTE_KEY = "note"
    
    // In the sharing service, we group anchor ids based on this key.
    // In a production app, you will want to give the user an option to select this.
    let SHARING_SERVICE_GROUPING_KEY = "helloworld";
    
    @IBOutlet var sceneView: ARSCNView!
    
    // The CloudSpatialAnchorSession
    var cloudSession : ASACloudSpatialAnchorSession? = nil
    
    // The CloudSpatialAnchor we will save.
    var anchorToSave : SphereVisual? = nil
    
    // Whether we are ready to save the CloudSpatialAnchor (i.e. we have a location and a note for it)
    var anchorReadyToSave : Bool = false
    
    // When recommendedSessionProgress > 1, we have enough information about the environment to save a CloudSpatialAnhchor.
    var recommendedSessionProgress : Float = 0.0
    
    // A list of anchors we have located. Used to render the spheres.
    var locatedAnchors = [SphereVisual]()
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        // Set the view's delegate.
        sceneView.delegate = self
        
        // Show statistics such as fps and timing information.
        sceneView.showsStatistics = true
        
        // Create a new scene and set it on the view.
        sceneView.scene = SCNScene()
        
        // The ASA session is initialized once ARKit tracking has stabilized. See session(_ session: ARSession, cameraDidChangeTrackingState camera: ARCamera) { }.
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        
        // Create a session configuration.
        let configuration = ARWorldTrackingConfiguration()
        sceneView.debugOptions = .showFeaturePoints
        
        // Run the view's session.
        sceneView.session.run(configuration)
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        
        // Pause the view's session.
        sceneView.session.pause()
    }
    
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else {
            return
        }
        
        // Do a hit test to find where in the physical world we tapped.
        let location = touch.location(in: sceneView)
        let hitList = sceneView.hitTest(location, types: [ARHitTestResult.ResultType.featurePoint])
        
        guard let result = hitList.first else {
            return
        }
        
        // Disable taps - we will re-enable once we have finished saving this anchor.
        self.view.isUserInteractionEnabled = false
        
        // Rotate the point to be facing the camera.
        let rotate = simd_float4x4(SCNMatrix4MakeRotation(sceneView.session.currentFrame!.camera.eulerAngles.y, 0, 1, 0))
        let rotateTransform = simd_mul(result.worldTransform, rotate)
        
        // Create the ARKit ARAnchor.
        let localAnchor = ARAnchor(transform: rotateTransform)
        
        // Create the Azure Spatial Anchors CloudSpatialAnchor.
        let cloudAnchor = ASACloudSpatialAnchor()
        
        // Set the ARKit ARAnchor as the localAnchor of the CloudSpatialAnchor.
        cloudAnchor!.localAnchor = localAnchor
        
        // Create the sphereVisual class, which we use to render the sphere.
        let visual = SphereVisual()
        visual.localAnchor = localAnchor
        visual.cloudAnchor = cloudAnchor
        anchorToSave = visual
        
        // Pop up a text box for the user to input a note.
        let alertController = UIAlertController(title: "Please enter a note!", message: "", preferredStyle: .alert)
        alertController.addAction(UIAlertAction(title: "Create anchor", style: .default, handler: { alert -> Void in
            let textField = alertController.textFields![0] as UITextField
            let note = textField.text ?? ""
            
            // Add the note as a property on the CloudSpatialAnchor
            let anchorProperties: [AnyHashable: Any] = [AnyHashable(self.CLOUDSPATIALANCHOR_PROPERTIES_NOTE_KEY): note]
            self.anchorToSave?.cloudAnchor?.appProperties = anchorProperties
            
            // Render the sphere and note
            self.anchorToSave?.note = note
            self.sceneView.session.add(anchor: localAnchor)
            
            // We are now ready to save the anchor
            self.anchorReadyToSave = true 
        }))
        alertController.addAction(UIAlertAction(title: "Cancel", style: .cancel, handler: { alert -> Void in
            DispatchQueue.main.async {
            self.view.isUserInteractionEnabled = true
            }
        }))
        
        alertController.addTextField(configurationHandler: {(textField : UITextField!) -> Void in
            textField.placeholder = "Enter note here..."
        })
        
        self.present(alertController, animated: true, completion: nil)
    }
    
    // MARK: - ARSCNViewDelegate
    
    func createSphereAndNote(_ note: String, _ sphereColor: UIColor) -> SCNNode {
        // Create the sphere.
        let sphere = SCNSphere(radius: 0.06)
        sphere.firstMaterial?.diffuse.contents = sphereColor
        let sphereNode = SCNNode(geometry: sphere)
        
        // Add the text.
        let text = SCNText(string: note, extrusionDepth: 0)
        let font = UIFont(name: "ChalkboardSE-Light", size: 0.5)
        text.font = font
        text.alignmentMode = "kCAAlignmentCenter"
        text.firstMaterial?.diffuse.contents = UIColor.black
        text.firstMaterial?.isDoubleSided = true
        
        let (minBound, maxBound) = text.boundingBox
        let textNode = SCNNode(geometry: text)
        textNode.pivot = SCNMatrix4MakeTranslation((maxBound.x - minBound.x) / 2, 
                                                   minBound.y, 
                                                   0)
        textNode.scale = SCNVector3Make(0.1, 0.1, 0.1)
        textNode.position = SCNVector3Make(0, 0.2, 0)
        sphereNode.addChildNode(textNode)
        
        // Add the grey rectangle around the text.
        let bound = SCNVector3Make(maxBound.x - minBound.x,
                                   maxBound.y - minBound.y,
                                   maxBound.z - minBound.z);
        
        let plane = SCNPlane(width: CGFloat(bound.x + 1),
                             height: CGFloat(bound.y + 1))
        plane.firstMaterial?.diffuse.contents = UIColor.gray.withAlphaComponent(0.9)
        plane.firstMaterial?.isDoubleSided = true
        
        let planeNode = SCNNode(geometry: plane)
        planeNode.position = SCNVector3(CGFloat(minBound.x) + CGFloat(bound.x) / 2 , 
                                        CGFloat(minBound.y) + CGFloat(bound.y) / 2,
                                        CGFloat(minBound.z - 0.01))
        textNode.addChildNode(planeNode)
        
        return sphereNode
    }
    
     // Override to create and configure nodes for anchors added to the view's session.
    func renderer(_ renderer: SCNSceneRenderer, nodeFor anchor: ARAnchor) -> SCNNode? {
        if (anchorToSave?.localAnchor == anchor) {
            logInfo("[renderer nodeFor] Rendering sphere for anchor we just created. ARAnchor: \(anchor)")
            anchorToSave?.node = createSphereAndNote((anchorToSave?.note)!, localColor) 
            return anchorToSave?.node
        } 
        else if let anchorIndex = locatedAnchors.index(where: { $0.localAnchor == anchor }) {
            logInfo("[renderer nodeFor] Rendering sphere for anchor we just located. Index: \(anchorIndex). Note: \(locatedAnchors[anchorIndex].note) ARAnchor: \(anchor)")
            locatedAnchors[anchorIndex].node = createSphereAndNote(locatedAnchors[anchorIndex].note, foundColor) 
            return locatedAnchors[anchorIndex].node
        } 
        return nil
    }
    
    func session(_ session: ARSession, cameraDidChangeTrackingState camera: ARCamera) {
        switch camera.trackingState {
        case .normal:
            if (cloudSession == nil) {
                // Initialize the ASA session.
                initializeSession()
                
                // Get the spatial anchor ids from the ASP.NET service.
                // After retrieving them, we will also begin locating them.
                getAnchorIds(SHARING_SERVICE_GROUPING_KEY)
            }
            break
        default:
            break
        }
    }
    
    func session(_ session: ARSession, didFailWithError error: Error) {
        // Present an error message to the user.
        
    }
    
    func sessionWasInterrupted(_ session: ARSession) {
        // Inform the user that the session has been interrupted, for example, by presenting an overlay.
        
    }
    
    func sessionInterruptionEnded(_ session: ARSession) {
        // Reset tracking and/or remove existing anchors if consistent tracking is required.
        
    }
    
    // MARK: - SceneKit Delegates
    
    func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
        // per-frame scenekit logic.
        // modifications don't go through transaction model.
        if let cloudSession = cloudSession {
            cloudSession.processFrame(sceneView.session.currentFrame)
        }
        
        if (anchorReadyToSave && recommendedSessionProgress > 1) {
            saveCloudAnchor()
        }
    }
    
    // MARK: - Azure Spatial Anchors Helper Functions
    
    func initializeSession() {
        if (SpatialAnchorsAccountId == "" || SpatialAnchorsAccountKey == "") {
            logError("[initializeSession] No SpatialAnchorsAccountId or SpatialAnchorsAccountKey!")
        }
        
        cloudSession = ASACloudSpatialAnchorSession()
        cloudSession!.configuration.accountId = SpatialAnchorsAccountId
        cloudSession!.configuration.accountKey = SpatialAnchorsAccountKey
        cloudSession!.logLevel = .information
        
        // Set the ARKit Session on the CloudSpatialAnchorSession.
        cloudSession!.session = sceneView.session
        
        // Set the delegate.
        cloudSession!.delegate = self
        
        // Start the CloudSpatialAnchorSession.
        cloudSession!.start()
    }
    
    func locateAnchors(_ identifiers: [String]) {
        // Locate anchors
        DispatchQueue.main.async {
            self.logInfo("[locateAnchors] Creating a watcher to look for anchors: \(identifiers)")
            let criteria = ASAAnchorLocateCriteria()!
            criteria.identifiers = identifiers
            self.cloudSession!.createWatcher(criteria)
        }
    }
    
    func saveCloudAnchor() {
        anchorReadyToSave = false
        
        // Set the sphere color to yellow.
        self.anchorToSave?.node?.geometry?.firstMaterial?.diffuse.contents = readyColor
        
        // Save CloudSpatialAnchor to the cloud.
        DispatchQueue.main.async {
            self.cloudSession?.createAnchor(self.anchorToSave?.cloudAnchor!, withCompletionHandler: { (error: Error?) in
                if (error != nil) {
                    self.logError("[createAnchor] Creation failed: \(error!.localizedDescription)")
                }
                else {
                    self.logInfo("[createAnchor] Cloud Anchor created! Identifier: \(self.anchorToSave?.cloudAnchor!.identifier ?? "No identifier")")
                    
                    // Allow user interaction.
                    self.view.isUserInteractionEnabled = true
                    
                    // Set the sphere color to blue.
                    self.anchorToSave?.node?.geometry?.firstMaterial?.diffuse.contents = savedColor
                    
                    // Save CloudSpatialAnchor ID to the Sharing Service.
                    self.postAnchor((self.anchorToSave?.cloudAnchor!.identifier)!, self.SHARING_SERVICE_GROUPING_KEY)
                }
            })
        }
    }
    
    // MARK: - ASACloudSpatialAnchorSession Delegates
    
    // Callback that prints log messages.
    internal func onLogDebug(_ cloudSpatialAnchorSession: ASACloudSpatialAnchorSession!, _ args: ASAOnLogDebugEventArgs!) {
        if let message = args.message {
            logInfo("[onLogDebug] \(message)")
        }
    }
    
    // Callback that prints error messages.
    internal func error (_ cloudSpatialAnchorSession: ASACloudSpatialAnchorSession!, _ args: ASASessionErrorEventArgs!) {
        if let errorMessage = args.errorMessage {
            logError("[error] \(errorMessage)")
        }
    }
    
    // Callback that gets called when the session has detected a change in the frames passed into it.
    // When recommendedSessionProgress is > 1, we can save a CloudSpatialAnchor.
    internal func sessionUpdated(_ cloudSpatialAnchorSession: ASACloudSpatialAnchorSession!, _ args: ASASessionUpdatedEventArgs!) {
        let status = args.status!.recommendedForCreateProgress
        recommendedSessionProgress = status
        logInfo("[sessionUpdated] sessionStatus: \(status)")
    }
    
    // Callback that is called when we locate an anchor.
    internal func anchorLocated(_ cloudSpatialAnchorSession: ASACloudSpatialAnchorSession!, _ args: ASAAnchorLocatedEventArgs!) {
        let status = args.status
        let anchor = args.anchor
        switch (status) {
        case .located:
            logInfo("[anchorLocated] Cloud Anchor found! Identifier: \(anchor!.identifier ?? "nil"). ARAnchor: \(anchor!.localAnchor!)")
            
            // Create the SphereVisual.
            let visual = SphereVisual()
            visual.cloudAnchor = anchor
            
            // Retrieve the local anchor (i.e. the ARKit ARAnchor).
            visual.localAnchor = anchor!.localAnchor
            
            // Retrieve the note.
            visual.note = anchor?.appProperties[AnyHashable(CLOUDSPATIALANCHOR_PROPERTIES_NOTE_KEY)] as? String ?? ""
            
            // Add it to the list of located anchors.
            locatedAnchors.append(visual)
            
            // Add the ARAnchor to the scene. The renderer will render the sphere based on information in the SphereVisual we put in locatedAnchors.
            sceneView.session.add(anchor: anchor!.localAnchor)
        case .alreadyTracked:
            logInfo("[anchorLocated] Cloud Anchor already tracked. Identifier: \(anchor!.identifier ?? "nil"). ARAnchor: \(anchor!.localAnchor!)")
            break
        case .notLocatedAnchorDoesNotExist:
            logInfo("[anchorLocated] Cloud Anchor does not exist. Identifier: \(anchor!.identifier ?? "nil").")
            break
        case .notLocated:
            break
        }
    }
    
    internal func locateAnchorsCompleted(_ cloudSpatialAnchorSession: ASACloudSpatialAnchorSession!, _ args: ASALocateAnchorsCompletedEventArgs!) {
        logInfo("[locateAnchorsCompleted] All anchors located for watcher with ID: \(args.watcher.identifier)")
    }
    
    // MARK: - Helpers
    
    func logInfo(_ message: String!) {
        DispatchQueue.main.async {
            print("ASA Info: " + message)
        }
    }
    
    func logError(_ message: String!) {
        DispatchQueue.main.async {
            print("ASA Error: " + message)
        }
    }
    
    // MARK: - Sharing Service
    
    func getAnchorIds(_ groupingKey: String) {
        let session = URLSession(configuration: .ephemeral)
        let url = URL(string: "\(SharingAnchorsServiceUrl)/\(groupingKey)")!
        let task = session.dataTask(with: url) { data, response, error in
            // Check for error.
            guard error == nil else {
                self.logError("[getAnchorIds] \(error!)")
                return
            }
            
            // Check that we got data back.
            guard let content = data else {
                self.logError("[getAnchorIds] No data.")
                return
            }
            
            // Check response.
            if let httpResponse = response as? HTTPURLResponse{
                let statusCode = httpResponse.statusCode 
                if (statusCode < 200 || statusCode >= 300) {
                    self.logError("[getAnchorIds] Error. Status code: \(statusCode)")
                    return
                }
                self.logInfo("[getAnchorIds] Successful reequest. Status code: \(statusCode)")
            }
            
            let contentString = String(decoding: content, as: UTF8.self)
            let ids = contentString.replacingOccurrences(of: "\\", with: "").replacingOccurrences(of: "\"", with: "").replacingOccurrences(of: "[", with: "").replacingOccurrences(of: "]", with: "").components(separatedBy: ",")
            self.logInfo("[getAnchorIds] Ids: \(ids)")
            
            // A watcher can only look for 10 identifiers at once, so we will only return 10.
            // Production code should either be more specific about which anchors it is looking for, 
            // or create watchers, one after another, that look for different anchors (you can generally give a watcher ~5 seconds to find its anchors).
            self.locateAnchors(Array(ids.prefix(10)))
        }
        
        // Execute the HTTP request.
        task.resume()
    }
    
    func postAnchor(_ anchorId: String, _ groupingKey: String){
        let config = URLSessionConfiguration.default
        let session = URLSession(configuration: config)
        let url = URL(string: "\(SharingAnchorsServiceUrl)")!
        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = "POST"
        urlRequest.httpBody = "\(anchorId)|\(groupingKey)".data(using: .utf8)
        
        self.logInfo("[postAnchor] Creating POST request. AnchorId: \(anchorId). groupingKey: \(groupingKey). URL: \(url.absoluteString)")
        
        let task = session.dataTask(with: urlRequest) { data, response, error in
            // Check for error.
            guard error == nil else {
                self.logError("[postAnchor] Error: \(error!)")
                return
            }
            
            // Check response.
            if let httpResponse = response as? HTTPURLResponse{
                let statusCode = httpResponse.statusCode 
                if (statusCode < 200 || statusCode >= 300) {
                    self.logError("[postAnchor] Error. Status code: \(statusCode)")
                    return
                }
                self.logInfo("[postAnchor] Identifier \(anchorId). Returned with a status code: \(statusCode)")
            }
        }
        
        // Execute the HTTP request.
        task.resume()
    }
    
}
