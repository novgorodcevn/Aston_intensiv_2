package com.example.customdrum

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.DrawableRes
import androidx.core.animation.doOnEnd
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toRect
import androidx.core.graphics.toRectF
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random


class CustomDrum(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context,attrs) {

    private enum class Action { SHOW_TEXT, SHOW_IMAGE }
    private data class Sector(@DrawableRes val color: Int, val action: Action, val data: String)

    private val sectors = listOf<Sector>(
        Sector(R.color.red, Action.SHOW_TEXT, "красный"),
        Sector(
            R.color.orange, Action.SHOW_IMAGE, "https://loremflickr.com/320/240/nature,sea,wave"
        ),
        Sector(R.color.yellow, Action.SHOW_TEXT, "жёлтый"),
        Sector(R.color.green, Action.SHOW_IMAGE, "https://loremflickr.com/320/240/nature,sea,wave"),
        Sector(R.color.lightblue, Action.SHOW_TEXT, "голубой"),
        Sector(R.color.blue, Action.SHOW_IMAGE, "https://loremflickr.com/320/240/nature,sea,wave"),
        Sector(R.color.violet, Action.SHOW_TEXT, "фиолетовый"),
    )
    private var currentSector = sectors.first()

    private val wheelCenterX by lazy { maxSize / 2f }
    private val wheelCenterY by lazy { maxSize * 1.5f }

    private val sectorAngle = 360f / sectors.size
    private val sweepAngle = -1 * sectorAngle
    private var wheelSizeRate = 0.5f
    private var rotationWheel = 0f
    private var isRotates = false
    private lateinit var bmWheel: Bitmap
    private lateinit var canvasWheel: Canvas
    private val painterWheel = Paint().apply {
        alpha = 255
        style = Paint.Style.FILL
        Paint.ANTI_ALIAS_FLAG
    }

    private var maxSize = 0f
    private val minSize by lazy { maxSize * 0.25f }

