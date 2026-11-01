package com.fongmi.android.NewTV.ui.dialog;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.bean.Device;
import com.fongmi.android.NewTV.ui.adapter.DeviceAdapter;
import com.fongmi.android.NewTV.utils.Notify;

public class DeviceConnectDialog extends DeviceDialog implements DeviceAdapter.OnConnectListener {

    public static void show(FragmentActivity activity) {
        if (activity.getSupportFragmentManager().isStateSaved() || activity.getSupportFragmentManager().findFragmentByTag(DeviceConnectDialog.class.getSimpleName()) != null) return;
        new DeviceConnectDialog().show(activity.getSupportFragmentManager(), DeviceConnectDialog.class.getSimpleName());
    }

    @Override
    protected boolean accepts(Device device) {
        return device.isApp();
    }

    @Override
    protected void initView() {
        super.initView();
        binding.title.setText(R.string.home_push);
    }

    @Override
    public void onItemClick(Device device) {
        openBrowser(device.getIp(), "/");
    }

    @Override
    public void onLogsClick(Device device) {
        openBrowser(device.getIp(), "/logs.html");
    }

    @Override
    public boolean onLongClick(Device device) {
        if (device.canRename()) DeviceNameDialog.show(this, device);
        else Notify.show(R.string.device_identity_invalid);
        return true;
    }

    public void refreshNames() {
        if (adapter != null) adapter.notifyDataSetChanged();
    }

}
