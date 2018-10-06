package com.doggonics.thingytempmotion;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class EnviroService extends Service {
    public EnviroService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
