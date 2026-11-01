package com.fongmi.android.tv.player.exo;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;

import java.io.File;
import java.util.List;

/**
 * 带外挂 ASS 的 MediaSource 链路自检（仅 debug 源集）。
 *
 * <p>放在 {@code player.exo} 包下，才能访问包内可见的 {@link ExoPlayerSession} 与
 * {@link ExoMediaSourceFactory}，从而复现"加载字幕后视频加载不出来"的真实路径：
 * 走 {@code ExoPlayerSession.createMediaSource} → {@code ExoMediaSourceFactory}，
 * 而不是探针自建的裸 ExoPlayer。
 */
public class SessionProbeActivity extends Activity {

    private static final String TAG = "SessionProbe";
    private static final String VIDEO = "/sdcard/Movies/test.mp4";
    private static final String SUB = "/sdcard/Movies/test.ass";

    private final StringBuilder report = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView output;
    private ExoPlayerSession session;
    private int state = Integer.MIN_VALUE;
    private String error = "none";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        output = new TextView(this);
        output.setTextColor(Color.WHITE);
        output.setTextSize(10);
        output.setBackgroundColor(Color.BLACK);
        output.setGravity(Gravity.TOP | Gravity.START);
        output.setPadding(12, 12, 12, 12);
        setContentView(output);

        line("=== 带 ASS 字幕的 MediaSource 链路 ===");
        File video = new File(VIDEO);
        File sub = new File(SUB);
        line("video=" + video.exists() + "  sub=" + sub.exists() + "(" + sub.length() + "B)");
        if (!video.exists() || !sub.exists()) {
            line("素材缺失，先 push WAV + ASS");
            return;
        }

        try {
            session = new ExoPlayerSession(null);
            line("session 创建成功, ass=" + (session.ass() != null));

            MediaItem.SubtitleConfiguration subConfig = new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(sub))
                    .setMimeType(MimeTypes.TEXT_SSA)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build();
            MediaItem item = new MediaItem.Builder()
                    .setUri(Uri.fromFile(video))
                    .setSubtitleConfigurations(List.of(subConfig))
                    .build();

            MediaSource source = session.createMediaSource(item);
            line("createMediaSource 成功: " + (source != null));

            ExoPlayer player = session.player();
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int newState) {
                    state = newState;
                    line("state -> " + name(newState));
                }

                @Override
                public void onPlayerError(PlaybackException e) {
                    error = e.getErrorCodeName() + " / " + e.getMessage();
                    line("ERROR -> " + error);
                    Log.e(TAG, "player error", e);
                }

                @Override
                public void onTracksChanged(Tracks tracks) {
                    line("tracks groups=" + tracks.getGroups().size());
                }
            });

            player.setMediaSource(source);
            player.prepare();
            player.play();
            line("prepare+play 已调用");
        } catch (Throwable e) {
            line("异常: " + e);
            Log.e(TAG, "session probe failed", e);
            for (StackTraceElement el : e.getStackTrace()) {
                if (el.getClassName().startsWith("com.fongmi") || el.getClassName().startsWith("androidx.media3")) {
                    line("  at " + el);
                    break;
                }
            }
        }

        handler.postDelayed(() -> {
            line("");
            line("结果: state=" + name(state) + "  error=" + error);
            boolean ok = "none".equals(error) && (state == Player.STATE_READY || state == Player.STATE_ENDED);
            line("判定: " + (ok ? "成功（字幕源未拖垮 MediaSource）" : "失败"));
            Log.i(TAG, report.toString());
        }, 6000);
    }

    private static String name(int s) {
        return switch (s) {
            case Player.STATE_IDLE -> "IDLE";
            case Player.STATE_BUFFERING -> "BUFFERING";
            case Player.STATE_READY -> "READY";
            case Player.STATE_ENDED -> "ENDED";
            default -> "UNSET";
        };
    }

    private void line(String text) {
        report.append(text).append('\n');
        output.setText(report.toString());
        Log.i(TAG, text);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (session != null) {
            try {
                session.release();
            } catch (Throwable e) {
                Log.w(TAG, "release failed", e);
            }
            session = null;
        }
    }
}
