package com.mct.bubblechat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.DisplayCutout;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.SpringForce;

import com.mct.bubblechat.BubbleTrash.AnimationState;
import com.mct.touchutils.TouchUtils.FlingMoveToWallListener;

import java.util.ArrayList;
import java.util.List;

public class BubblesManager implements BubbleLayoutListener, TrashViewListener, ScreenChangedListener {

    /**
     * WindowManager
     */
    private final WindowManager mWindowManager;


    /**
     * A list of BubbleViews attached to a Window
     */
    private final List<BubbleLayout> mBubbles;

    /**
     * Bubble target on top
     */
    private BubbleLayout mTargetView;

    /**
     * A View that monitors the full screen.
     */
    private final FullscreenObserverView mObserverView;

    /**
     * The View that removes the Bubble.
     */
    private final BubbleTrash mTrashView;
    /**
     * BubbleLayout's collision rectangle
     */
    private final Rect mBubbleViewRect;

    /**
     * TrashView's hitbox rectangle
     */
    private final Rect mTrashViewRect;

    /**
     * Cutout safe inset rect
     */
    private final Rect mSafeInsetRect;

    /**
     * State when bubble move. True if bubble intersect trash
     */
    private boolean isIntersect;

    public BubblesManager(@NonNull Context context) {
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mBubbles = new ArrayList<>();
        mObserverView = new FullscreenObserverView(context, this);
        mTrashView = new BubbleTrash(context);
        mBubbleViewRect = new Rect();
        mTrashViewRect = new Rect();
        mSafeInsetRect = new Rect();
        setFixedTrashIconImage(R.drawable.ic_trash_fixed);
        setActionTrashIconImage(R.drawable.ic_trash_action);
    }

    /**
     * Sets the image for the fixed delete icon.
     *
     * @param resId drawable ID
     */
    public void setFixedTrashIconImage(@DrawableRes int resId) {
        mTrashView.setFixedTrashIconImage(resId);
    }

    /**
     * Sets the image of the action delete icon.
     *
     * @param resId drawable ID
     */
    public void setActionTrashIconImage(@DrawableRes int resId) {
        mTrashView.setActionTrashIconImage(resId);
    }

    /**
     * Set fixed delete icon.
     *
     * @param drawable Drawable
     */
    public void setFixedTrashIconImage(Drawable drawable) {
        mTrashView.setFixedTrashIconImage(drawable);
    }

    /**
     * Sets the action delete icon.
     *
     * @param drawable Drawable
     */
    public void setActionTrashIconImage(Drawable drawable) {
        mTrashView.setActionTrashIconImage(drawable);
    }

    public void setBubbleVisibility(int visibility) {
        for (BubbleLayout bubble : mBubbles) {
            bubble.setVisibility(visibility);
        }
    }

