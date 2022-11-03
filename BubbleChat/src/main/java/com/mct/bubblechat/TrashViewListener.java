package com.mct.bubblechat;

import com.mct.bubblechat.BubbleTrash.AnimationState;

/**
 * TrashViewA listener that handles the events of .
 * INFO: Due to the specification that the delete icon follows,
 *       the end of the OPEN animation is not notified.
 */
interface TrashViewListener {

    /**
     * Require ActionTrashIcon updates.
     */
    void onUpdateActionTrashIcon();

    /**
     * Notified when an animation has started.
     *
     * @param animationCode animation code
     */
    void onTrashAnimationStarted(@AnimationState int animationCode);

    /**
     * Notified when the animation has finished.
     *
     * @param animationCode animation code
     */
    void onTrashAnimationEnd(@AnimationState int animationCode);


}
