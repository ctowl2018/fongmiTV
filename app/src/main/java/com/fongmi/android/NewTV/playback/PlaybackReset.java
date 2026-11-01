package com.fongmi.android.NewTV.playback;

import com.fongmi.android.NewTV.bean.Track;
import com.fongmi.android.NewTV.player.PlayerManager;

public final class PlaybackReset {

    public static void afterError(PlayerManager player) {
        afterError(player, null);
    }

    public static void afterError(PlayerManager player, Runnable beforeReset) {
        if (beforeReset != null) beforeReset.run();
        Track.delete(player.getKey());
        player.resetTrack();
        player.reset();
        player.stop();
    }
}
