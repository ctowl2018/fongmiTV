package com.fongmi.android.NewTV.playback;

import com.fongmi.android.NewTV.bean.Result;

public record PlaybackResult<T>(T request, Result result) {
}
