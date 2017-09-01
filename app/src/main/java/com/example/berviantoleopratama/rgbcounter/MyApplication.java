package com.example.berviantoleopratama.rgbcounter;

import android.app.Application;

import com.facebook.drawee.backends.pipeline.Fresco;

/**
 * Created by BerviantoLeoPratama on 01/09/2017.
 */

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Fresco.initialize(this);
    }
}
