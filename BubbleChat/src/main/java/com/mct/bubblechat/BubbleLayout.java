package com.mct.bubblechat;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringForce;

import com.mct.touchutils.TouchUtils;
import com.mct.touchutils.TouchUtils.FlingMoveToWallListener;

import java.lang.ref.WeakReference;

class BubbleLayout extends BubbleBaseLayout implements ViewTreeObserver.OnPreDrawListener {

    /**
     * a value representing the default X coordinate
     */
    static final int DEFAULT_X = Integer.MIN_VALUE;

    /**
     * a value representing the default Y coordinate
     */
    static final int DEFAULT_Y = Integer.MIN_VALUE;

    /**
     * Default width size
     */
    static final int DEFAULT_WIDTH = ViewGroup.LayoutParams.WRAP_CONTENT;

    /**
     * Default height size
     */
    static final int DEFAULT_HEIGHT = ViewGroup.LayoutParams.WRAP_CONTENT;

    /**
     * A interface that interact between bubbleLayout & bubbleManager
     */
    private BubbleLayoutListener mLayoutListener;

    /**
     * A interface that inform when bubble removed
     */
    private BubbleRemoveListener mBubbleRemoveListener;

    /**
     * A class which detect touch from user
     */
    private final BubbleTouchListener mBubbleTouchListener;

    /**
     * DisplayMetrics
     */
    private final DisplayMetrics mMetrics;

    /**
     * A Rect representing the limit of the display position (screen edge)
     */
    private final Rect mPositionLimitRect;

    /**
     * Cutout safe inset rect(Same as BubbleLayoutManager's mSafeInsetRect)
     */
    private final Rect mSafeInsetRect;

    /**
     * Margin over edge of screen
     */
    private int mOverMargin;

    /**
     * Initial display X coordinate
     */
    private int mInitX;

    /**
     * Initial display Y coordinate
     */
    private int mInitY;

    /**
     * MoveStiffness
     */
    public float mMoveStiffness;

    /**
     * MoveDampingRatio
     */
    public float mMoveDampingRatio;

    /**
     * Fling mode
     */
    private FlingMoveToWallListener.MoveMode mMode;

    /**
     * status bar's height
     */
    private final int mBaseStatusBarHeight;

    /**
     * status bar's height(landscape)
     */
    private final int mBaseStatusBarRotatedHeight;

    /**
     * Current status bar's height
     */
    private int mStatusBarHeight;

    /**
     * Navigation bar's height(portrait)
     */
    private final int mBaseNavigationBarHeight;

    /**
     * Navigation bar's height
     * Placed bottom on the screen(tablet)
     * Or placed vertically on the screen(phone)
     */
    private final int mBaseNavigationBarRotatedHeight;

    /**
     * Current Navigation bar's vertical size
     */
    private int mNavigationBarVerticalOffset;

    /**
     * Current Navigation bar's horizontal size
     */
    private int mNavigationBarHorizontalOffset;

    /**
     * If true, it's a tablet. If false, it's a phone
     */
    private final boolean mIsTablet;

    private int mRotation;

    public BubbleLayout(Context context) {
        this(context, null);
    }

    public BubbleLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        getWindowManager().getDefaultDisplay().getMetrics(mMetrics = new DisplayMetrics());

        WindowManager.LayoutParams mParams = new WindowManager.LayoutParams();
        mParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        mParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mParams.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        mParams.format = PixelFormat.TRANSLUCENT;
        mParams.gravity = Gravity.START | Gravity.TOP;

        setViewParams(mParams);

