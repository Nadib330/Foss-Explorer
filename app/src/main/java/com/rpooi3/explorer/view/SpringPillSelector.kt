/*
 * Explorer: the shared spring-morphing selector used by the home-style chooser.
 *
 * The thumb is deliberately the only animated part. The buttons remain ordinary Material buttons
 * in their existing group, so restoring this motion does not bring back unrelated 18.8.2 UI.
 */
package com.rpooi3.explorer.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.rpooi3.explorer.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Moves one shared pill thumb between the buttons with the 18.8.2 spring motion. */
class SpringPillSelector(
    private val track: ViewGroup,
    private val group: MaterialButtonToggleGroup,
    private val buttons: List<MaterialButton>,
) {
    companion object {
        // These are the motion values used by the 18.8.2 selector.
        const val SPRING_DAMPING = 0.78f
        const val SPRING_STIFFNESS = 650f
    }

    private val thumb: View
    private val translationXSpring: SpringAnimation
    private val translationYSpring: SpringAnimation
    private val scaleXSpring: SpringAnimation
    private val scaleYSpring: SpringAnimation
    private var selectedIndex = -1
    private var suppressAnimation = false

    init {
        require(buttons.size >= 2) { "A spring pill needs at least two buttons" }
        require(buttons.all { it.parent === group }) {
            "Spring pill buttons must belong to its toggle group"
        }

        track.clipChildren = false
        track.clipToPadding = false
        thumb = track.findViewById<View>(R.id.spring_pill_thumb)
            ?: View(track.context).also { created ->
                created.id = R.id.spring_pill_thumb
                track.addView(
                    created,
                    0,
                    FrameLayout.LayoutParams(0, 0),
                )
            }
        styleThumb(thumb, track.context)

        // MaterialButton's own fill would cover the shared thumb. Its ripple and text remain
        // untouched; only the opaque fill and outline are made transparent.
        buttons.forEach { button ->
            button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            button.strokeWidth = 0
        }

        translationXSpring = SpringAnimation(thumb, DynamicAnimation.TRANSLATION_X)
        translationYSpring = SpringAnimation(thumb, DynamicAnimation.TRANSLATION_Y)
        scaleXSpring = SpringAnimation(thumb, DynamicAnimation.SCALE_X)
        scaleYSpring = SpringAnimation(thumb, DynamicAnimation.SCALE_Y)

        group.addOnButtonCheckedListener { _, checkedId, checked ->
            if (!checked || suppressAnimation) return@addOnButtonCheckedListener
            val index = buttons.indexOfFirst { it.id == checkedId }
            if (index >= 0) {
                selectedIndex = index
                moveTo(index, animate = true)
            }
        }
        track.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                if (selectedIndex >= 0) moveTo(selectedIndex, animate = false)
            }
        }

        // The chooser can be bound before its preference row has been laid out. The first
        // selection is therefore also synced by the layout callback above.
        if (track.isLaidOut && !track.isLayoutRequested) syncToChecked(false)
        else track.post { syncToChecked(false) }
    }

    /** Selects [buttonId], optionally animating the thumb from its current position. */
    fun select(buttonId: Int, animate: Boolean = true) {
        val index = buttons.indexOfFirst { it.id == buttonId }
        if (index < 0) return
        suppressAnimation = true
        try {
            if (group.checkedButtonId != buttonId) group.check(buttonId)
        } finally {
            suppressAnimation = false
        }
        syncToChecked(animate)
    }

    private fun syncToChecked(animate: Boolean) {
        val checkedId = group.checkedButtonId
        val index = buttons.indexOfFirst { it.id == checkedId }
        if (index < 0) return
        selectedIndex = index
        moveTo(index, animate)
    }

    private fun moveTo(index: Int, animate: Boolean) {
        if (index !in buttons.indices || track.width <= 0 || track.height <= 0) return
        val button = buttons[index]
        if (button.width <= 0 || button.height <= 0) return

        val targetLeft = relativeLeft(button)
        val targetTop = relativeTop(button)
        val targetWidth = button.width.toFloat()
        val targetHeight = button.height.toFloat()

        val hasBounds = thumb.width > 0 && thumb.height > 0
        val currentLeft = if (hasBounds) thumb.left + thumb.translationX else targetLeft
        val currentTop = if (hasBounds) thumb.top + thumb.translationY else targetTop
        val currentWidth = if (hasBounds) thumb.width * thumb.scaleX else targetWidth
        val currentHeight = if (hasBounds) thumb.height * thumb.scaleY else targetHeight
        val alreadyThere = hasBounds &&
            abs(currentLeft - targetLeft) < 1f &&
            abs(currentTop - targetTop) < 1f &&
            abs(currentWidth - targetWidth) < 1f &&
            abs(currentHeight - targetHeight) < 1f

        val params = (thumb.layoutParams as? ViewGroup.MarginLayoutParams)
            ?: FrameLayout.LayoutParams(0, 0)
        params.width = targetWidth.roundToInt()
        params.height = targetHeight.roundToInt()
        params.leftMargin = (targetLeft - track.paddingLeft).roundToInt()
        params.topMargin = (targetTop - track.paddingTop).roundToInt()
        thumb.layoutParams = params

        if (!hasBounds) {
            thumb.measure(
                View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(params.height, View.MeasureSpec.EXACTLY),
            )
            thumb.layout(
                targetLeft.roundToInt(),
                targetTop.roundToInt(),
                (targetLeft + targetWidth).roundToInt(),
                (targetTop + targetHeight).roundToInt(),
            )
        }

        cancelAnimations()
        val canAnimate = animate &&
            track.isAttachedToWindow &&
            track.windowToken != null &&
            hasBounds &&
            !alreadyThere
        if (canAnimate) {
            thumb.pivotX = 0f
            thumb.pivotY = 0f
            thumb.translationX = currentLeft - targetLeft
            thumb.translationY = currentTop - targetTop
            thumb.scaleX = max(0.01f, currentWidth / targetWidth)
            thumb.scaleY = max(0.01f, currentHeight / targetHeight)
            startSpring(translationXSpring, 0f)
            startSpring(translationYSpring, 0f)
            startSpring(scaleXSpring, 1f)
            startSpring(scaleYSpring, 1f)
        } else {
            thumb.translationX = 0f
            thumb.translationY = 0f
            thumb.scaleX = 1f
            thumb.scaleY = 1f
        }
        thumb.visibility = View.VISIBLE
    }

    private fun startSpring(animation: SpringAnimation, finalPosition: Float) {
        animation.cancel()
        animation.spring = SpringForce(finalPosition).apply {
            dampingRatio = SPRING_DAMPING
            stiffness = SPRING_STIFFNESS
        }
        animation.start()
    }

    private fun cancelAnimations() {
        translationXSpring.cancel()
        translationYSpring.cancel()
        scaleXSpring.cancel()
        scaleYSpring.cancel()
    }

    private fun relativeLeft(view: View): Float {
        var current: View? = view
        var result = 0f
        while (current != null && current !== track) {
            result += current.left
            current = current.parent as? View
        }
        return if (current === track) result else view.left.toFloat()
    }

    private fun relativeTop(view: View): Float {
        var current: View? = view
        var result = 0f
        while (current != null && current !== track) {
            result += current.top
            current = current.parent as? View
        }
        return if (current === track) result else view.top.toFloat()
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private fun styleThumb(view: View, context: Context) {
        view.setBackgroundResource(R.drawable.bg_spring_pill_thumb)
        view.elevation = 0f
        view.translationZ = 0f
        view.stateListAnimator = null
        view.clipToOutline = true
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        view.isClickable = false
        view.isFocusable = false
    }
}
