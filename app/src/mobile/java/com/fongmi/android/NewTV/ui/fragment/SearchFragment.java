package com.fongmi.android.NewTV.ui.fragment;

import static androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.NewTV.App;
import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.bean.Word;
import com.fongmi.android.NewTV.databinding.FragmentSearchBinding;
import com.fongmi.android.NewTV.impl.Callback;
import com.fongmi.android.NewTV.setting.Setting;
import com.fongmi.android.NewTV.ui.adapter.RecordAdapter;
import com.fongmi.android.NewTV.ui.adapter.WordAdapter;
import com.fongmi.android.NewTV.ui.base.BaseFragment;
import com.fongmi.android.NewTV.ui.custom.CustomTextListener;
import com.fongmi.android.NewTV.ui.dialog.SiteDialog;
import com.fongmi.android.NewTV.utils.SearchSuggest;
import com.fongmi.android.NewTV.utils.Util;
import com.github.catvod.net.OkHttp;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import okhttp3.Call;
import okhttp3.Response;

public class SearchFragment extends BaseFragment implements MenuProvider, WordAdapter.OnClickListener, RecordAdapter.OnClickListener {

    private static final int HOT_LIMIT = 10;

    private FragmentSearchBinding mBinding;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;
    private WordAdapter mMovieAdapter;
    private WordAdapter mTvAdapter;

    /** 联想词序号：双源并发返回，用序号丢弃过期响应。 */
    private int mSuggestSeq;
    private List<Word.Data> mIqiyi = List.of();
    private List<Word.Data> mTencent = List.of();

    private interface HotSaver {

        void save(String result);
    }

