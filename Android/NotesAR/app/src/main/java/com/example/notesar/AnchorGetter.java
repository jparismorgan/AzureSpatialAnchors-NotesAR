// Copyright (c) Paris Morgan. All rights reserved.
// Licensed under the MIT license.

package com.example.notesar;

import android.os.AsyncTask;
import android.util.Log;

import java.io.DataInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

class AnchorGetter extends AsyncTask<String, Void, String[]> {
    private String baseAddress;
    private MainActivity mainActivity;

    public AnchorGetter(String BaseAddress, MainActivity main) {
        baseAddress = BaseAddress;
        mainActivity = main;
    }

    /**
     * Gets all anchors stored by the sharing service.
     * @return An array of 10 anchors that the sharing service returned.
     */
    public String[] GetAnchors(String groupingKey) {
        String[] ret = {};
        HttpURLConnection connection = null;
        try {
            String anchorAddress = baseAddress+"/"+groupingKey;
            URL url = new URL(anchorAddress);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            Log.i("NotesAR-SharingService", "[GetAnchors] Returned with a status code of: " + responseCode);

            InputStream res = new DataInputStream(connection.getInputStream());

            String temp = "";
            int readValue = -1;
            do {
                readValue = res.read();
                if (readValue != -1) {
                    temp += (char)readValue;
                }
            } while(readValue != -1);

            connection.disconnect();

            // Parse the response into a list
            List<String> tempArray = Arrays.asList(temp.replaceAll("\"","").replaceAll("\\]", "").replaceAll("\\[", "").trim().split(","));

            // A watcher can only look for 10 identifiers at once, so we will only return 10.
            // Production code should either be more specific about which anchors it is looking for, or create watchers, one after another, that look for different anchors (you can generally give a watcher ~5 seconds to find its anchors).
            int end = Math.min(10, tempArray.size());
            ret = tempArray.subList(0, end).toArray(new String[0]);
        }
        catch(Exception e) {
            Log.e("NotesAR-SharingService", e.getMessage());
        }
        finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return ret;
    }

    @Override
    protected String[] doInBackground(String... input) {
        return GetAnchors(input[0]);
    }

    @Override
    protected void onPostExecute(String[] result) {
        mainActivity.AnchorIdsRetrievedFromSharingService(result);
    }
}