        final Resources resources = context.getResources();
        mIsTablet = (resources.getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
        mRotation = getWindowManager().getDefaultDisplay().getRotation();

        mPositionLimitRect = new Rect();
        mSafeInsetRect = new Rect();

        // Get status bar height
        mBaseStatusBarHeight = getSystemUiDimensionPixelSize(resources, "status_bar_height");
        // Check landscape resource id
        @SuppressLint({"DiscouragedApi", "InternalInsetResource"}) final int statusBarLandscapeResId = resources.getIdentifier("status_bar_height_landscape", "dimen", "android");
        if (statusBarLandscapeResId > 0) {
            mBaseStatusBarRotatedHeight = getSystemUiDimensionPixelSize(resources, "status_bar_height_landscape");
        } else {
            mBaseStatusBarRotatedHeight = mBaseStatusBarHeight;
        }

        // Detect NavigationBar
        if (hasSoftNavigationBar()) {
            mBaseNavigationBarHeight = getSystemUiDimensionPixelSize(resources, "navigation_bar_height");
            final String resName = mIsTablet ? "navigation_bar_height_landscape" : "navigation_bar_width";
            mBaseNavigationBarRotatedHeight = getSystemUiDimensionPixelSize(resources, resName);
        } else {
            mBaseNavigationBarHeight = 0;
            mBaseNavigationBarRotatedHeight = 0;
        }

        setOnTouchListener(mBubbleTouchListener = new BubbleTouchListener(this));
        getViewTreeObserver().addOnPreDrawListener(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        playAnimation();
    }

    /**
     * Set the coordinates for the initial drawing.
     */
    @Override
    public boolean onPreDraw() {
        getViewTreeObserver().removeOnPreDrawListener(this);
        // Enter the default value
        // if the initial value is set for the X coordinate (margin is not taken into account)
        if (mInitX == DEFAULT_X) {
            mInitX = 0;
        }
        // Enter the default value if the initial value is set for the Y coordinate
        if (mInitY == DEFAULT_Y) {
            mInitY = 0;
        }

        // Set initial position
        getViewParams().x = mInitX;
        getViewParams().y = mInitY;

        updateLayoutParams();

        return true;
    }

    void getWindowDrawingRect(@NonNull Rect outRect, @NonNull Point position) {
        outRect.set(position.x, position.y, position.x + getWidth(), position.y + getHeight());
    }

    private void playAnimation() {
        if (!isInEditMode()) {
            startScale(this, 0f, 1f);
        }
    }

    private void playAnimationClickDown() {
        if (!isInEditMode()) {
            startScale(this, 1f, 0.8f);
        }
    }

    private void playAnimationClickUp() {
        if (!isInEditMode()) {
            startScale(this, 0.8f, 1f);
        }
    }

    private static void startScale(View target, float from, float to) {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setInterpolator(new OvershootInterpolator());
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(target, "ScaleX", from, to).setDuration(100),
                ObjectAnimator.ofFloat(target, "ScaleY", from, to).setDuration(100)
        );
        animatorSet.start();
    }

    private boolean hasSoftNavigationBar() {
        final DisplayMetrics realDisplayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(realDisplayMetrics);
        return realDisplayMetrics.heightPixels > mMetrics.heightPixels
                || realDisplayMetrics.widthPixels > mMetrics.widthPixels;
    }

    /**
     * Get the System ui dimension(pixel)
     *
     * @param resources {@link Resources}
     * @param resName   dimension resource name
     * @return pixel size
     */
    private static int getSystemUiDimensionPixelSize(@NonNull Resources resources, String resName) {
        int pixelSize = 0;
        @SuppressLint("DiscouragedApi") final int resId = resources.getIdentifier(resName, "dimen", "android");
        if (resId > 0) {
            pixelSize = resources.getDimensionPixelSize(resId);
        }
        return pixelSize;
    }