    public boolean isEmpty() {
        return mBubbles.isEmpty();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void addBubble(@NonNull View view, @NonNull Options options) {
        final BubbleLayout bubble = new BubbleLayout(view.getContext());
        bubble.setLayoutListener(this);
        bubble.setOverMargin(options.overMargin);
        bubble.setInitCoords(options.initX, options.initY);
        bubble.setMoveStiffness(options.moveStiffness);
        bubble.setMoveDampingRatio(options.moveDampingRatio);
        bubble.setFlingMode(options.mode);
        bubble.setOnBubbleRemoveListener(options.bubbleRemoveListener);
        bubble.setOnClickListener(options.onClickListener);
        bubble.setSafeInsetRect(mSafeInsetRect);
        // set BubbleLayout size
        bubble.addView(view, options.floatingViewWidth, options.floatingViewHeight);
        bubble.attachToWindow();

        if (mBubbles.isEmpty()) {
            mWindowManager.addView(mObserverView, mObserverView.getWindowLayoutParams());
        } else {
            bubble.setUpdateTarget(mTargetView);
            mTargetView.setLayoutListener(null);
            mTargetView.setOnTouchListener(null);
        }
        if (!mBubbles.contains(bubble)) {
            mBubbles.add(bubble);
        }
        mTargetView = bubble;
        mTrashView.setTrashViewListener(this);
        mTrashView.detachFromWindow();
        mTrashView.attachToWindow();
    }

    /**
     * Set the DisplayCutout's safe area
     * Note:You must set the Cutout obtained on portrait orientation.
     *
     * @param safeInsetRect DisplayCutout#getSafeInsetXXX
     */
    public void setSafeInsetRect(Rect safeInsetRect) {
        if (safeInsetRect == null) {
            mSafeInsetRect.setEmpty();
        } else {
            mSafeInsetRect.set(safeInsetRect);
        }

        final int size = mBubbles.size();
        if (size == 0) {
            return;
        }
        // update floating view
        for (int i = 0; i < size; i++) {
            final BubbleLayout bubble = mBubbles.get(i);
            bubble.setSafeInsetRect(mSafeInsetRect);
        }
        // dirty hack
        mObserverView.onGlobalLayout();
    }

    public void dispose() {
        mWindowManager.removeViewImmediate(mObserverView);
        mTrashView.detachFromWindow();
        for (BubbleLayout bubble : mBubbles) {
            removeBubble(bubble);
        }
        mBubbles.clear();
    }

    /**
     * Find the safe area of DisplayCutout.
     *
     * @param activity {@link Activity} (Portrait and `windowLayoutInDisplayCutoutMode` != never)
     * @return Safe cutout insets.
     */
    @NonNull
    public static Rect findCutoutSafeArea(@NonNull Activity activity) {
        final Rect safeInsetRect = new Rect();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return safeInsetRect;
        }

        // Fix: getDisplayCutout() on a null object reference (issue #110)
        final WindowInsets windowInsets = activity.getWindow().getDecorView().getRootWindowInsets();
        if (windowInsets == null) {
            return safeInsetRect;
        }

        // set safeInsetRect
        final DisplayCutout displayCutout = windowInsets.getDisplayCutout();
        if (displayCutout != null) {
            safeInsetRect.set(
                    displayCutout.getSafeInsetLeft(),
                    displayCutout.getSafeInsetTop(),
                    displayCutout.getSafeInsetRight(),
                    displayCutout.getSafeInsetBottom()
            );
        }

        return safeInsetRect;
    }

    /* --------------------------------- BubbleLayoutListener ----------------------------------- */
    @Override
    public boolean onBubbleMove(Point position) {
        if (mTrashView.isTrashReady()) {
            boolean isIntersect = this.isIntersect;
            boolean isIntersecting = this.isIntersect = isIntersectWithTrash(position);
            if (isIntersecting && !isIntersect) {
                Point trashPosition = mTrashView.getTrashPosition();
                position.set(
                        trashPosition.x - mTargetView.getWidth() / 2,
                        trashPosition.y - mTargetView.getHeight() / 2);
                mTrashView.vibrate();
                mTrashView.setScaleTrashIcon(true);
            }
            if (!isIntersecting && isIntersect) {
                mTrashView.setScaleTrashIcon(false);
            }
        }
        notifyTrash(MotionEvent.ACTION_MOVE);
        return isIntersect;
    }

    @Override
    public boolean onBubbleFling(Point predictPosition) {
        if (this.isIntersect) {
            return false;
        }
        this.isIntersect = isIntersectWithTrash(predictPosition);
        if (isIntersect) {
            Point trashPosition = mTrashView.getTrashPosition();
            predictPosition.set(
                    trashPosition.x - mTargetView.getWidth() / 2,
                    trashPosition.y - mTargetView.getHeight() / 2);
            mTrashView.vibrate();
            mTrashView.setScaleTrashIcon(true);
            notifyTrash(MotionEvent.ACTION_MOVE, predictPosition.x, predictPosition.y);
        }
        return this.isIntersect;
    }

    @Override
    public void onBubbleRelease() {
        if (isIntersect) {
            isIntersect = false;
            mBubbles.remove(mTargetView);
            removeBubble(mTargetView);
        }
        mTrashView.setScaleTrashIcon(false);
        notifyTrash(MotionEvent.ACTION_UP);
    }

    /* -------------------------------- TrashViewListener --------------------------------------- */

    @Override
    public void onUpdateActionTrashIcon() {
        mTrashView.updateActionTrashIcon(mTargetView.getMeasuredWidth(), mTargetView.getMeasuredHeight());
    }

