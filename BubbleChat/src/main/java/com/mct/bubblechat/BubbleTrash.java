package com.mct.bubblechat;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

/**
 * BubbleLayout View for erasing .
 */
@SuppressWarnings("SameParameterValue")
class BubbleTrash extends BubbleBaseLayout implements ViewTreeObserver.OnPreDrawListener {

    /**
     * background height (dp)
     */
    private static final int BACKGROUND_HEIGHT = 164;

    /**
     * Horizontal area to capture target(dp)
     */
    private static final float TARGET_CAPTURE_HORIZONTAL_REGION = 64.0f;

    /**
     * vertical area to capture target(dp)
     */
    private static final float TARGET_CAPTURE_VERTICAL_REGION = 32.0f;

    /**
     * Animation time for enlargement/reduction of delete icon
     */
    private static final long TRASH_ICON_SCALE_DURATION_MILLIS = 200L;

    /**
     * A constant representing the no-animation state
     */
    static final int ANIMATION_NONE = 0;
    /**
     * Constants representing animations that display backgrounds, delete icons, etc.<br/>
     * BubbleLayout Also includes tracking.
     */
    static final int ANIMATION_OPEN = 1;
    /**
     * A constant representing an animation that erases the background, delete icon, etc.
     */
    static final int ANIMATION_CLOSE = 2;
    /**
     * A constant that indicates to immediately erase the background, delete icon, etc.
     */
    static final int ANIMATION_FORCE_CLOSE = 3;

    /**
     * Animation State
     */
    @IntDef({ANIMATION_NONE, ANIMATION_OPEN, ANIMATION_CLOSE, ANIMATION_FORCE_CLOSE})
    @Retention(RetentionPolicy.SOURCE)
    @interface AnimationState {
    }

    /**
     * Long press judgment time
     */
    private static final int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

    /**
     * Vibrator
     */
    private final Vibrator mVibrator;

    /**
     * DisplayMetrics
     */
    private final DisplayMetrics mMetrics;

    /**
     * delete icon
     */
    private final FrameLayout mTrashIconRootView;

    /**
     * Fixed delete icon
     */
    private final ImageView mFixedTrashIconView;

    /**
     * Delete icon that behaves according to overlap
     */
    private final ImageView mActionTrashIconView;

    /**
     * width of ActionTrashIcon
     */
    private int mActionTrashIconBaseWidth;

    /**
     * height of ActionTrashIcon
     */
    private int mActionTrashIconBaseHeight;

    /**
     * maximum magnification of ActionTrashIcon
     */
    private float mActionTrashIconMaxScale;

    /**
     * background View
     */
    private final FrameLayout mBackgroundView;

    /**
     * Animation when entering the frame of the delete icon (enlarge)
     */
    private ObjectAnimator mEnterScaleAnimator;

    /**
     * Animation when going out of the frame of the delete icon (shrinking)
     */
    private ObjectAnimator mExitScaleAnimator;

    /**
     * the handler that does the animation
     */
    private final AnimationHandler mAnimationHandler;

    /**
     * TrashViewListener
     */
    private TrashViewListener mTrashViewListener;

    /**
     * trash Position
     */
    private final Point trashPosition;

    /**
     * View valid / invalid flag (not displayed if invalid)
     */
    private boolean mIsEnabled;

