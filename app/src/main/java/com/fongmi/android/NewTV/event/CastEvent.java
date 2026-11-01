package com.fongmi.android.NewTV.event;

import com.fongmi.android.NewTV.bean.Config;
import com.fongmi.android.NewTV.bean.Device;
import com.fongmi.android.NewTV.bean.History;

import org.greenrobot.eventbus.EventBus;

public record CastEvent(Config config, Device device, History history) {

    public static void post(Config config, Device device, History history) {
        EventBus.getDefault().post(new CastEvent(config, device, history));
    }
}
