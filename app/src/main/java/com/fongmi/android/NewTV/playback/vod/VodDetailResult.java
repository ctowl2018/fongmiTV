package com.fongmi.android.NewTV.playback.vod;

import android.text.TextUtils;

import com.fongmi.android.NewTV.bean.Result;

public record VodDetailResult(String key, String id, Result result) {

    public boolean matches(String key, String id) {
        return TextUtils.equals(this.key, key) && TextUtils.equals(this.id, id);
    }
}
