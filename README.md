### Azure Spatial Anchors - NotesAR

Hello! This repo is to show off an Azure Spatial Anchors demo app. 

### Getting started

#### Sharing Service
To start, you'll need to set up the sharing service. To do so, you should follow the steps to deploy the SharingServiceSample as outlined in [Tutorial: Share Azure Spatial Anchors across sessions and devices with an Azure Cosmos DB back end](https://docs.microsoft.com/en-us/azure/spatial-anchors/tutorials/tutorial-use-cosmos-db-to-store-anchors#create-a-database-account). This will walk you through deploying the web app.

The result of this is you'll have a Sharing Service URL, which should look like "https://<app_name>.azurewebsites.net/api/anchors".

#### Android
Get an Account ID and Account Key from Azure and paste them in to MainActivity.java. Then paste the Sharing Service URL from the last step and paste it into the SharingAnchorsServiceUrl. Do a Gradle sync, and you should be able to build and run! Try placing notes and recalling them.

#### iOS

At the root of the repo, run:

`pod install --repo-update`

Then open open the XCode Workspace:

`open ./NotesAR.xcworkspace`

To check that you have the ASA SDK correctly installed, you should be able to open the AzureSpatialAnchorsLibrary.h header file. You can look at it with:

`less Pods/AzureSpatialAnchors/bin/frameworks/AzureSpatialAnchors.framework/Headers/AzureSpatialAnchorsLibrary.h`

Next, Get an Account ID and Account Key from Azure and paste them in to MainActivity.java. Then paste the Sharing Service URL from the Sharing Service step and paste it into the SharingAnchorsServiceUrl. 

### Notes

We use a SHARING_SERVICE_GROUPING_KEY to group anchors. You can only place 10 anchors per SHARING_SERVICE_GROUPING_KEY because a Watcher can only look for 10 anchors at a time. When you get to this limit, just change the key. To improve this demo app, we may add UI that lets you select the anchor grouping, or perhaps use GPS to narrow the search down. 

### Improvements

- [] Don't use thread.sleep() in the Android sample. Use a callback or check for sessionProgress in the rendering loop.
- [] Add UI to choose grouping key OR use GPS to narrow down.
- [] Refactor iOS to have a separate class extension per protocol. Code is easier to read like that.

### Contact

Feel free to contact Paris Morgan here, on Twitter (@jparismorgan), or in the HoloDevelopers slack channel with any questions or suggestions.
