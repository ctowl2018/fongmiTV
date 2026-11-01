package com.fongmi.android.NewTV.player.engine;

import static com.fongmi.android.NewTV.player.engine.PlayerEngine.Type.EXO;
import static com.fongmi.android.NewTV.player.engine.PlayerEngine.Type.MPV;

import androidx.media3.common.Player;

import com.fongmi.android.NewTV.player.exo.ExoPlayerEngine;
import com.fongmi.android.NewTV.player.media.PlaySpec;
import com.fongmi.android.NewTV.player.mpv.MpvPlayerEngine;
import com.fongmi.android.NewTV.setting.PlayerSetting;
import com.github.catvod.utils.UriUtil;

public final class PlayerEngineFactory {

    public static PlayerEngine create(Player.Listener listener) {
        return create(resolve(), listener);
    }

    public static PlayerEngine create(PlaySpec spec, Player.Listener listener) {
        return create(resolve(spec), listener);
    }

    public static PlayerEngine create(PlayerEngine.Type type, Player.Listener listener) {
        return switch (type) {
            case EXO -> new ExoPlayerEngine(listener);
            case MPV -> new MpvPlayerEngine(listener);
        };
    }

    public static boolean matches(PlayerEngine engine, PlaySpec spec) {
        return engine != null && engine.getType() == resolve(spec) && !engine.needsRebuild();
    }

    public static boolean canPreload(PlaySpec spec) {
        return true;
    }

    private static PlayerEngine.Type resolve(PlaySpec spec) {
        return requiresExo(spec) ? EXO : resolve();
    }

    private static PlayerEngine.Type resolve() {
        return isMpvReady() ? MPV : EXO;
    }

    static boolean requiresExo(PlaySpec spec) {
        return "smb".equals(UriUtil.scheme(spec.getUrl())) || spec.getDrm() != null;
    }

    private static boolean isMpvReady() {
        return false;
    }
}
