package com.fongmi.android.NewTV.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.NewTV.App;
import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.bean.Word;
import com.fongmi.android.NewTV.databinding.ActivitySearchBinding;
import com.fongmi.android.NewTV.impl.Callback;
import com.fongmi.android.NewTV.setting.Setting;
import com.fongmi.android.NewTV.ui.adapter.RecordAdapter;
import com.fongmi.android.NewTV.ui.adapter.WordAdapter;
import com.fongmi.android.NewTV.ui.base.BaseActivity;
import com.fongmi.android.NewTV.ui.custom.CustomKeyboard;
import com.fongmi.android.NewTV.ui.custom.CustomTextListener;
import com.fongmi.android.NewTV.ui.dialog.LocalConnectDialog;
import com.fongmi.android.NewTV.ui.dialog.SiteDialog;
import com.fongmi.android.NewTV.utils.KeyUtil;
import com.fongmi.android.NewTV.utils.SearchSuggest;
import com.fongmi.android.NewTV.utils.Util;
import com.fongmi.android.NewTV.utils.ZhuToPin;
import com.github.catvod.net.OkHttp;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Response;

public class SearchActivity extends BaseActivity implements WordAdapter.OnClickListener, RecordAdapter.OnClickListener, CustomKeyboard.Callback {

    private static final int HOT_LIMIT = 10;

    private ActivitySearchBinding mBinding;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;
    private WordAdapter mMovieAdapter;
    private WordAdapter mTvAdapter;

    /** 联想词序号：双源并发返回，用序号丢弃过期响应，避免旧结果覆盖新结果。 */
    private int mSuggestSeq;
    private List<Word.Data> mIqiyi = List.of();
    private List<Word.Data> mTencent = List.of();

    private interface HotSaver {

