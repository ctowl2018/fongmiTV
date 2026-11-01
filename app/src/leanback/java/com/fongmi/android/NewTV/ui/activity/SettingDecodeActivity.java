package com.fongmi.android.NewTV.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.media3.common.DolbyVisionOutputPolicy;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.databinding.ActivitySettingDecodeBinding;
import com.fongmi.android.NewTV.setting.DecodeSetting;
import com.fongmi.android.NewTV.setting.PlayerSetting;
import com.fongmi.android.NewTV.setting.Setting;
import com.fongmi.android.NewTV.ui.base.BaseActivity;
import com.fongmi.android.NewTV.utils.ResUtil;

public class SettingDecodeActivity extends BaseActivity {

    private ActivitySettingDecodeBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingDecodeActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingDecodeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        (PlayerSetting.isExo() ? mBinding.tunnel : mBinding.audioPassThrough).requestFocus();
        setVisible();
        refresh();
    }

    @Override
    protected void initEvent() {
        mBinding.aac.setOnClickListener(this::setAAC);
        mBinding.tunnel.setOnClickListener(this::setTunnel);
        mBinding.dolbyVisionOutput.setOnClickListener(this::setDolbyVisionOutput);
        mBinding.audioPassThrough.setOnClickListener(this::setAudioPassThrough);
    }

    private void setVisible() {
        boolean exo = PlayerSetting.isExo();
        mBinding.aac.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.tunnel.setVisibility(exo ? View.VISIBLE : View.GONE);
    }

    private void refresh() {
        mBinding.aacText.setText(Setting.getSwitch(DecodeSetting.isPreferAAC()));
        mBinding.tunnelText.setText(Setting.getSwitch(DecodeSetting.isTunnel()));
        mBinding.dolbyVisionOutputText.setText(ResUtil.getStringArray(R.array.select_dolby_vision_output)[DecodeSetting.getDolbyVisionOutputPolicy()]);
        mBinding.audioPassThroughText.setText(Setting.getSwitch(DecodeSetting.isAudioPassThrough()));
    }

    private void setTunnel(View view) {
        if (PlayerSetting.isMpv()) return;
        DecodeSetting.putTunnel(!DecodeSetting.isTunnel());
        mBinding.tunnelText.setText(Setting.getSwitch(DecodeSetting.isTunnel()));
    }

    private void setAudioPassThrough(View view) {
        DecodeSetting.putAudioPassThrough(!DecodeSetting.isAudioPassThrough());
        mBinding.audioPassThroughText.setText(Setting.getSwitch(DecodeSetting.isAudioPassThrough()));
    }

    private void setDolbyVisionOutput(View view) {
        int mode = (DecodeSetting.getDolbyVisionOutputPolicy() + 1) % (DolbyVisionOutputPolicy.ASSUME_UNSUPPORTED + 1);
        DecodeSetting.putDolbyVisionOutputPolicy(mode);
        mBinding.dolbyVisionOutputText.setText(ResUtil.getStringArray(R.array.select_dolby_vision_output)[mode]);
    }

    private void setAAC(View view) {
        DecodeSetting.putPreferAAC(!DecodeSetting.isPreferAAC());
        mBinding.aacText.setText(Setting.getSwitch(DecodeSetting.isPreferAAC()));
    }
}
