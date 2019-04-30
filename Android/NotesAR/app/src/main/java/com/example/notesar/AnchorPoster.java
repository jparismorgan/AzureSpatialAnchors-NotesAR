// Copyright (c) Paris Morgan. All rights reserved.
// Licensed under the MIT license.

package com.example.notesar;

import android.os.AsyncTask;
import android.util.Log;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

class AnchorPoster extends AsyncTask<String, Void, Void> {
    private String baseAddress;
    private MainActivity mainActivity;

    public AnchorPoster(String BaseAddress, MainActivity main) {
        baseAddress = BaseAddress;
        mainActivity = main;
    }

    /**
     * Saves an identifier to the sharing service.
     * @param anchorId The identifier to save.
     */
    public void PostAnchor(String anchorId, String groupingKey) {
        String ret = "";
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseAddress);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            DataOutputStream output = new DataOutputStream(connection.getOutputStream());
            output.writeBytes(anchorId + "|" + groupingKey);

            int responseCode = connection.getResponseCode();
            Log.i("NotesAR-SharingService", "[PostAnchor] Identifier " + anchorId + ". Returned with a status code of: " + responseCode);
        }
        catch(Exception e) {
            Log.e("NotesAR-SharingService", e.getMessage());
        }
        finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    protected Void doInBackground(String... input) {
        PostAnchor(input[0], input[1]);
        return null;
    }


    @Override
    protected void onPostExecute(Void v) {
        mainActivity.AnchorPosted();
        return;
    }
}