        void save(String result);
    }

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SearchActivity.class));
    }

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        String keyword = getIntent().getStringExtra("keyword");
        return keyword != null ? keyword : "";
    }

    private boolean empty() {
        return mBinding.keyword.getText().toString().trim().isEmpty();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        CustomKeyboard.init(this, mBinding);
        setRecyclerView();
        checkKeyword();
        onSearch();
    }

    @Override
    protected void initEvent() {
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onSearch();
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                getWord(s.toString());
            }
        });
        mBinding.mic.setOnClickListener(v -> mBinding.mic.start());
        mBinding.mic.setListener(this, new CustomTextListener() {
            @Override
            public void onResults(String result) {
                if (!result.isEmpty()) setKeyword(result);
                mBinding.keyword.requestFocus();
            }
        });
    }

    private void setRecyclerView() {
        mBinding.wordRecycler.setItemAnimator(null);
        mBinding.wordRecycler.setHasFixedSize(false);
        mBinding.wordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.wordRecycler.setAdapter(mWordAdapter = new WordAdapter(this));
        mBinding.movieWordRecycler.setItemAnimator(null);
        mBinding.movieWordRecycler.setHasFixedSize(false);
        mBinding.movieWordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.movieWordRecycler.setAdapter(mMovieAdapter = new WordAdapter(this));
        mBinding.tvWordRecycler.setItemAnimator(null);
        mBinding.tvWordRecycler.setHasFixedSize(false);
        mBinding.tvWordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.tvWordRecycler.setAdapter(mTvAdapter = new WordAdapter(this));
        mBinding.recordRecycler.setHasFixedSize(false);
        mBinding.recordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.recordRecycler.setAdapter(mRecordAdapter = new RecordAdapter(this));
    }

    private void checkKeyword() {
        setKeyword(getKeyword());
        getWord(getKeyword());
    }

    private void setKeyword(String text) {
        mBinding.keyword.setText(text);
        mBinding.keyword.setSelection(text.length());
    }

    private void getWord(String text) {
        if (text.isEmpty()) getHot();
        else getSuggest(text);
    }

    /**
     * 三个分类各自「先渲染缓存 → 超时才刷新」。
     *
     * <p>榜单是低频数据，没必要每次进搜索页都打三次请求；15 分钟内只读缓存。
     * 任一请求失败都静默降级为缓存内容，不弹提示、不清空已有数据。
     */
    private void getHot() {
        mSuggestSeq++;
        mBinding.word.setText(R.string.search_hot);
        showHotGroups(true);
        bindHot(mWordAdapter, mBinding.wordRecycler, mBinding.word, Setting.getHot(), true);
        bindHot(mMovieAdapter, mBinding.movieWordRecycler, mBinding.movieWord, Setting.getHotMovie(), false);
        bindHot(mTvAdapter, mBinding.tvWordRecycler, mBinding.tvWord, Setting.getHotTv(), false);
        if (!Setting.isHotExpired()) return;
        requestRank(1, mWordAdapter, mBinding.wordRecycler, mBinding.word, Setting::putHot);
        requestRank(2, mMovieAdapter, mBinding.movieWordRecycler, mBinding.movieWord, Setting::putHotMovie);
        requestRank(3, mTvAdapter, mBinding.tvWordRecycler, mBinding.tvWord, Setting::putHotTv);
    }

    private void bindHot(WordAdapter adapter, RecyclerView list, TextView label, String json, boolean alwaysVisible) {
        List<Word.Data> data = limit(Word.objectFrom(json).getData());
        adapter.setItems(data);
        boolean show = alwaysVisible || !data.isEmpty();
        label.setVisibility(show ? View.VISIBLE : View.GONE);
        list.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private List<Word.Data> limit(List<Word.Data> data) {
        return new ArrayList<>(data.subList(0, Math.min(data.size(), HOT_LIMIT)));
    }

    private void requestRank(int cat, WordAdapter adapter, RecyclerView list, TextView label, HotSaver saver) {
        OkHttp.newCall(rankUrl(cat), Map.of(HttpHeaders.REFERER, "https://www.360kan.com/rank/general")).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String result = response.body().string();
                if (TextUtils.isEmpty(result)) return;
                App.post(() -> {
                    if (!empty()) return;
                    Setting.putHotTime();
                    saver.save(result);
                    bindHot(adapter, list, label, result, cat == 1);
                });
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // 静默降级：保留缓存内容，不打扰用户
            }
        });
    }

    private String rankUrl(int cat) {
        return "https://api.web.360kan.com/v1/rank?cat=" + cat;
    }

    /**
     * 联想词：关掉「联网搜索建议」就完全不发请求，避免把输入内容外发。
     * 开启时爱奇艺 + 腾讯并发，用序号丢弃过期响应，最后按出现顺序合并去重。
     */
    private void getSuggest(String text) {
        int seq = ++mSuggestSeq;
        mIqiyi = List.of();
        mTencent = List.of();
        mBinding.word.setText(R.string.search_suggest);
        showHotGroups(false);
        mWordAdapter.setItems(new ArrayList<>());
        if (!Setting.isSearchSuggest()) return;
        String keyword = ZhuToPin.get(text);
        requestSuggest(SearchSuggest.iqiyiUrl(keyword), seq, false);
        requestSuggest(SearchSuggest.tencentUrl(keyword), seq, true);
    }

    private void requestSuggest(String url, int seq, boolean tencent) {
        OkHttp.newCall(url).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String result = response.body().string();
                if (TextUtils.isEmpty(result)) return;
                App.post(() -> applySuggest(seq, result, tencent));
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // 单源失败不影响另一源
            }
        });
    }

    private void applySuggest(int seq, String result, boolean tencent) {
        if (seq != mSuggestSeq || empty()) return;
        if (tencent) mTencent = SearchSuggest.parseTencent(result);
        else mIqiyi = SearchSuggest.parseIqiyi(result);
        mWordAdapter.setItems(SearchSuggest.merge(mIqiyi, mTencent));
        updateSuggestVisibility();
    }

    private void updateSuggestVisibility() {
        boolean hasWord = mWordAdapter.getItemCount() > 0;
        mBinding.word.setVisibility(hasWord ? View.VISIBLE : View.GONE);
        mBinding.wordRecycler.setVisibility(hasWord ? View.VISIBLE : View.GONE);
    }

    private void showHotGroups(boolean show) {
        if (show) return;
        mBinding.movieWord.setVisibility(View.GONE);
        mBinding.movieWordRecycler.setVisibility(View.GONE);
        mBinding.tvWord.setVisibility(View.GONE);
        mBinding.tvWordRecycler.setVisibility(View.GONE);
    }

    @Override
    public void onItemClick(String text) {
        setKeyword(text);
        onSearch();
    }

    @Override
    public void onDataChanged(int size) {
        mBinding.recordLayout.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        if (size == 0) focusFirst(mBinding.wordRecycler);
    }

    @Override
    public void onSearch() {
        if (empty()) return;
        String keyword = mBinding.keyword.getText().toString().trim();
        App.post(() -> mRecordAdapter.add(keyword), 250);
        Util.hideKeyboard(mBinding.keyword);
        CollectActivity.start(this, keyword);
    }

    @Override
    public void showDialog() {
        SiteDialog.create().search().show(this);
    }

    @Override
    public void onRemote() {
        LocalConnectDialog.show(this, "search");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) showDialog();
        if (KeyUtil.isActionDown(event) && findFocus(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private boolean findFocus(KeyEvent event) {
        View current = getCurrentFocus();
        if (current == mBinding.keyword) return handleKeywordKey(event);
        View inKeyboard = mBinding.keyboard.findContainingItemView(current);
        View inWord = mBinding.wordRecycler.findContainingItemView(current);
        View inRecord = mBinding.recordRecycler.findContainingItemView(current);
        if (inKeyboard != null) return handleKeyboardKey(event, inKeyboard);
        if (inRecord != null) return handleRecordKey(event, inRecord);
        if (inWord != null) return handleWordKey(event, inWord);
        return false;
    }

    private View findNearestInLastRow(RecyclerView rv, int targetLeft) {
        if (rv.getChildCount() == 0) return null;
        int lastTop = rv.getChildAt(rv.getChildCount() - 1).getTop();
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == lastTop) {
                int dist = Math.abs(child.getLeft() - targetLeft);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = child;
                }
            }
        }
        return nearest;
    }

    private boolean isFirstRow(RecyclerView rv, View item) {
        View first = rv.getChildAt(0);
        return first != null && item.getTop() == first.getTop();
    }

    private boolean isLastRow(RecyclerView rv, View item) {
        View last = rv.getChildAt(rv.getChildCount() - 1);
        return last != null && item.getTop() == last.getTop();
    }

    private boolean isFirstInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int left = focused.getLeft();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == top && child.getLeft() < left) return false;
        }
        return true;
    }

    private boolean isLastInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int right = focused.getRight();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == top && child.getRight() > right) return false;
        }
        return true;
    }

    private boolean handleKeywordKey(KeyEvent event) {
        if (!KeyUtil.isRightKey(event)) return false;
        if (mBinding.keyword.getSelectionEnd() < mBinding.keyword.getText().length()) return false;
        boolean hasRecord = mBinding.recordLayout.getVisibility() == View.VISIBLE;
        return focusFirst(hasRecord ? mBinding.recordRecycler : mBinding.wordRecycler);
    }

    private boolean handleKeyboardKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.keyboard, item)) {
            mBinding.keyword.requestFocus();
            return true;
        }
        if (KeyUtil.isLeftKey(event) && isFirstInRow(mBinding.keyboard, item)) return true;
        return KeyUtil.isDownKey(event) && isLastRow(mBinding.keyboard, item);
    }

    private boolean handleWordKey(KeyEvent event, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(mBinding.wordRecycler, item);
        if (KeyUtil.isDownKey(event)) return isLastRow(mBinding.wordRecycler, item);
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.wordRecycler, item)) {
            if (mBinding.recordLayout.getVisibility() == View.VISIBLE) {
                View child = findNearestInLastRow(mBinding.recordRecycler, item.getLeft());
                if (child != null) {
                    mBinding.scroll.smoothScrollTo(0, 0);
                    child.requestFocus();
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    private boolean handleRecordKey(KeyEvent event, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(mBinding.recordRecycler, item);
        if (KeyUtil.isUpKey(event)) return isFirstRow(mBinding.recordRecycler, item);
        if (KeyUtil.isDownKey(event) && isLastRow(mBinding.recordRecycler, item)) return focusFirst(mBinding.wordRecycler);
        return false;
    }

    private boolean focusFirst(RecyclerView rv) {
        View child = rv.getChildAt(0);
        if (child == null) return false;
        child.requestFocus();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBinding.mic.setFocusable(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBinding.mic.setFocusable(true);
        mBinding.keyword.requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBinding.mic.destroy();
    }
}
