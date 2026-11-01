package com.fongmi.android.NewTV.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.NewTV.Product;
import com.fongmi.android.NewTV.bean.Style;
import com.fongmi.android.NewTV.bean.Vod;
import com.fongmi.android.NewTV.databinding.AdapterVodListBinding;
import com.fongmi.android.NewTV.databinding.AdapterVodOvalBinding;
import com.fongmi.android.NewTV.databinding.AdapterVodRectBinding;
import com.fongmi.android.NewTV.ui.base.BaseVodHolder;
import com.fongmi.android.NewTV.ui.base.ViewType;
import com.fongmi.android.NewTV.ui.holder.VodListHolder;
import com.fongmi.android.NewTV.ui.holder.VodOvalHolder;
import com.fongmi.android.NewTV.ui.holder.VodRectHolder;

import java.util.Arrays;

public class VodPresenter extends Presenter {

    private final OnClickListener listener;
    private final Style style;
    private final int[] size;

    public VodPresenter(OnClickListener listener) {
        this(listener, Style.rect());
    }

    public VodPresenter(OnClickListener listener, Style style) {
        this.listener = listener;
        this.style = style;
        this.size = Product.getSpec(style);
    }

    public boolean matches(Style style) {
        return this.style.equals(style) && Arrays.equals(size, Product.getSpec(style));
    }

    public interface OnClickListener {

        void onItemClick(Vod item);

        boolean onLongClick(Vod item);
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return switch (style.getViewType()) {
            case ViewType.LIST -> new VodListHolder(AdapterVodListBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener);
            case ViewType.OVAL -> new VodOvalHolder(AdapterVodOvalBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener).size(size);
            default -> new VodRectHolder(AdapterVodRectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener).size(size);
        };
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        ((BaseVodHolder) viewHolder).initView((Vod) object);
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
        ((BaseVodHolder) viewHolder).unbind();
    }
}
