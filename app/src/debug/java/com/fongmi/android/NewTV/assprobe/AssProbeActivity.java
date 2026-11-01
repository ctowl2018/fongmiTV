package com.fongmi.android.NewTV.assprobe;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import io.github.peerless2012.ass.Ass;
import io.github.peerless2012.ass.AssFrame;
import io.github.peerless2012.ass.AssRender;
import io.github.peerless2012.ass.AssTex;
import io.github.peerless2012.ass.AssTexType;
import io.github.peerless2012.ass.AssTrack;

/**
 * libass 渲染自检（仅 debug 源集，release 不包含）。
 *
 * <p>不走播放器，直接用 ass-kt 的 native API 渲染指定时间点的一帧，
 * 用来确认三件事：原生库能否加载、ASS 能否解析、特效位图能否产出。
 */
public class AssProbeActivity extends Activity {

    private static final String TAG = "AssProbe";
    private static final String[] CANDIDATES = {
            "/sdcard/Movies/test.ass",
            "/storage/emulated/0/Movies/test.ass",
    };
    private static final int W = 640;
    private static final int H = 360;
    private static final long[] TIMES = {200, 1500, 3500};

    private final StringBuilder report = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        try {
            run();
        } catch (Throwable e) {
            Log.e(TAG, "probe failed", e);
            line("异常: " + e);
            e.printStackTrace();
            for (StackTraceElement el : e.getStackTrace()) {
                if (el.getClassName().startsWith("io.github.peerless2012")
                        || el.getClassName().startsWith("com.fongmi")) {
                    line("  at " + el);
                }
            }
        }

        TextView tv = new TextView(this);
        tv.setText(report.toString());
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(11);
        tv.setGravity(Gravity.TOP | Gravity.START);
        tv.setPadding(16, 16, 16, 16);
        root.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        Log.i(TAG, report.toString());
    }

    private void run() throws Exception {
        long t0 = System.currentTimeMillis();
        Ass ass = new Ass();
        line("1. libass 加载成功 (" + (System.currentTimeMillis() - t0) + "ms)");

        File file = null;
        for (String path : CANDIDATES) {
            File f = new File(path);
            if (f.exists()) {
                file = f;
                break;
            }
        }
        if (file == null) {
            line("2. 找不到 ASS 文件，尝试过: " + String.join(", ", CANDIDATES));
            return;
        }

        byte[] data;
        try (InputStream in = new FileInputStream(file)) {
            data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) break;
                read += n;
            }
        }
        line("2. 读入 " + file.getAbsolutePath() + " (" + data.length + " bytes)");

        AssTrack track = ass.createTrack();
        track.readBuffer(data, 0, data.length);
        line("3. 解析完成 PlayRes=" + track.getWidth() + "x" + track.getHeight()
                + " 事件数=" + track.getEvents().length);

        AssRender render = ass.createRender();
        render.setFrameSize(W, H);
        render.setTrack(track);
        line("4. 渲染器就绪 frameSize=" + W + "x" + H);

        Bitmap composite = null;
        for (long time : TIMES) {
            AssFrame frame = render.renderFrame(time, AssTexType.BITMAP_ALPHA);
            AssTex[] images = frame.getImages();
            line("   t=" + time + "ms 位图块=" + images.length + " changed=" + frame.getChanged());
            if (images.length == 0) continue;

            Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            canvas.drawColor(Color.rgb(32, 32, 40));
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            for (AssTex tex : images) {
                if (tex.getBitmap() == null) continue;
                paint.setColorFilter(new PorterDuffColorFilter(tex.getColor(), PorterDuff.Mode.SRC_IN));
                canvas.drawBitmap(tex.getBitmap(), tex.getX(), tex.getY(), paint);
                line("     block x=" + tex.getX() + " y=" + tex.getY()
                        + " w=" + tex.getW() + " h=" + tex.getH()
                        + " color=#" + Integer.toHexString(tex.getColor()));
            }
            composite = bmp;
        }

        if (composite != null) {
            final Bitmap shown = composite;
            FrameLayout preview = new FrameLayout(this);
            View view = new View(this) {
                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    canvas.drawBitmap(shown, null, new android.graphics.Rect(0, 0, getWidth(), getHeight()), null);
                }
            };
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(W, H);
            lp.gravity = Gravity.BOTTOM;
            preview.addView(view, lp);
            line("5. 合成帧已生成，见下方预览");
        }
        line("结论: libass 在当前设备可用");
    }

    private void line(String text) {
        report.append(text).append('\n');
        Log.i(TAG, text);
    }
}
