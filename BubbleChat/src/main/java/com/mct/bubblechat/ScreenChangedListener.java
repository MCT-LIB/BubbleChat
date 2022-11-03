package com.mct.bubblechat;

import android.graphics.Rect;

/**
 * Listener for screen changes.
 */
interface ScreenChangedListener {
    /**
     * Called when the screen has changed.
     *
     * @param windowRect System window rect
     * @param visibility System UI Mode
     */
    void onScreenChanged(Rect windowRect, int visibility);
}