    /**
     * Determine the display position.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        refreshLimitRect();
    }

    /**
     * Adjust the layout when rotating the screen.
     */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshLimitRect();
    }

    public void onUpdateSystemLayout(boolean isHideStatusBar, boolean isHideNavigationBar, boolean isPortrait, Rect windowRect) {
        // status bar
        updateStatusBarHeight(isHideStatusBar, isPortrait);
        // navigation bar
        updateNavigationBarOffset(isHideNavigationBar, isPortrait, windowRect);
        // refresh
        post(this::refreshLimitRect);
    }

    /**
     * Update height of StatusBar.
     *
     * @param isHideStatusBar If true, the status bar is hidden
     * @param isPortrait      If true, the device orientation is portrait
     */
    private void updateStatusBarHeight(boolean isHideStatusBar, boolean isPortrait) {
        if (isHideStatusBar) {
            // 1.(No Cutout) No StatusBar(=0)
            // 2.(Has Cutout)StatusBar is not included in mMetrics.heightPixels (=0)
            mStatusBarHeight = 0;
            return;
        }

        // Has Cutout
        final boolean hasTopCutout = mSafeInsetRect.top != 0;
        if (hasTopCutout) {
            if (isPortrait) {
                mStatusBarHeight = 0;
            } else {
                mStatusBarHeight = mBaseStatusBarRotatedHeight;
            }
            return;
        }

        // No cutout
        if (isPortrait) {
            mStatusBarHeight = mBaseStatusBarHeight;
        } else {
            mStatusBarHeight = mBaseStatusBarRotatedHeight;
        }
    }

    /**
     * Update offset of NavigationBar.
     *
     * @param isHideNavigationBar If true, the navigation bar is hidden
     * @param isPortrait          If true, the device orientation is portrait
     * @param windowRect          {@link Rect} of system window
     */
    private void updateNavigationBarOffset(boolean isHideNavigationBar, boolean isPortrait, @NonNull Rect windowRect) {
        // auto hide navigation bar(Galaxy S8, S9 and so on.)
        final DisplayMetrics realDisplayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(realDisplayMetrics);

        int currentNavigationBarHeight = realDisplayMetrics.heightPixels - windowRect.bottom;
        int currentNavigationBarWidth = realDisplayMetrics.widthPixels - mMetrics.widthPixels;
        int navigationBarVerticalDiff = mBaseNavigationBarHeight - currentNavigationBarHeight;
        final boolean hasSoftNavigationBar = hasSoftNavigationBar();

        if (!isHideNavigationBar) {
            // auto hide navigation bar
            // Guess based on inconsistencies with other devices
            // 1.A navigation bar built into the device (mBaseNavigationBarHeight == 0) does not cause a difference in height depending on the system state
            // 2.A navigation bar built into the device (!hasSoftNavigationBar) intentionally sets Base to 0, so it is inconsistent
            if (navigationBarVerticalDiff != 0 && mBaseNavigationBarHeight == 0 ||
                    !hasSoftNavigationBar && mBaseNavigationBarHeight != 0) {
                if (hasSoftNavigationBar) {
                    // 1.auto hide mode -> show mode
                    // 2.show mode -> auto hide mode -> home
                    mNavigationBarVerticalOffset = 0;
                } else {
                    // show mode -> home
                    mNavigationBarVerticalOffset = -currentNavigationBarHeight;
                }
            } else {
                // normal device
                mNavigationBarVerticalOffset = 0;
            }
            mNavigationBarHorizontalOffset = 0;
            return;
        }

        // If the portrait, is displayed at the bottom of the screen
        if (isPortrait) {
            // auto hide navigation bar
            if (!hasSoftNavigationBar && mBaseNavigationBarHeight != 0) {
                mNavigationBarVerticalOffset = 0;
            } else {
                mNavigationBarVerticalOffset = mBaseNavigationBarHeight;
            }
            mNavigationBarHorizontalOffset = 0;
            return;
        }

        // If it is a Tablet, it will appear at the bottom of the screen.
        // If it is Phone, it will appear on the side of the screen
        if (mIsTablet) {
            mNavigationBarVerticalOffset = mBaseNavigationBarRotatedHeight;
            mNavigationBarHorizontalOffset = 0;
        } else {
            mNavigationBarVerticalOffset = 0;
            // auto hide navigation bar
            // Guess based on inconsistencies with other devices
            // 1. A navigation bar built into the device(!hasSoftNavigationBar)
            //      is inconsistent because Base is intentionally set to 0
            if (!hasSoftNavigationBar && mBaseNavigationBarRotatedHeight != 0) {
                mNavigationBarHorizontalOffset = 0;
            } else if (hasSoftNavigationBar && mBaseNavigationBarRotatedHeight == 0) {
                // 2.Inconsistent because for soft nav bars Base is set
                mNavigationBarHorizontalOffset = currentNavigationBarWidth;
            } else {
                mNavigationBarHorizontalOffset = mBaseNavigationBarRotatedHeight;
            }
        }
    }

    /**
     * Update the PositionLimitRect and MoveLimitRect according to the screen size change.
     */
    private void refreshLimitRect() {
        // Save previous screen coordinates
        final int oldPositionX = getViewParams().x;
        final int oldPositionY = getViewParams().y;

        @TouchUtils.Wall
        int wall = oldPositionX < mPositionLimitRect.centerX() ? TouchUtils.LEFT : TouchUtils.RIGHT;
        // old percent position of y
        float percentY = (float) oldPositionY / mPositionLimitRect.height();

        // Switch to new coordinate information
        getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        final int newScreenWidth = mMetrics.widthPixels;
        final int newScreenHeight = mMetrics.heightPixels;

        mPositionLimitRect.set(-mOverMargin, 0,
                newScreenWidth + mOverMargin + mNavigationBarHorizontalOffset,
                newScreenHeight - mStatusBarHeight + mNavigationBarVerticalOffset);

        int newRotation = getWindowManager().getDefaultDisplay().getRotation();
        float newPositionX = wall == TouchUtils.LEFT ? mPositionLimitRect.left : mPositionLimitRect.right - getWidth();
        float newPositionY = newRotation != mRotation ? mPositionLimitRect.height() * percentY : oldPositionY;

        if (mBubbleTouchListener != null) {
            mBubbleTouchListener.stopAnimation();
            mBubbleTouchListener.refresh(newPositionX, newPositionY);
        }

        mRotation = newRotation;
    }

    /**
     * Margin over the edge of the screen.
     *
     * @param margin margin
     */
    void setOverMargin(int margin) {
        mOverMargin = margin;
    }

    void setInitCoords(int initX, int initY) {
        mInitX = initX;
        mInitY = initY;
    }

    void setMoveStiffness(float moveStiffness) {
        mMoveStiffness = moveStiffness;
    }

    void setMoveDampingRatio(float moveDampingRatio) {
        mMoveDampingRatio = moveDampingRatio;
    }

    void setFlingMode(FlingMoveToWallListener.MoveMode mode) {
        mMode = mode;
    }


    void setLayoutListener(BubbleLayoutListener layoutCoordinator) {
        this.mLayoutListener = layoutCoordinator;
    }

    void setOnBubbleRemoveListener(BubbleRemoveListener listener) {
        mBubbleRemoveListener = listener;
    }

    void notifyBubbleRemoved() {
        if (mBubbleRemoveListener != null) {
            mBubbleRemoveListener.onRemoved();
        }
    }

    /**
     * Set the cutout's safe inset area
     *
     * @param safeInsetRect {@link BubblesManager#setSafeInsetRect(Rect)}
     */
    void setSafeInsetRect(Rect safeInsetRect) {
        mSafeInsetRect.set(safeInsetRect);
    }

    void setUpdateTarget(@NonNull BubbleLayout bubbleLayout) {
        mPositionLimitRect.set(bubbleLayout.mPositionLimitRect);
        mSafeInsetRect.set(bubbleLayout.mSafeInsetRect);
        mStatusBarHeight = bubbleLayout.mStatusBarHeight;
        mNavigationBarVerticalOffset = bubbleLayout.mNavigationBarVerticalOffset;
        mNavigationBarHorizontalOffset = bubbleLayout.mNavigationBarHorizontalOffset;
        mRotation = bubbleLayout.mRotation;
        mBubbleTouchListener.setUpdateTarget(bubbleLayout);
    }

    private static class BubbleTouchListener extends FlingMoveToWallListener {

        WeakReference<BubbleLayout> bubbleLayout;
        WeakReference<BubbleLayout> mUpdateBubble;
        FloatPropertyCompat<View> propertyCompatX, propertyCompatY;
        Point movePosition;
        boolean isInTrash;

        DynamicAnimation.OnAnimationUpdateListener updateListenerX;
        DynamicAnimation.OnAnimationUpdateListener updateListenerY;

        public BubbleTouchListener(BubbleLayout bubbleLayout) {
            this.bubbleLayout = new WeakReference<>(bubbleLayout);
            propertyCompatX = WINDOW_X.getPropertyCompat();
            propertyCompatY = WINDOW_Y.getPropertyCompat();
            movePosition = new Point();
            init(bubbleLayout);
        }

        public void refresh(float newPositionX, float newPositionY) {
            BubbleLayout v = bubbleLayout.get();
            setArea(v, initArea(v));
            Rect animArea = initAnimArea(v);
            getSpringX().setMinValue(animArea.left).setMaxValue(animArea.right);
            getSpringY().setMinValue(animArea.top).setMaxValue(animArea.bottom);
            Rect moveArea = getMoveArea();
            v.getViewParams().x = (int) TouchUtils.coerceIn(newPositionX, moveArea.left, moveArea.right);
            v.getViewParams().y = (int) TouchUtils.coerceIn(newPositionY, moveArea.top, moveArea.bottom);
            v.updateLayoutParams();
        }

        public void setUpdateTarget(BubbleLayout bubbleLayout) {
            clearUpdateTarget();
            mUpdateBubble = new WeakReference<>(bubbleLayout);
            addUpdateListener();
        }

        void addUpdateListener() {
            final int delay = 5;
            updateListenerX = (animation, value, velocity)
                    -> mUpdateBubble.get().postDelayed(()
                    -> mUpdateBubble.get().mBubbleTouchListener.animateToX(value), delay);
            updateListenerY = (animation, value, velocity)
                    -> mUpdateBubble.get().postDelayed(()
                    -> mUpdateBubble.get().mBubbleTouchListener.animateToY(value), delay);
            getSpringX().addUpdateListener(updateListenerX);
            getSpringY().addUpdateListener(updateListenerY);
        }

        void clearUpdateTarget() {
            getSpringX().removeUpdateListener(updateListenerX);
            getSpringY().removeUpdateListener(updateListenerY);
            updateListenerX = updateListenerY = null;
            if (mUpdateBubble != null && mUpdateBubble.get() != null) {
                mUpdateBubble.clear();
            }
        }

        void animateToX(float value) {
            getSpringX().animateToFinalPosition(value);
        }

        void animateToY(float value) {
            getSpringY().animateToFinalPosition(value);
        }

        @NonNull
        @Override
        protected Rect initArea(View view) {
            return new Rect(bubbleLayout.get().mPositionLimitRect);
        }

        @Override
        protected FloatPropertyCompat<View> getPropX() {
            return propertyCompatX;
        }

        @Override
        protected FloatPropertyCompat<View> getPropY() {
            return propertyCompatY;
        }

        @Override
        protected float getMoveStiffness() {
            return bubbleLayout.get().mMoveStiffness;
        }

        @Override
        protected float getMoveDampingRatio() {
            return bubbleLayout.get().mMoveDampingRatio;
        }

        @NonNull
        @Override
        protected MoveMode getMoveMode() {
            return bubbleLayout.get().mMode;
        }

        @Override
        protected boolean onActionDown(@NonNull View view, @NonNull MotionEvent event) {
            bubbleLayout.get().playAnimationClickDown();
            return super.onActionDown(view, event);
        }

        @Override
        protected boolean onActionMove(@NonNull View view, @NonNull MotionEvent event) {
            if (isTouching()) {
                int x = (int) (event.getRawX() + getDownX());
                int y = (int) (event.getRawY() + getDownY());
                movePosition.set(x, y);
                if (bubbleLayout.get().mLayoutListener.onBubbleMove(movePosition)) {
                    if (!isInTrash) {
                        isInTrash = true;
                        float damping = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY;
                        float stiffness = SpringForce.STIFFNESS_MEDIUM;
                        moveToTrash(movePosition, damping, stiffness);
                    }
                    return true;
                } else {
                    if (isInTrash) {
                        isInTrash = false;
                        resetForce(false);
                    }
                    return super.onActionMove(view, event);
                }
            }
            return false;
        }

        @Override
        protected void handleFling(View view, Point predictPosition) {
            if (predictPosition != null && bubbleLayout.get().mLayoutListener.onBubbleFling(predictPosition)) {
                DynamicAnimation.OnAnimationEndListener endListener = new DynamicAnimation.OnAnimationEndListener() {
                    @Override
                    public void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value, float velocity) {
                        if (!canceled && !getSpringX().isRunning() && !getSpringY().isRunning()) {
                            bubbleLayout.get().playAnimationClickUp();
                            bubbleLayout.get().mLayoutListener.onBubbleRelease();
                        }
                        if (!getSpringX().isRunning()) getSpringX().removeEndListener(this);
                        if (!getSpringY().isRunning()) getSpringY().removeEndListener(this);
                    }
                };
                getSpringX().addEndListener(endListener);
                getSpringY().addEndListener(endListener);
                float damping = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY;
                float stiffness = 300;
                moveToTrash(predictPosition, damping, stiffness);
            } else {
                bubbleLayout.get().playAnimationClickUp();
                bubbleLayout.get().mLayoutListener.onBubbleRelease();
                super.handleFling(view, predictPosition);
            }
        }

        void moveToTrash(@NonNull Point position, float damping, float stiffness) {
            position.x -= bubbleLayout.get().mNavigationBarHorizontalOffset / 2;
            moveTo(position, damping, stiffness);
        }

        void moveTo(@NonNull Point position, float damping, float stiffness) {
            getSpringX().getSpring().setDampingRatio(damping).setStiffness(stiffness);
            getSpringY().getSpring().setDampingRatio(damping).setStiffness(stiffness);
            getSpringX().animateToFinalPosition(position.x);
            getSpringY().animateToFinalPosition(position.y);
        }

        public void stopAnimation() {
            clearAnimation();
        }

    }

}
