package com.fongmi.android.NewTV.assprobe;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.NewTV.player.compat.DecoderMode;
import com.fongmi.android.NewTV.player.exo.ExoUtil;
import com.fongmi.android.NewTV.player.subtitle.AssSubtitleSupport;
import com.fongmi.android.NewTV.player.track.DecodeCodecSelector;

import java.io.File;

/**
 * 播放链路自检（仅 debug 源集）。
 *
 * <p>串行跑三种装配，定位"无法播放"是在哪一步引入的：
 * <ol>
 *   <li>A：不启用 ASS（基线）</li>
 *   <li>B：启用 ASS，仅包装 RenderersFactory，不挂 View</li>
 *   <li>C：启用 ASS + 挂 AssSubtitleView（完整路径）</li>
 * </ol>
 * 只创建 player、不接 PlayerView，避免表面层干扰，专注装配差异。
 */
public class PlayProbeActivity extends Activity {

    private static final String TAG = "PlayProbe";
    private static final String VIDEO = "/sdcard/Movies/test.mp4";
    private static final long WATCH_MS = 4000;

    private final StringBuilder report = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView output;
    private FrameLayout host;
    private ExoPlayer player;
    private AssSubtitleSupport ass;
    private PlayerView playerView;
    private int state = Integer.MIN_VALUE;
    private String error = "none";
    private String videoSize = "-";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        host = new FrameLayout(this);
        host.setBackgroundColor(Color.BLACK);
        output = new TextView(this);
        output.setTextColor(Color.WHITE);
        output.setTextSize(10);
        output.setGravity(Gravity.TOP | Gravity.START);
        output.setPadding(12, 12, 12, 12);
        host.addView(output, new FrameLayout.LayoutParams(-1, -1));
        setContentView(host);
        run(0);
    }

    private void run(int step) {
        release();
        String name;
        switch (step) {
            case 0 -> name = "A 基线（无 ASS）";
            case 1 -> name = "B 仅包装 RenderersFactory";
            case 2 -> name = "C 完整（+ AssSubtitleView）";
            case 3 -> name = "D FFMPEG 模式（应回退软解）";
            default -> {
                line("");
                line("=== 结束 ===");
                return;
            }
        }
        line("");
        line("=== " + name + " ===");

        if (step == 3) {
            DecodeCodecSelector.setMode(C.TRACK_TYPE_AUDIO, DecoderMode.FFMPEG);
            line("  audio 模式设为 FFMPEG，扩展可用性=" + DecodeCodecSelector.FFMPEG_EXTENSION_USABLE);
        }

        File file = new File(VIDEO);
        if (!file.exists()) {
            line("素材缺失: " + VIDEO);
            return;
        }

        try {
            ass = step == 0 ? null : AssSubtitleSupport.create();
            line("AssSubtitleSupport: " + (ass == null ? "null（已降级）" : "created"));

            player = ExoUtil.buildPlayer(null, ass);
            line("player 创建成功 renderer 数=" + player.getRendererCount());

            if (step == 2 && ass != null) {
                playerView = new PlayerView(this);
                host.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
                playerView.setPlayer(player);
                ass.bindPlayerView(playerView);
                line("AssSubtitleView 已挂载 attached=" + ass.isAttached());
            }

            state = Integer.MIN_VALUE;
            error = "none";
            videoSize = "-";
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int newState) {
                    state = newState;
                    line("  state -> " + stateName(newState));
                }

                @Override
                public void onPlayerError(PlaybackException e) {
                    error = e.getErrorCodeName() + " / " + e.getMessage();
                    line("  ERROR -> " + error);
                }

                @Override
                public void onVideoSizeChanged(androidx.media3.common.VideoSize size) {
                    videoSize = size.width + "x" + size.height;
                    line("  videoSize -> " + videoSize);
                }
            });

            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
            player.prepare();
            player.play();
            line("prepare+play 已调用");
        } catch (Throwable e) {
            line("  异常: " + e);
            Log.e(TAG, "step " + step + " failed", e);
            e.printStackTrace();
            player = null;
            handler.postDelayed(() -> run(step + 1), 300);
            return;
        }

        handler.postDelayed(() -> {
            line("  结果: state=" + stateName(state) + " videoSize=" + videoSize + " error=" + error);
            line("  判定: " + judge());
            run(step + 1);
        }, WATCH_MS);
    }

    private String judge() {
        if (!"none".equals(error)) return "失败（有错误）";
        if (state == Player.STATE_READY) return "成功（已就绪）";
        if (state == Player.STATE_ENDED) return "成功（播完）";
        if (state == Player.STATE_BUFFERING) return "卡在缓冲";
        if (state == Player.STATE_IDLE) return "未开始";
        return "未知";
    }

    private static String stateName(int s) {
        return switch (s) {
            case Player.STATE_IDLE -> "IDLE";
            case Player.STATE_BUFFERING -> "BUFFERING";
            case Player.STATE_READY -> "READY";
            case Player.STATE_ENDED -> "ENDED";
            default -> "UNSET";
        };
    }

    private void release() {
        handler.removeCallbacksAndMessages(null);
        DecodeCodecSelector.reset(DecoderMode.AUTO, DecoderMode.AUTO);
        if (playerView != null) {
            playerView.setPlayer(null);
            host.removeView(playerView);
            playerView = null;
        }
        if (player != null) {
            try {
                player.release();
            } catch (Throwable e) {
                Log.w(TAG, "release player failed", e);
            }
            player = null;
        }
        if (ass != null) {
            try {
                ass.release();
            } catch (Throwable e) {
                Log.w(TAG, "release ass failed", e);
            }
            ass = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        release();
        Log.i(TAG, report.toString());
    }

    private void line(String text) {
        report.append(text).append('\n');
        output.setText(report.toString());
        Log.i(TAG, text);
    }
}
