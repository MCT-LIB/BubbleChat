package com.mct.bubblechat;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Property;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatPropertyCompat;

class BubbleBaseLayout extends FrameLayout {

    private final Object lock = new Object();
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private boolean isAttach;

    WindowManager getWindowManager() {
        if (windowManager == null) {
            windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        }
        return this.windowManager;
    }

    void setViewParams(WindowManager.LayoutParams params) {
        this.params = params;
    }

    WindowManager.LayoutParams getViewParams() {
        return this.params;
    }

    public void updateLayoutParams() {
        synchronized (lock) {
            if (isAttach) {
                getWindowManager().updateViewLayout(this, getViewParams());
            }
        }
    }

    public void attachToWindow() {
        synchronized (lock) {
            if (!isAttach) {
                isAttach = true;
                getWindowManager().addView(this, getViewParams());
            }
        }
    }

    public void detachFromWindow() {
        synchronized (lock) {
            if (isAttach) {
                isAttach = false;
                getWindowManager().removeViewImmediate(this);
            }
        }
    }

    public BubbleBaseLayout(Context context) {
        super(context);
    }

    public BubbleBaseLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BubbleBaseLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    static abstract class BubbleProperty extends Property<View, Float> {
        public BubbleProperty(String name) {
            super(Float.class, name);
        }

        public FloatPropertyCompat<View> getPropertyCompat() {
            return new FloatPropertyCompat<View>(getName()) {
                @Override
                public float getValue(View object) {
                    return get(object);
                }

                @Override
                public void setValue(View object, float value) {
                    set(object, value);
                }
            };
        }

        protected BubbleBaseLayout cast(View view) {
            return (BubbleBaseLayout) view;
        }
    }

    static final BubbleProperty WINDOW_X = new BubbleProperty("WINDOW_X") {
        @Override
        public Float get(@NonNull View object) {
            return (float) cast(object).getViewParams().x;
        }

        @Override
        public void set(@NonNull View object, @NonNull Float value) {
            BubbleBaseLayout layout = cast(object);
            layout.getViewParams().x = value.intValue();
            layout.updateLayoutParams();
        }
    };

    static final BubbleProperty WINDOW_Y = new BubbleProperty("WINDOW_Y") {
        @Override
        public Float get(@NonNull View object) {
            return (float) cast(object).getViewParams().y;
        }

        @Override
        public void set(@NonNull View object, @NonNull Float value) {
            BubbleBaseLayout layout = cast(object);
            layout.getViewParams().y = value.intValue();
            layout.updateLayoutParams();
        }
    };
}
