package com.fongmi.android.NewTV.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.databinding.FragmentSettingPlayerBinding;
import com.fongmi.android.NewTV.impl.BufferListener;
import com.fongmi.android.NewTV.impl.UaListener;
import com.fongmi.android.NewTV.setting.PlayerSetting;
import com.fongmi.android.NewTV.setting.Setting;
import com.fongmi.android.NewTV.setting.SubtitleSetting;
import com.fongmi.android.NewTV.ui.activity.HomeActivity;
import com.fongmi.android.NewTV.ui.base.BaseFragment;
import com.fongmi.android.NewTV.ui.dialog.BufferDialog;
import com.fongmi.android.NewTV.ui.dialog.UaDialog;
import com.fongmi.android.NewTV.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingPlayerFragment extends BaseFragment implements UaListener, BufferListener {

    private FragmentSettingPlayerBinding mBinding;
    private String[] render;
    private String[] background;
    private String[] scale;
    private String[] assLimit;

    public static SettingPlayerFragment newInstance() {
        return new SettingPlayerFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingPlayerBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setPlaybackModeText();
        mBinding.adblockText.setText(Setting.getSwitch(Setting.isAdblock()));
        mBinding.libassText.setText(Setting.getSwitch(PlayerSetting.isLibass()));
        mBinding.bufferText.setText(String.valueOf(PlayerSetting.getBuffer()));
        mBinding.backgroundText.setText((background = ResUtil.getStringArray(R.array.select_background))[PlayerSetting.getBackground()]);
        mBinding.scaleText.setText((scale = ResUtil.getStringArray(R.array.select_scale))[PlayerSetting.getScale()]);
        mBinding.assLimitText.setText((assLimit = ResUtil.getStringArray(R.array.select_ass_limit))[SubtitleSetting.getAssRenderLimit()]);
        mBinding.verboseLogText.setText(Setting.getSwitch(PlayerSetting.isVerboseLog()));
    }

    @Override
    protected void initEvent() {
        mBinding.decode.setOnClickListener(this::onDecode);
        mBinding.adblock.setOnClickListener(this::setAdblock);
        mBinding.libass.setOnClickListener(this::setLibass);
        mBinding.render.setOnClickListener(this::setRender);
        mBinding.background.setOnClickListener(this::onBackground);
        mBinding.scale.setOnClickListener(this::setScale);
        mBinding.assLimit.setOnClickListener(this::setAssLimit);
        mBinding.verboseLog.setOnClickListener(this::setVerboseLog);
        mBinding.buffer.setOnClickListener(this::onBuffer);
        mBinding.preload.setOnClickListener(this::onPreload);
        mBinding.ua.setOnClickListener(this::onUa);
    }

    private void setRender(View view) {
        int index = (PlayerSetting.getRender() + 1) % render.length;
        PlayerSetting.putRender(index);
        setPlaybackModeText();
    }

    private void setPlaybackModeText() {
        render = ResUtil.getStringArray(R.array.select_render);
        mBinding.renderText.setText(render[PlayerSetting.getRender()]);
    }

    private void onBuffer(View view) {
        BufferDialog.show(this);
    }

    @Override
    public void setBuffer(int times) {
        mBinding.bufferText.setText(String.valueOf(times));
        PlayerSetting.putBuffer(times);
    }

    private void setScale(View view) {
        int next = (PlayerSetting.getScale() + 1) % scale.length;
        PlayerSetting.putScale(next);
        mBinding.scaleText.setText(scale[next]);
    }

    private void setVerboseLog(View view) {
        PlayerSetting.putVerboseLog(!PlayerSetting.isVerboseLog());
        mBinding.verboseLogText.setText(Setting.getSwitch(PlayerSetting.isVerboseLog()));
    }

    private void setAssLimit(View view) {
        int next = (SubtitleSetting.getAssRenderLimit() + 1) % assLimit.length;
        SubtitleSetting.putAssRenderLimit(next);
        mBinding.assLimitText.setText(assLimit[next]);
    }

    private void onBackground(View view) {
        PlayerSetting.putBackground(PlayerSetting.isBackgroundOn() ? 0 : 1);
        mBinding.backgroundText.setText((background = ResUtil.getStringArray(R.array.select_background))[PlayerSetting.getBackground()]);
        mBinding.scaleText.setText((scale = ResUtil.getStringArray(R.array.select_scale))[PlayerSetting.getScale()]);
        mBinding.assLimitText.setText((assLimit = ResUtil.getStringArray(R.array.select_ass_limit))[SubtitleSetting.getAssRenderLimit()]);
        mBinding.verboseLogText.setText(Setting.getSwitch(PlayerSetting.isVerboseLog()));
    }

    private void setAdblock(View view) {
        Setting.putAdblock(!Setting.isAdblock());
        mBinding.adblockText.setText(Setting.getSwitch(Setting.isAdblock()));
    }

    private void setLibass(View view) {
        PlayerSetting.putLibass(!PlayerSetting.isLibass());
        mBinding.libassText.setText(Setting.getSwitch(PlayerSetting.isLibass()));
    }

    private void onPreload(View view) {
        ((HomeActivity) requireActivity()).change(4);
    }

    private void onDecode(View view) {
        ((HomeActivity) requireActivity()).change(5);
    }

    private void onUa(View view) {
        UaDialog.show(this);
    }

    @Override
    public void setUa(String ua) {
        Setting.putUa(ua);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) initView();
    }
}
