### Azure Spatial Anchors - NotesAR

Hello! This repo is to show off an Azure Spatial Anchors demo app. 

### Getting started

To start, you'll need to set up the sharing service. To do so, you should follow the steps to deploy the SharingServiceSample as outlined in [Tutorial: Share Azure Spatial Anchors across sessions and devices with an Azure Cosmos DB back end](https://docs.microsoft.com/en-us/azure/spatial-anchors/tutorials/tutorial-use-cosmos-db-to-store-anchors#create-a-database-account). This will walk you through deploying the web app.

After that, you'll need to set up the Android app. Get an Account ID and Account Key from Azure and paste them in to MainActivity.java. Do a Gradle sync, and you should be able to build and run! Try placing notes and recalling them.

### Notes

We use a SHARING_SERVICE_GROUPING_KEY to group anchors. You can only place 10 anchors per SHARING_SERVICE_GROUPING_KEY because a Watcher can only look for 10 anchors at a time. When you get to this limit, just change the key. To improve this demo app, we may add UI that lets you select the anchor grouping, or use GPS to narrow the search down. 