    /**
     * constructor
     *
     * @param context Context
     */
    BubbleTrash(Context context) {
        super(context);
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        mAnimationHandler = new AnimationHandler(Looper.getMainLooper(), this);
        trashPosition = new Point();
        setTrashEnabled(true);

        WindowManager.LayoutParams mParams = new WindowManager.LayoutParams();
        mParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        mParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        mParams.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        mParams.format = PixelFormat.TRANSLUCENT;
        mParams.gravity = Gravity.START | Gravity.TOP;
        mParams.alpha = 0.8f;

        setViewParams(mParams);
        // Various view settings
        // TrashView View that can be pasted directly to
        // (If you don't go through this view,
        // the layout of the deleted view and the background view will collapse for some reason)
        /*
         * root View（Including background, delete icon View）
         */
        setClipChildren(false);
        // Root view of delete icon
        mTrashIconRootView = new FrameLayout(context);
        mTrashIconRootView.setClipChildren(false);
        mFixedTrashIconView = new ImageView(context);
        mActionTrashIconView = new ImageView(context);
        // BackgroundView
        mBackgroundView = new FrameLayout(context);
        mBackgroundView.setAlpha(0.0f);
        final GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x50000000});
        mBackgroundView.setBackground(gradientDrawable);
        // Paste background view
        final LayoutParams backgroundParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (BACKGROUND_HEIGHT * mMetrics.density));
        backgroundParams.gravity = Gravity.BOTTOM;
        addView(mBackgroundView, backgroundParams);
        // Paste action icon
        final LayoutParams actionTrashIconParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionTrashIconParams.gravity = Gravity.CENTER;
        mTrashIconRootView.addView(mActionTrashIconView, actionTrashIconParams);

        // Paste a fixed icon
        final LayoutParams fixedTrashIconParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fixedTrashIconParams.gravity = Gravity.CENTER;
        mTrashIconRootView.addView(mFixedTrashIconView, fixedTrashIconParams);
        // Paste delete icon
        final LayoutParams trashIconParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trashIconParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        addView(mTrashIconRootView, trashIconParams);

        // For initial drawing processing
        getViewTreeObserver().addOnPreDrawListener(this);
    }

    /**
     * Determine the display position.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        updateViewLayout();
    }

    /**
     * Adjust the layout when rotating the screen.
     */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateViewLayout();
    }

    /**
     * Set the coordinates for the initial drawing.<br/>
     * Because there is a phenomenon that
     * the delete icon is displayed for a moment when it is displayed for the first time.
     */
    @Override
    public boolean onPreDraw() {
        getViewTreeObserver().removeOnPreDrawListener(this);
        mTrashIconRootView.setTranslationY(mTrashIconRootView.getMeasuredHeight());
        return true;
    }

    /**
     * initialize ActionTrashIcon
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mTrashViewListener.onUpdateActionTrashIcon();
    }

    /**
     * Determine your position from the screen size.
     */
    private void updateViewLayout() {
        post(() -> {
            trashPosition.x = mBackgroundView.getWidth() / 2;
            trashPosition.y = mTrashIconRootView.getTop() - mFixedTrashIconView.getTop();
        });
        getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        getViewParams().x = (mMetrics.widthPixels - getWidth()) / 2;
        getViewParams().y = 0;
        // Update view and layout
        mTrashViewListener.onUpdateActionTrashIcon();
        mAnimationHandler.onUpdateViewLayout();

        updateLayoutParams();
    }

    /**
     * to hide TrashView.
     */
    void dismiss() {
        // Animation stop
        mAnimationHandler.removeMessages(ANIMATION_OPEN);
        mAnimationHandler.removeMessages(ANIMATION_CLOSE);
        mAnimationHandler.sendAnimationMessage(ANIMATION_FORCE_CLOSE);
        // stop zoom animation
        setScaleTrashIconImmediately(false);
    }

    /**
     * Window Gets the drawing area on top.
     * Represents the hitbox rectangle.
     *
     * @param outRect Rect to make changes
     */
    void getWindowDrawingRect(@NonNull Rect outRect) {
        final int left = (int) (trashPosition.x - TARGET_CAPTURE_HORIZONTAL_REGION * mMetrics.density);
        final int top = (int) (trashPosition.y - TARGET_CAPTURE_VERTICAL_REGION * mMetrics.density);
        final int right = (int) (trashPosition.x + TARGET_CAPTURE_HORIZONTAL_REGION * mMetrics.density);
        final int bottom = (int) (trashPosition.y + 2 * TARGET_CAPTURE_VERTICAL_REGION * mMetrics.density);
        outRect.set(left, top, right, bottom);
    }

    /**
     * Update the action delete icon settings.
     *
     * @param width  Width of target View
     * @param height Height of target View
     */
    void updateActionTrashIcon(float width, float height) {
        // Do nothing if no action delete icon is set
        if (noHasTrashIcon()) {
            return;
        }
        // Magnification setting
        mAnimationHandler.mTargetWidth = width;
        mAnimationHandler.mTargetHeight = height;
        final float newWidthScale = width / mActionTrashIconBaseWidth;
        final float newHeightScale = height / mActionTrashIconBaseHeight;
        mActionTrashIconMaxScale = Math.max(newWidthScale, newHeightScale);
        // Enter animation creation
        mEnterScaleAnimator = ObjectAnimator.ofPropertyValuesHolder(mActionTrashIconView, PropertyValuesHolder.ofFloat(ImageView.SCALE_X, mActionTrashIconMaxScale), PropertyValuesHolder.ofFloat(ImageView.SCALE_Y, mActionTrashIconMaxScale));
        mEnterScaleAnimator.setInterpolator(new OvershootInterpolator());
        mEnterScaleAnimator.setDuration(TRASH_ICON_SCALE_DURATION_MILLIS);
        // Exit animation creation
        mExitScaleAnimator = ObjectAnimator.ofPropertyValuesHolder(mActionTrashIconView, PropertyValuesHolder.ofFloat(ImageView.SCALE_X, 1.0f), PropertyValuesHolder.ofFloat(ImageView.SCALE_Y, 1.0f));
        mExitScaleAnimator.setInterpolator(new OvershootInterpolator());
        mExitScaleAnimator.setDuration(TRASH_ICON_SCALE_DURATION_MILLIS);
    }

    Point getTrashPosition() {
        return new Point(trashPosition);
    }

    /**
     * Checks if there is a delete icon to act on.
     *
     * @return true if there is a delete icon to act on
     */
    private boolean noHasTrashIcon() {
        return mActionTrashIconBaseWidth == 0 || mActionTrashIconBaseHeight == 0;
    }

    /**
     * Sets the image for the fixed delete icon.<br/>
     * This image does not change size when the floating display overlaps.
     *
     * @param resId drawable ID
     */
    void setFixedTrashIconImage(int resId) {
        mFixedTrashIconView.setImageResource(resId);
    }

    /**
     * Sets the image of the action delete icon.<br/>
     * The size of this image changes when the floating display overlaps.
     *
     * @param resId drawable ID
     */
    void setActionTrashIconImage(int resId) {
        mActionTrashIconView.setImageResource(resId);
        final Drawable drawable = mActionTrashIconView.getDrawable();
        if (drawable != null) {
            mActionTrashIconBaseWidth = drawable.getIntrinsicWidth();
            mActionTrashIconBaseHeight = drawable.getIntrinsicHeight();
        }
    }

    /**
     * Set fixed delete icon.<br/>
     * This image does not change size when the floating display overlaps.
     *
     * @param drawable Drawable
     */
    void setFixedTrashIconImage(Drawable drawable) {
        mFixedTrashIconView.setImageDrawable(drawable);
    }

    /**
     * Sets the action delete icon.<br/>
     * The size of this image changes when the floating display overlaps.
     *
     * @param drawable Drawable
     */
    void setActionTrashIconImage(Drawable drawable) {
        mActionTrashIconView.setImageDrawable(drawable);
        if (drawable != null) {
            mActionTrashIconBaseWidth = drawable.getIntrinsicWidth();
            mActionTrashIconBaseHeight = drawable.getIntrinsicHeight();
        }
    }

    /**
     * Instantly resize the delete icon.
     *
     * @param isEnter true if the region is entered, false otherwise
     */
    private void setScaleTrashIconImmediately(boolean isEnter) {
        cancelScaleTrashAnimation();
        mActionTrashIconView.setScaleX(isEnter ? mActionTrashIconMaxScale : 1.0f);
        mActionTrashIconView.setScaleY(isEnter ? mActionTrashIconMaxScale : 1.0f);
    }

    /**
     * Change the size of the delete icon.
     *
     * @param isEnter true if the region is entered, false otherwise
     */
    void setScaleTrashIcon(boolean isEnter) {
        // Do nothing if no action icon is set
        if (noHasTrashIcon()) {
            return;
        }

        // cancel animation
        cancelScaleTrashAnimation();

        // entering the area
        if (isEnter) {
            mEnterScaleAnimator.start();
        } else {
            mExitScaleAnimator.start();
        }
    }

    /**
     * Enable/disable TrashView.
     *
     * @param enabled Enabled (visible) if true, disabled (hidden) if false
     */
    void setTrashEnabled(boolean enabled) {
        // Do nothing if settings are the same
        if (mIsEnabled == enabled) {
            return;
        }
        // Close to Hide
        mIsEnabled = enabled;
        if (!mIsEnabled) {
            dismiss();
        }
    }

    /**
     * Gets the display state of the TrashView.
     *
     * @return True if enable
     */
    boolean isTrashDisabled() {
        return !mIsEnabled;
    }

    boolean isTrashReady() {
        return mAnimationHandler.isAnimationStarted(ANIMATION_OPEN) &&
                SystemClock.uptimeMillis() - mAnimationHandler.mStartTime > AnimationHandler.TRASH_OPEN_DURATION_MILLIS;
    }

    /**
     * Cancel the enlargement/reduction animation of the delete icon
     */
    private void cancelScaleTrashAnimation() {
        // Frame animation
        if (mEnterScaleAnimator != null && mEnterScaleAnimator.isStarted()) {
            mEnterScaleAnimator.cancel();
        }

        // out-of-frame animation
        if (mExitScaleAnimator != null && mExitScaleAnimator.isStarted()) {
            mExitScaleAnimator.cancel();
        }
    }

    /**
     * Set a TrashViewListener.
     *
     * @param listener TrashViewListener
     */
    void setTrashViewListener(TrashViewListener listener) {
        mTrashViewListener = listener;
    }

    /**
     * Do something related to BubbleLayout.
     *
     * @param action Motion Event action
     * @param x      BubbleLayout's X coordinate
     * @param y      BubbleLayout's Y coordinate
     */
    void onTouchBubbleLayout(int action, float x, float y) {
        // press down
        if (action == MotionEvent.ACTION_DOWN) {
            mAnimationHandler.updateTargetPosition(x, y);
            // Wait for long press
            mAnimationHandler.removeMessages(ANIMATION_CLOSE);
            mAnimationHandler.sendAnimationMessageDelayed(ANIMATION_OPEN, LONG_PRESS_TIMEOUT);
        }
        // move
        else if (action == MotionEvent.ACTION_MOVE) {
            mAnimationHandler.updateTargetPosition(x, y);
            // Only run if the open animation has not started yet
            if (!mAnimationHandler.isAnimationStarted(ANIMATION_OPEN)) {
                // Delete long press message
                mAnimationHandler.removeMessages(ANIMATION_OPEN);
                // open
                mAnimationHandler.sendAnimationMessage(ANIMATION_OPEN);
            }
        }
        // push up, cancel
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // Delete long press message
            mAnimationHandler.removeMessages(ANIMATION_OPEN);
            mAnimationHandler.sendAnimationMessage(ANIMATION_CLOSE);
        }
    }

    void vibrate() {
        mVibrator.vibrate(50);
    }

    /**
     * Handler that controls animation.
     */
    static class AnimationHandler extends Handler {

        /**
         * milliseconds to refresh the animation
         */
        private static final long ANIMATION_REFRESH_TIME_MILLIS = 10L;

        /**
         * background animation time
         */
        private static final long BACKGROUND_DURATION_MILLIS = 200L;

        /**
         * Delete icon pop animation start delay time
         */
        private static final long TRASH_OPEN_START_DELAY_MILLIS = 200L;

        /**
         * Delete icon open animation time
         */
        private static final long TRASH_OPEN_DURATION_MILLIS = 300L;

        /**
         * Delete icon closing animation time
         */
        private static final long TRASH_CLOSE_DURATION_MILLIS = 200L;

        /**
         * Overshoot animation factor
         */
        private static final float OVERSHOOT_TENSION = 1.0f;

        /**
         * Delete icon travel limit X-axis offset (dp)
         */
        private static final int TRASH_MOVE_LIMIT_OFFSET_X = 24;

        /**
         * Delete icon travel limit Y-axis offset (dp)
         */
        private static final int TRASH_MOVE_LIMIT_TOP_OFFSET = -10;

        /**
         * Constant representing the start of the animation
         */
        private static final int TYPE_FIRST = 1;

        /**
         * Constant representing animation update
         */
        private static final int TYPE_UPDATE = 2;

        /**
         * maximum alpha
         */
        private static final float MAX_ALPHA = 1.0f;

        /**
         * minimum alpha
         */
        private static final float MIN_ALPHA = 0.0f;

        /**
         * the time the animation started
         */
        private long mStartTime;

        /**
         * Alpha value at the start of the animation
         */
        private float mStartAlpha;

        /**
         * TransitionY at the start of the animation
         */
        private float mStartTransitionY;

        /**
         * Code for animation in action
         */
        private int mStartedCode;

        /**
         * X coordinate of follow target
         */
        private float mTargetPositionX;

        /**
         * Y coordinate of follow target
         */
        private float mTargetPositionY;

        /**
         * Width of target to follow
         */
        private float mTargetWidth;

        /**
         * Height of target to follow
         */
        private float mTargetHeight;

        /**
         * Move limit position of delete icon
         */
        private final Rect mTrashIconLimitPosition;

        /**
         * Y-axis tracking range
         */
        private float mMoveStickyYRange;

        /**
         * OvershootInterpolator
         */
        private final OvershootInterpolator mOvershootInterpolator;

        /**
         * TrashView
         */
        private final WeakReference<BubbleTrash> mTrashView;

        /**
         * constructor
         */
        AnimationHandler(Looper looper, BubbleTrash trashView) {
            super(looper);
            mTrashView = new WeakReference<>(trashView);
            mStartedCode = ANIMATION_NONE;
            mTrashIconLimitPosition = new Rect();
            mOvershootInterpolator = new OvershootInterpolator(OVERSHOOT_TENSION);
        }

        /**
         * handle the animation.
         */
        @Override
        public void handleMessage(Message msg) {
            final BubbleTrash trashView = mTrashView.get();
            if (trashView == null) {
                removeMessages(ANIMATION_OPEN);
                removeMessages(ANIMATION_CLOSE);
                removeMessages(ANIMATION_FORCE_CLOSE);
                return;
            }

            // Don't animate if not valid
            if (trashView.isTrashDisabled()) {
                return;
            }

            final int animationCode = msg.what;
            final int animationType = msg.arg1;
            final FrameLayout backgroundView = trashView.mBackgroundView;
            final FrameLayout trashIconRootView = trashView.mTrashIconRootView;
            final TrashViewListener listener = trashView.mTrashViewListener;
            final float screenWidth = trashView.mMetrics.widthPixels;
            final float trashViewX = trashView.getViewParams().x;

            // Initialization when animation starts
            if (animationType == TYPE_FIRST) {
                mStartTime = SystemClock.uptimeMillis();
                mStartAlpha = backgroundView.getAlpha();
                mStartTransitionY = trashIconRootView.getTranslationY();
                mStartedCode = animationCode;
                if (listener != null) {
                    listener.onTrashAnimationStarted(mStartedCode);
                }
            }
            // elapsed time
            final float elapsedTime = SystemClock.uptimeMillis() - mStartTime;

            // display animation
            if (animationCode == ANIMATION_OPEN) {
                final float currentAlpha = backgroundView.getAlpha();
                // If the maximum alpha value is not reached
                if (currentAlpha < MAX_ALPHA) {
                    final float alphaTimeRate = Math.min(elapsedTime / BACKGROUND_DURATION_MILLIS, 1.0f);
                    final float alpha = Math.min(mStartAlpha + alphaTimeRate, MAX_ALPHA);
                    backgroundView.setAlpha(alpha);
                }

                // Animation starts if DelayTime is exceeded
                if (elapsedTime >= TRASH_OPEN_START_DELAY_MILLIS) {
                    final float screenHeight = trashView.mMetrics.heightPixels;
                    // 0% and 100% calculation when the icon protrudes all to the left and right
                    final float positionX = trashViewX + (mTargetPositionX + mTargetWidth) / (screenWidth + mTargetWidth) * mTrashIconLimitPosition.width() + mTrashIconLimitPosition.left;
                    // Y-coordinate animation and follow-up of delete icon (negative upward direction)
                    // targetPositionYRate is 0% when the target Y coordinate is completely off screen, and 100% after half of the screen
                    // stickyPositionY moves to the upper end with the lower end of the movement limit as the origin. mMoveStickyRange is the sticky range
                    // Calculate positionY to move over time
                    final float targetPositionYRate = Math.min(2 * (mTargetPositionY + mTargetHeight) / (screenHeight + mTargetHeight), 1.0f);
                    final float stickyPositionY = mMoveStickyYRange * targetPositionYRate + mTrashIconLimitPosition.height() - mMoveStickyYRange;
                    final float translationYTimeRate = Math.min((elapsedTime - TRASH_OPEN_START_DELAY_MILLIS) / TRASH_OPEN_DURATION_MILLIS, 1.0f);
                    final float positionY = mTrashIconLimitPosition.bottom - stickyPositionY * mOvershootInterpolator.getInterpolation(translationYTimeRate);
                    trashIconRootView.setTranslationX(positionX);
                    trashIconRootView.setTranslationY(positionY);
                    // clear drag view garbage
                }
                sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS);
            }
            // hide animation
            else if (animationCode == ANIMATION_CLOSE) {
                // Alpha value calculation
                final float alphaElapseTimeRate = Math.min(elapsedTime / BACKGROUND_DURATION_MILLIS, 1.0f);
                final float alpha = Math.max(mStartAlpha - alphaElapseTimeRate, MIN_ALPHA);
                backgroundView.setAlpha(alpha);

                // Y-coordinate animation of delete icon
                final float translationYTimeRate = Math.min(elapsedTime / TRASH_CLOSE_DURATION_MILLIS, 1.0f);
                // If the animation has not reached the end
                if (alphaElapseTimeRate < 1.0f || translationYTimeRate < 1.0f) {
                    final float position = mStartTransitionY + mTrashIconLimitPosition.height() * translationYTimeRate;
                    trashIconRootView.setTranslationY(position);
                    sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS);
                } else {
                    // Force position adjustment
                    trashIconRootView.setTranslationY(mTrashIconLimitPosition.bottom);
                    mStartedCode = ANIMATION_NONE;
                    if (listener != null) {
                        listener.onTrashAnimationEnd(ANIMATION_CLOSE);
                    }
                }
            }
            // Immediate non-representation
            else if (animationCode == ANIMATION_FORCE_CLOSE) {
                backgroundView.setAlpha(0.0f);
                trashIconRootView.setTranslationY(mTrashIconLimitPosition.bottom);
                mStartedCode = ANIMATION_NONE;
                if (listener != null) {
                    listener.onTrashAnimationEnd(ANIMATION_FORCE_CLOSE);
                }
            }
        }

        /**
         * Send an animated message.
         *
         * @param animation   ANIMATION_OPEN,ANIMATION_CLOSE,ANIMATION_FORCE_CLOSE
         * @param delayMillis Message sent time
         */
        void sendAnimationMessageDelayed(int animation, long delayMillis) {
            sendMessageAtTime(newMessage(animation, TYPE_FIRST), SystemClock.uptimeMillis() + delayMillis);
        }

        /**
         * Send an animated message.
         *
         * @param animation ANIMATION_OPEN,ANIMATION_CLOSE,ANIMATION_FORCE_CLOSE
         */
        void sendAnimationMessage(int animation) {
            sendMessage(newMessage(animation, TYPE_FIRST));
        }

        /**
         * Generate a message to send.
         *
         * @param animation ANIMATION_OPEN,ANIMATION_CLOSE,ANIMATION_FORCE_CLOSE
         * @param type      TYPE_FIRST,TYPE_UPDATE
         * @return Message
         */
        @NonNull
        private static Message newMessage(int animation, int type) {
            final Message message = Message.obtain();
            message.what = animation;
            message.arg1 = type;
            return message;
        }

        /**
         * Checks if the animation has started.
         *
         * @param animationCode animation code
         * @return true if the animation has started, false otherwise
         */
        boolean isAnimationStarted(int animationCode) {
            return mStartedCode == animationCode;
        }

        /**
         * Update the location information of the tracking target.
         *
         * @param x X coordinate of follow target
         * @param y Y coordinate of follow target
         */
        void updateTargetPosition(float x, float y) {
            mTargetPositionX = x;
            mTargetPositionY = y;
        }

        /**
         * Called when the view's visibility changes.
         */
        void onUpdateViewLayout() {
            final BubbleTrash trashView = mTrashView.get();
            if (trashView == null) {
                return;
            }
            // Setting the movement limit of the delete icon (TrashIconRootView) (calculated based on the Gravity reference position)
            // The bottom left origin (bottom edge of the screen (including padding): 0, upward direction: negative, downward direction: positive),
            // the upper limit of the Y axis is the position where the delete icon is in the center of the background,
            // and the lower limit is the position where the TrashIconRootView is completely hidden.
            final float density = trashView.mMetrics.density;
            final float backgroundHeight = trashView.mBackgroundView.getMeasuredHeight();
            final float offsetX = TRASH_MOVE_LIMIT_OFFSET_X * density;
            final int trashIconHeight = trashView.mTrashIconRootView.getMeasuredHeight();
            final int left = (int) -offsetX;
            final int top = (int) ((trashIconHeight - backgroundHeight) / 2 - TRASH_MOVE_LIMIT_TOP_OFFSET * density);
            final int right = (int) offsetX;
            //noinspection UnnecessaryLocalVariable
            final int bottom = trashIconHeight;
            mTrashIconLimitPosition.set(left, top, right, bottom);

            // Set Y-axis tracking range based on background size
            mMoveStickyYRange = backgroundHeight * 0.20f;
        }
    }
}
