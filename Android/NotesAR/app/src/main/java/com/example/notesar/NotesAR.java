// Copyright (c) Paris Morgan. All rights reserved.
// Licensed under the MIT license.

package com.example.notesar;

import android.app.Application;

import com.microsoft.CloudServices;

public class NotesAR extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CloudServices.initialize(this);
    }
}
