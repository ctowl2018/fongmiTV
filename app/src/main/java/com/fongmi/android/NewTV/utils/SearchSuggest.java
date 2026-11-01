package com.fongmi.android.NewTV.utils;

import android.text.TextUtils;

import com.fongmi.android.NewTV.bean.Word;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 搜索联想词的两个第三方来源与合并逻辑。
 *
 * <p><b>接口未公开</b>：爱奇艺与腾讯这两个地址都是从官方 App 的流量里取的，没有文档、
 * 没有 SLA、没有版本号，对方随时可能改结构或直接封禁。因此：
 * <ul>
 *   <li>解析全程用 try/catch 吞异常，失败返回空列表，绝不让搜索页崩；</li>
 *   <li>调用方要能把空结果当作"没有联想"正常降级；</li>
 *   <li>关键词会明文发往第三方，所以受「联网搜索建议」开关控制（见 {@code Setting#isSearchSuggest()}）。</li>
 * </ul>
 */
public final class SearchSuggest {

    /** 合并后的条数上限。 */
    public static final int LIMIT = 20;

    private SearchSuggest() {
    }

    public static String iqiyiUrl(String keyword) {
        return "https://suggest.video.iqiyi.com/?if=mobile&key=" + encode(keyword);
    }

    public static String tencentUrl(String keyword) {
        return "https://tv.aiseet.atianqi.com/i-tvbin/qtv_video/search/get_search_smart_box?format=json&page_num=0&page_size=10&key=" + encode(keyword);
    }

    /** 爱奇艺：{@code {code, data:[{name, ...}]}}，结构刚好能被 {@link Word} 的 alternate 兜住。 */
    public static List<Word.Data> parseIqiyi(String result) {
        return Word.objectFrom(result).getData();
    }

    /**
     * 腾讯：{@code {data:{search_data:{vecGroupData:[{group_data:[...]}]}}}。
     *
     * <p>三级嵌套，且关键词带 {@code <hl>} 高亮标签，需要剥掉。
     * 取词优先用 {@code action.actionArgs.search_keyword.strVal}，
     * 取不到再退回 {@code cell_info.title}。
     */
    public static List<Word.Data> parseTencent(String result) {
        List<Word.Data> items = new ArrayList<>();
        try {
            JsonObject root = Json.parse(result).getAsJsonObject();
            JsonObject search = root.getAsJsonObject("data").getAsJsonObject("search_data");
            JsonArray groups = search.getAsJsonArray("vecGroupData");
            for (JsonElement group : groups) {
                JsonArray data = group.getAsJsonObject().getAsJsonArray("group_data");
                if (data == null) continue;
                for (JsonElement element : data) {
                    String title = tencentTitle(element.getAsJsonObject());
                    if (!TextUtils.isEmpty(title)) items.add(Word.Data.create(title));
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    /** 按出现顺序去重合并，只保留前 {@link #LIMIT} 条。 */
    public static List<Word.Data> merge(List<Word.Data> first, List<Word.Data> second) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        addAll(set, first);
        addAll(set, second);
        return set.stream().limit(LIMIT).map(Word.Data::create).toList();
    }

    private static void addAll(LinkedHashSet<String> set, List<Word.Data> items) {
        if (items == null) return;
        for (Word.Data item : items) {
            String title = clean(item.getTitle());
            if (!TextUtils.isEmpty(title)) set.add(title);
        }
    }

    private static String tencentTitle(JsonObject item) {
        try {
            JsonObject args = item.getAsJsonObject("action").getAsJsonObject("actionArgs");
            String title = args.getAsJsonObject("search_keyword").getAsJsonPrimitive("strVal").getAsString();
            if (!TextUtils.isEmpty(title)) return clean(title);
        } catch (Exception ignored) {
        }
        try {
            return clean(item.getAsJsonObject("cell_info").getAsJsonPrimitive("title").getAsString());
        } catch (Exception ignored) {
            return "";
        }
    }

    /** 去掉腾讯返回里的 {@code <hl>…</hl>} 高亮标签并压缩空白。 */
    private static String clean(String title) {
        if (TextUtils.isEmpty(title)) return "";
        return title.replaceAll("(?i)</?hl>", "").replaceAll("\\s+", " ").trim();
    }

    private static String encode(String keyword) {
        try {
            return URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return keyword;
        }
    }
}
