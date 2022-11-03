package com.mct.bubblechat;

import static android.view.WindowManager.LayoutParams;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/**
 * A View that monitors the full screen.
 * http://stackoverflow.com/questions/18551135/receiving-hidden-status-bar-entering-a-full-screen-activity-event-on-a-service/19201933#19201933
 */
@SuppressLint("ViewConstructor")
class FullscreenObserverView extends View implements ViewTreeObserver.OnGlobalLayoutListener, View.OnSystemUiVisibilityChangeListener {

    /**
     * Constant that mLastUiVisibility does not exist.
     */
    static final int NO_LAST_VISIBILITY = -1;

    /**
     * WindowManager.LayoutParams
     */
    private final LayoutParams mParams;

    /**
     * ScreenListener
     */
    private final ScreenChangedListener mScreenChangedListener;

    /**
     * last display state（Keep it yourself because onSystemUiVisibilityChange may not come）
     * ※if you don't come：ImmersiveMode → touch the status bar → status bar disappears
     */
    private int mLastUiVisibility;

    /**
     * Window's Rect
     */
    private final Rect mWindowRect;

    /**
     * constructor
     */
    FullscreenObserverView(Context context, ScreenChangedListener listener) {
        super(context);

        // set listener
        mScreenChangedListener = listener;

        // Prepare a transparent View with a width of 1 and maximum height to detect layout changes
        mParams = new LayoutParams();
        mParams.width = 1;
        mParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        mParams.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? LayoutParams.TYPE_APPLICATION_OVERLAY
                : LayoutParams.TYPE_PHONE;
        mParams.flags = LayoutParams.FLAG_NOT_FOCUSABLE |
                LayoutParams.FLAG_NOT_TOUCHABLE |
                LayoutParams.FLAG_NOT_TOUCH_MODAL;
        mParams.format = PixelFormat.TRANSLUCENT;

        mWindowRect = new Rect();
        mLastUiVisibility = NO_LAST_VISIBILITY;

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(this);
        setOnSystemUiVisibilityChangeListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDetachedFromWindow() {
        // Remove layout change notification
        getViewTreeObserver().removeOnGlobalLayoutListener(this);
        setOnSystemUiVisibilityChangeListener(null);
        super.onDetachedFromWindow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onGlobalLayout() {
        // ViewGet size (full screen)
        if (mScreenChangedListener != null) {
            getWindowVisibleDisplayFrame(mWindowRect);
            mScreenChangedListener.onScreenChanged(mWindowRect, mLastUiVisibility);
        }
    }

    /**
     * Apps that do things to the navigation bar(If the onGlobalLayout event does not fire)is used in
     * (Nexus5 camera app, etc.)
     */
    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        mLastUiVisibility = visibility;
        // Display/hide switching in response to changes in the navigation bar
        if (mScreenChangedListener != null) {
            getWindowVisibleDisplayFrame(mWindowRect);
            mScreenChangedListener.onScreenChanged(mWindowRect, visibility);
        }
    }

    /**
     * WindowManager.LayoutParams
     *
     * @return WindowManager.LayoutParams
     */
    LayoutParams getWindowLayoutParams() {
        return mParams;
    }

}