    private val btnTitle = "Spin"
    private val radiusBtnSpin by lazy { maxSize * 0.1f }
    private var bntSpinIsPush = false
    private var bntSpinIsClick = false
    private val bounds = Rect()
    private val painterBtnSpin = Paint().apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        Paint.ANTI_ALIAS_FLAG
    }
    private val painterTextBtn = Paint().apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textSize = 48f
        Paint.ANTI_ALIAS_FLAG
    }
    private var currentText = "КРУТИТЕ БАРАБАН!"
    private var textColor = Color.GRAY
    private val painterText = Paint().apply {
        alpha = 255
        style = Paint.Style.FILL
        color = textColor
        textSize = 56f
        Paint.ANTI_ALIAS_FLAG
    }

    private var currentImage: Bitmap? = if (Random.nextInt(0, 3) == 0)
        ResourcesCompat.getDrawable(resources, R.drawable.ic_launcher_foreground, null)
            ?.toBitmap() else null
    private var downloadImage: Bitmap? = null
    private var updateImage = -1
    private val painterImage = Paint().apply {
        flags = Paint.ANTI_ALIAS_FLAG
    }
    private val coroutineScope by lazy { CoroutineScope(Dispatchers.Main + SupervisorJob()) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        maxSize = if (width * 2f < height) width.toFloat() else height / 2f
    }

    override fun onDraw(canvas: Canvas) {
        drawImage(canvas)
        drawText(canvas)
        drawWheel(canvas)
        drawBtnSpin(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
        if (hypot(
                (event.x - wheelCenterX).toDouble(),
                (event.y - wheelCenterY).toDouble()
            ) >= radiusBtnSpin
        ) {
            bntSpinIsPush = false
            bntSpinIsClick = false
        } else {
            bntSpinIsPush = true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> bntSpinIsClick = true
                MotionEvent.ACTION_UP -> {
                    if (bntSpinIsClick) if (!isRotates) rotateWheel()
                    bntSpinIsClick = false
                    bntSpinIsPush = false
                }
            }
        }
        invalidate()
        return true
    }

    override fun onDetachedFromWindow() {
        coroutineScope.cancel()
        super.onDetachedFromWindow()
    }

    fun updateWheelSize(newSizePercents: Int) {
        wheelSizeRate = newSizePercents / 100f
        invalidate()
    }

    private var drawWheel = ::initDrawWheel

    @SuppressLint("ResourceType")
    private fun initDrawWheel(canvas: Canvas) {
        val size = maxSize.toInt()
        val viewRect = Rect(0, 0, size, size)
        val startAngle = rotationWheel + sectorAngle / 2 - 90f

        bmWheel = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        canvasWheel = Canvas(bmWheel)
        sectors.forEach { sector: Sector ->
            painterWheel.color = ResourcesCompat.getColor(resources, sector.color, null)
            canvasWheel.drawArc(
                viewRect.toRectF(),
                startAngle,
                sweepAngle - 0.5f,
                true,
                painterWheel
            )
            canvasWheel.rotate(sweepAngle, maxSize / 2, maxSize / 2)
        }
        drawWheel = ::periodicDrawWheel
        drawWheel(canvas)
    }

    private fun periodicDrawWheel(canvas: Canvas) {
        val radius = ((((maxSize - minSize) * wheelSizeRate) + minSize) / 2).toInt()
        val oval = RectF(
            wheelCenterX - radius,
            wheelCenterY - radius,
            wheelCenterX + radius,
            wheelCenterY + radius
        )
        canvas.save()
        canvas.rotate(rotationWheel, wheelCenterX, wheelCenterY)
        canvas.drawBitmap(bmWheel, null, oval.toRect(), null)
        canvas.restore()
    }

    private fun drawBtnSpin(canvas: Canvas) {
        val radiusBtnSpinClicked = if (bntSpinIsPush) radiusBtnSpin * 0.95f else radiusBtnSpin
        canvas.drawCircle(wheelCenterX, wheelCenterY, radiusBtnSpinClicked, painterBtnSpin)

        canvas.drawArc(
            wheelCenterX - radiusBtnSpin,
            wheelCenterY - radiusBtnSpin * 3 + 16,
            wheelCenterX + radiusBtnSpin,
            wheelCenterY - radiusBtnSpin + 16,
            90 - 45f / 2,
            45f,
            true,
            painterBtnSpin
        )

        painterTextBtn.getTextBounds(btnTitle, 0, btnTitle.length, bounds)

        canvas.drawText(
            btnTitle,
            wheelCenterX - bounds.exactCenterX(),
            wheelCenterY - bounds.exactCenterY(),
            painterTextBtn
        )
    }

    private fun drawImage(canvas: Canvas) {
        if (updateImage == 0) {
            currentImage = downloadImage
            updateImage = -1
        }
        val bitmap = currentImage ?: return

        val scale = min(
            maxSize / bitmap.width.toFloat(), maxSize / bitmap.height.toFloat()
        )

        canvas.drawBitmap(
            bitmap, null, Rect(
                0, 0, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt()
            ), painterImage
        )
    }

    private fun drawText(canvas: Canvas) {
        if (currentText == "") return
        painterText.apply {
            color = textColor
        }

        val rect = Rect()
        painterText.getTextBounds(currentText, 0, currentText.length, rect)

        val bmText = Bitmap.createBitmap(
            rect.width() + 10,
            rect.height() * 2,
            Bitmap.Config.ARGB_8888
        )

        val canvasText = Canvas(bmText)
        canvasText.drawText(
            currentText,
            0f,
            rect.height() - (painterText.ascent() + painterText.descent()) / 2,
            painterText
        )
        canvas.drawBitmap(
            bmText, null, RectF(
                0f,
                maxSize - rect.height() * 2,
                rect.width().toFloat(),
                maxSize
            ), null
        )
    }

    @SuppressLint("ResourceType")
    private fun rotateWheel() {
        isRotates = true
        val step = Random.nextInt(sectors.size, sectors.size * 7 - 1)
        val index = sectors.indexOf(currentSector)
        val newIndex = (index + step) % sectors.size
        currentSector = sectors[newIndex]
        if (currentSector.action == Action.SHOW_IMAGE) loadNewImage(currentSector.data)

        val currentAngle = (index * 360f) / sectors.size
        val rotate = ((step + index) * 360f) / sectors.size

        val animator = ValueAnimator.ofFloat(currentAngle, rotate)
        animator.addUpdateListener {
            rotationWheel = it.animatedValue as Float
            invalidate()
        }
        animator.doOnEnd {
            when (currentSector.action) {
                Action.SHOW_TEXT -> {
                    currentText = currentSector.data
                    textColor = ResourcesCompat.getColor(resources, currentSector.color, null)
                }

                Action.SHOW_IMAGE -> {
                    updateImage--
                    textColor = ResourcesCompat.getColor(resources, currentSector.color, null)
                }
            }
            isRotates = false
        }
        animator.duration = step * if (step > sectors.size * 2) 100L else 200L
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.start()
    }

    private fun loadNewImage(uri: String) {
        updateImage = 2
        coroutineScope.launch {
            Glide.with(context).asBitmap().load(uri).diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true).addListener(object : RequestListener<Bitmap> {
                    override fun onResourceReady(
                        resource: Bitmap,
                        model: Any,
                        target: Target<Bitmap>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        downloadImage = resource
                        updateImage--
                        invalidate()
                        return true
                    }

                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Bitmap>,
                        isFirstResource: Boolean
                    ): Boolean {
                        return true
                    }
                }).preload()
        }
    }

    fun deleteContent() {
        currentText = ""
        currentImage = null
        invalidate()
    }
}
