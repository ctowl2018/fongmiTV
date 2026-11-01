package com.fongmi.android.NewTV.searchprobe;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.fongmi.android.NewTV.bean.Word;
import com.fongmi.android.NewTV.setting.Setting;
import com.fongmi.android.NewTV.utils.SearchSuggest;
import com.github.catvod.net.OkHttp;

import java.util.List;
import java.util.Map;


/**
 * 搜索推荐链路自检（仅 debug 源集，不进 release）。
 *
 * <p>验证四件事：
 * <ol>
 *   <li>360kan 三个分类能否拿到并解析；</li>
 *   <li>爱奇艺联想能否解析；</li>
 *   <li>腾讯联想的三层嵌套 + {@code <hl>} 清理是否生效；</li>
 *   <li>双源合并去重后条数是否合理。</li>
 * </ol>
 */
public class SearchProbeActivity extends Activity {

    private static final String TAG = "SearchProbe";

    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 32, 32, 32);
        scroll.addView(container);
        setContentView(scroll);
        new Thread(this::run).start();
    }

    private void run() {
        line("=== 搜索推荐自检 ===");
        line("联网建议开关 = " + Setting.isSearchSuggest());

        rank(1, "综合");
        rank(2, "电影");
        rank(3, "剧集");

        suggest("繁花");
        line("结论见上方各行；解析失败会显示 0 条。");
    }

    private void rank(int cat, String name) {
        try {
            String url = "https://api.web.360kan.com/v1/rank?cat=" + cat;
            String result = OkHttp.string(url, Map.of("Referer", "https://www.360kan.com/rank/general"));
            List<Word.Data> data = Word.objectFrom(result).getData();
            int limit = Math.min(data.size(), 10);
            post(() -> line("[榜单] " + name + " cat=" + cat + " 原始 " + data.size() + " 条，展示 " + limit + " 条：" + titles(data.subList(0, limit))));
        } catch (Throwable e) {
            post(() -> line("[榜单] " + name + " cat=" + cat + " 失败：" + e));
        }
    }

    private void suggest(String keyword) {
        try {
            String iqiyiRaw = OkHttp.string(SearchSuggest.iqiyiUrl(keyword));
            List<Word.Data> iqiyi = SearchSuggest.parseIqiyi(iqiyiRaw);
            post(() -> line("[联想] 爱奇艺 " + iqiyi.size() + " 条：" + titles(iqiyi)));
        } catch (Throwable e) {
            post(() -> line("[联想] 爱奇艺失败：" + e));
        }
        try {
            String tencentRaw = OkHttp.string(SearchSuggest.tencentUrl(keyword));
            List<Word.Data> tencent = SearchSuggest.parseTencent(tencentRaw);
            post(() -> line("[联想] 腾讯 " + tencent.size() + " 条：" + titles(tencent)));
            try {
                List<Word.Data> merged = SearchSuggest.merge(SearchSuggest.parseIqiyi(OkHttp.string(SearchSuggest.iqiyiUrl(keyword))), tencent);
                post(() -> line("[联想] 合并去重后 " + merged.size() + " 条：" + titles(merged)));
            } catch (Throwable ignored) {
            }
        } catch (Throwable e) {
            post(() -> line("[联想] 腾讯失败：" + e));
        }
    }

    private String titles(List<Word.Data> data) {
        StringBuilder builder = new StringBuilder();
        for (Word.Data item : data) {
            if (builder.length() > 0) builder.append(" / ");
            builder.append(item.getTitle());
        }
        return builder.toString();
    }

    private void line(String text) {
        Log.i(TAG, text);
        post(() -> {
            TextView view = new TextView(this);
            view.setText(text);
            view.setTextSize(13);
            container.addView(view);
        });
    }

    private void post(Runnable runnable) {
        runOnUiThread(runnable);
    }
}
