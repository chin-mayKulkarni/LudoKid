package com.ludokid.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator

/**
 * Custom animated dice view — shows pip-based dice faces with roll animation.
 */
class DiceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentValue = 1
    private var isAnimating = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(8f, 2f, 2f, Color.parseColor("#66000000"))
    }

    private val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A2E")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6C63FF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // Active player's color
    private var playerColor: Int = Color.parseColor("#6C63FF")

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setDiceValue(value: Int) {
        currentValue = value
        invalidate()
    }

    fun setPlayerColor(color: Int) {
        playerColor = color
        borderPaint.color = color
        invalidate()
    }

    fun animateRoll(finalValue: Int, onComplete: () -> Unit) {
        if (isAnimating) return
        isAnimating = true

        val shakeX = ObjectAnimator.ofFloat(this, "translationX", 0f, -15f, 15f, -10f, 10f, 0f)
        val shakeY = ObjectAnimator.ofFloat(this, "translationY", 0f, -10f, 10f, 0f)
        val scaleDown = ObjectAnimator.ofFloat(this, "scaleX", 1f, 0.85f)
        val scaleDownY = ObjectAnimator.ofFloat(this, "scaleY", 1f, 0.85f)

        shakeX.duration = 600
        shakeY.duration = 600
        scaleDown.duration = 600
        scaleDownY.duration = 600
        shakeX.interpolator = AccelerateDecelerateInterpolator()

        val set = AnimatorSet()
        set.playTogether(shakeX, shakeY, scaleDown, scaleDownY)

        // Start frame-flip ONLY after shake+scaleDown completes — avoids partial-scale frames
        set.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                var frameCount = 0
                val frameRunnable = object : Runnable {
                    override fun run() {
                        if (frameCount < 8) {
                            currentValue = (1..6).random()
                            invalidate()
                            frameCount++
                            postDelayed(this, 80)
                        } else {
                            currentValue = finalValue
                            invalidate()
                            isAnimating = false

                            // Bounce scale up — read actual current scale to avoid jump
                            val currentScale = scaleX
                            val scaleUp = ObjectAnimator.ofFloat(this@DiceView, "scaleX", currentScale, 1.1f, 1f)
                            val scaleUpY = ObjectAnimator.ofFloat(this@DiceView, "scaleY", currentScale, 1.1f, 1f)
                            scaleUp.duration = 300
                            scaleUpY.duration = 300
                            scaleUp.interpolator = BounceInterpolator()
                            val bounceSet = AnimatorSet()
                            bounceSet.playTogether(scaleUp, scaleUpY)
                            bounceSet.start()
                            bounceSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: android.animation.Animator) {
                                    onComplete()
                                }
                            })
                        }
                    }
                }
                post(frameRunnable)
            }
        })

        set.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        val padding = size * 0.06f
        val cornerRadius = size * 0.2f

        // Background
        bgPaint.color = Color.WHITE
        canvas.drawRoundRect(
            padding, padding, size - padding, size - padding,
            cornerRadius, cornerRadius, bgPaint
        )

        // Border with player color
        borderPaint.strokeWidth = size * 0.05f
        canvas.drawRoundRect(
            padding, padding, size - padding, size - padding,
            cornerRadius, cornerRadius, borderPaint
        )

        // Draw pips
        drawPips(canvas, size, padding)
    }

    private fun drawPips(canvas: Canvas, size: Float, padding: Float) {
        val pipR = size * 0.09f
        val usable = size - padding * 2
        val third = usable / 3f
        val start = padding + third / 2f

        // Pip positions (row, col) in thirds of the face
        val positions = getPipPositions(currentValue)
        pipPaint.color = Color.parseColor("#1A1A2E")

        positions.forEach { (row, col) ->
            val cx = start + col * third
            val cy = start + row * third
            canvas.drawCircle(cx, cy, pipR, pipPaint)
        }
    }

    private fun getPipPositions(value: Int): List<Pair<Float, Float>> = when (value) {
        1 -> listOf(Pair(1f, 1f))
        2 -> listOf(Pair(0f, 2f), Pair(2f, 0f))
        3 -> listOf(Pair(0f, 2f), Pair(1f, 1f), Pair(2f, 0f))
        4 -> listOf(Pair(0f, 0f), Pair(0f, 2f), Pair(2f, 0f), Pair(2f, 2f))
        5 -> listOf(Pair(0f, 0f), Pair(0f, 2f), Pair(1f, 1f), Pair(2f, 0f), Pair(2f, 2f))
        6 -> listOf(
            Pair(0f, 0f), Pair(1f, 0f), Pair(2f, 0f),
            Pair(0f, 2f), Pair(1f, 2f), Pair(2f, 2f)
        )
        else -> emptyList()
    }
}