    public static SearchFragment newInstance(String keyword) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        SearchFragment fragment = new SearchFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return getArguments().getString("keyword");
    }

    private boolean empty() {
        return mBinding.keyword.getText().toString().trim().isEmpty();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initMenu() {
        if (isHidden()) return;
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.setSupportActionBar(mBinding.toolbar);
        activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        activity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        activity.setTitle("");
    }

    @Override
    protected void initView() {
        setRecyclerView();
        checkKeyword();
        search();
    }

    private void setRecyclerView() {
        mBinding.wordRecycler.setHasFixedSize(false);
        mBinding.wordRecycler.setAdapter(mWordAdapter = new WordAdapter(this));
        mBinding.wordRecycler.setLayoutManager(new FlexboxLayoutManager(getContext(), FlexDirection.ROW));
        mBinding.movieWordRecycler.setHasFixedSize(false);
        mBinding.movieWordRecycler.setAdapter(mMovieAdapter = new WordAdapter(this));
        mBinding.movieWordRecycler.setLayoutManager(new FlexboxLayoutManager(getContext(), FlexDirection.ROW));
        mBinding.tvWordRecycler.setHasFixedSize(false);
        mBinding.tvWordRecycler.setAdapter(mTvAdapter = new WordAdapter(this));
        mBinding.tvWordRecycler.setLayoutManager(new FlexboxLayoutManager(getContext(), FlexDirection.ROW));
        mBinding.recordRecycler.setHasFixedSize(false);
        mBinding.recordRecycler.setAdapter(mRecordAdapter = new RecordAdapter(this));
        mBinding.recordRecycler.setLayoutManager(new FlexboxLayoutManager(getContext(), FlexDirection.ROW));
    }

    @Override
    protected void initEvent() {
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) search();
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                requireActivity().invalidateOptionsMenu();
                getWord(s.toString());
            }
        });
        getParentFragmentManager().setFragmentResultListener("result", getViewLifecycleOwner(), (requestKey, bundle) -> {
            if (bundle.getBoolean("edit", false)) Util.showKeyboard(mBinding.keyword);
        });
    }

    private void checkKeyword() {
        boolean visible = requireActivity().getSupportFragmentManager().findFragmentByTag(CollectFragment.class.getSimpleName()) != null;
        if (TextUtils.isEmpty(getKeyword()) && !visible) Util.showKeyboard(mBinding.keyword);
        setKeyword(getKeyword());
        getWord(getKeyword());
    }

    private void setKeyword(String text) {
        mBinding.keyword.setText(text);
        mBinding.keyword.setSelection(text.length());
    }

    private void search() {
        if (empty()) return;
        String keyword = mBinding.keyword.getText().toString().trim();
        App.post(() -> mRecordAdapter.add(keyword), 250);
        Util.hideKeyboard(mBinding.keyword);
        collect(keyword);
    }

    private void collect(String keyword) {
        FragmentManager fm = requireActivity().getSupportFragmentManager();
        String collectTag = CollectFragment.class.getSimpleName();
        if (fm.findFragmentByTag(collectTag) != null) return;
        String searchTag = SearchFragment.class.getSimpleName();
        FragmentTransaction ft = fm.beginTransaction().setTransition(TRANSIT_FRAGMENT_OPEN);
        ft.add(R.id.container, CollectFragment.newInstance(keyword), collectTag);
        Optional.ofNullable(fm.findFragmentByTag(searchTag)).ifPresent(ft::hide);
        ft.setReorderingAllowed(true).addToBackStack(null).commit();
    }

    private void getWord(String text) {
        if (text.isEmpty()) getHot();
        else getSuggest(text);
    }

    /**
     * 三个分类各自「先渲染缓存 → 超时才刷新」。榜单低频，15 分钟内只读缓存；
     * 请求失败静默降级为缓存内容。
     */
    private void getHot() {
        mSuggestSeq++;
        mBinding.word.setText(R.string.search_hot);
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
        OkHttp.newCall("https://api.web.360kan.com/v1/rank?cat=" + cat, Map.of(HttpHeaders.REFERER, "https://www.360kan.com/rank/general")).enqueue(new Callback() {
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
                // 静默降级
            }
        });
    }

    /**
     * 联想词：关闭「联网搜索建议」后完全不发请求；开启时两源并发，序号丢弃过期响应后合并去重。
     */
    private void getSuggest(String text) {
        int seq = ++mSuggestSeq;
        mIqiyi = List.of();
        mTencent = List.of();
        mBinding.word.setText(R.string.search_suggest);
        hideHotGroups();
        mWordAdapter.setItems(new ArrayList<>());
        if (!Setting.isSearchSuggest()) return;
        requestSuggest(SearchSuggest.iqiyiUrl(text), seq, false);
        requestSuggest(SearchSuggest.tencentUrl(text), seq, true);
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
        boolean hasWord = mWordAdapter.getItemCount() > 0;
        mBinding.word.setVisibility(hasWord ? View.VISIBLE : View.GONE);
        mBinding.wordRecycler.setVisibility(hasWord ? View.VISIBLE : View.GONE);
    }

    private void hideHotGroups() {
        mBinding.movieWord.setVisibility(View.GONE);
        mBinding.movieWordRecycler.setVisibility(View.GONE);
        mBinding.tvWord.setVisibility(View.GONE);
        mBinding.tvWordRecycler.setVisibility(View.GONE);
    }

    private void onReset() {
        mBinding.keyword.setText("");
        requireActivity().invalidateOptionsMenu();
    }

    private void onSite() {
        Util.hideKeyboard(mBinding.keyword);
        mBinding.keyword.post(() -> SiteDialog.create().search().show(this));
    }

    @Override
    public void onItemClick(String text) {
        setKeyword(text);
        search();
    }

    @Override
    public void onDataChanged(int size) {
        mBinding.record.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        mBinding.recordRecycler.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        mBinding.recordRecycler.postDelayed(() -> mBinding.recordRecycler.requestLayout(), 250);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_search, menu);
    }

    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        menu.findItem(R.id.action_reset).setVisible(!empty());
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) requireActivity().getOnBackPressedDispatcher().onBackPressed();
        if (menuItem.getItemId() == R.id.action_reset) onReset();
        if (menuItem.getItemId() == R.id.action_site) onSite();
        return true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) requireActivity().removeMenuProvider(this);
        else initMenu();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().removeMenuProvider(this);
    }
}
