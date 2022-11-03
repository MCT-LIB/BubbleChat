package com.mct.bubblechat.test;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mct.bubblechat.BubblesManager;

public class ChatHeadService extends Service {

    public static final String EXTRA_CUTOUT_SAFE_AREA = "cutout_safe_area";
    private static final int BUBBLE_OVER_MARGIN = dp2px(8);

    private boolean isInit;
    private BubblesManager bubblesManager;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isOverlayGranted(this)) {
            if (!isInit) {
                isInit = true;
                bubblesManager = new BubblesManager(this);
                bubblesManager.setSafeInsetRect((Rect) intent.getParcelableExtra(EXTRA_CUTOUT_SAFE_AREA));

                BubblesManager.Options options = new BubblesManager.Options();
                options.overMargin = BUBBLE_OVER_MARGIN;
                options.initX = -BUBBLE_OVER_MARGIN;
                options.initY = 150;
                options.floatingViewWidth = dp2px(80);
                options.floatingViewHeight = dp2px(80);
                options.onClickListener = v -> Log.e("ddd", "onStartCommand: Clicked");

                View mView = getBubbleView(this);
                bubblesManager.addBubble(mView, options);
                options.bubbleRemoveListener = this::stopSelf;
                mView = getBubbleView(this);
                bubblesManager.addBubble(mView, options);
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isInit = false;
        if (bubblesManager != null) {
            bubblesManager.dispose();
            bubblesManager = null;
        }
    }

    @NonNull
    private ImageView getBubbleView(Context context) {
        int padding = dp2px(8);
        ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.ic_avatar);
        imageView.setPadding(padding, padding, padding, padding);
        return imageView;
    }

    static int dp2px(float dpValue) {
        final float scale = Resources.getSystem().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    static boolean isOverlayGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }
}