    @Override
    public void onTrashAnimationStarted(@AnimationState int animationCode) {
    }

    @Override
    public void onTrashAnimationEnd(@AnimationState int animationCode) {
    }

    /* -------------------------------- ScreenChangedListener ----------------------------------- */

    @Override
    public void onScreenChanged(@NonNull Rect windowRect, int visibility) {
        // detect status bar
        final boolean isHideStatusBar = windowRect.top == 0;
        // detect navigation bar
        final boolean isHideNavigationBar;
        if (visibility == FullscreenObserverView.NO_LAST_VISIBILITY) {
            // At the first it can not get the correct value, so do special processing
            DisplayMetrics mDisplayMetrics = new DisplayMetrics();
            mWindowManager.getDefaultDisplay().getRealMetrics(mDisplayMetrics);
            isHideNavigationBar = windowRect.width() - mDisplayMetrics.widthPixels == 0 && windowRect.bottom - mDisplayMetrics.heightPixels == 0;
        } else {
            isHideNavigationBar = (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }
        final boolean isPortrait = Resources.getSystem().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        // update BubbleLayout layout
        mTargetView.onUpdateSystemLayout(isHideStatusBar, isHideNavigationBar, isPortrait, windowRect);

        for (BubbleLayout bubble : mBubbles) {
            if (bubble == mTargetView) {
                continue;
            }
            bubble.postDelayed(() -> {
                bubble.getViewParams().x = mTargetView.getViewParams().x;
                bubble.getViewParams().y = mTargetView.getViewParams().y;
                bubble.updateLayoutParams();
            }, 50);
        }
    }

    /* ----------------------------------- private area ----------------------------------------- */

    private boolean isIntersectWithTrash(Point position) {
        // If disabled, overlap judgment is not performed.
        if (mTrashView.isTrashDisabled()) {
            return false;
        }
        // INFO:TrashView and BubbleLayout should have the same Gravity
        mTrashView.getWindowDrawingRect(mTrashViewRect);
        mTargetView.getWindowDrawingRect(mBubbleViewRect, position);
        return Rect.intersects(mTrashViewRect, mBubbleViewRect);
    }

    private void removeBubble(@NonNull BubbleLayout bubble) {
        bubble.detachFromWindow();
        bubble.notifyBubbleRemoved();
    }

    private void notifyTrash(int action) {
        WindowManager.LayoutParams params = mTargetView.getViewParams();
        notifyTrash(action, params.x, params.y);
    }

    private void notifyTrash(int action, int x, int y) {
        mTrashView.onTouchBubbleLayout(action, x, y);
    }

    /* -------------------------------------- option -------------------------------------------- */

    /**
     * A class that represents options when pasting a BubbleLayout.
     */
    public static class Options {

        /**
         * Margin outside the screen(px)
         */
        public int overMargin;

        /**
         * The X coordinate of the BubbleLayout with the origin at the top left of the screen
         */
        public int initX;

        /**
         * The Y coordinate of the BubbleLayout with the origin at the top left of the screen
         */
        public int initY;

        /**
         * Width of BubbleLayout(px)
         */
        public int floatingViewWidth;

        /**
         * Height of BubbleLayout(px)
         */
        public int floatingViewHeight;

        /**
         * MoveStiffness
         */
        public float moveStiffness;

        /**
         * MoveDampingRatio
         */
        public float moveDampingRatio;

        /**
         * Fling mode
         */
        public FlingMoveToWallListener.MoveMode mode;

        /**
         * Bubble Remove Listener
         */
        public BubbleRemoveListener bubbleRemoveListener;

        /**
         * Click Listener
         */
        public View.OnClickListener onClickListener;

        /**
         * Sets the default value of an option
         */
        public Options() {
            overMargin = 0;
            initX = BubbleLayout.DEFAULT_X;
            initY = BubbleLayout.DEFAULT_Y;
            floatingViewWidth = BubbleLayout.DEFAULT_WIDTH;
            floatingViewHeight = BubbleLayout.DEFAULT_HEIGHT;
            moveStiffness = SpringForce.STIFFNESS_MEDIUM;
            moveDampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY;
            mode = FlingMoveToWallListener.MoveMode.Vertical;
        }

    }
}
