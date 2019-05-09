package com.jkjk.reboundscrollview

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Scroller

open class ReboundScrollView(

    context: Context?,
    attrs: AttributeSet?,
    defStyleAttr: Int

) : ScrollView(context, attrs, defStyleAttr), View.OnTouchListener, GestureDetector.OnGestureListener {

    var pageChangeThreshold = 20

    protected var gestureDetector: GestureDetector? = null

    protected var scroller: Scroller? = null

    var activeItem = 0
        private set

    private var isInitiation = true
    private var flingDisable = true

    protected var llMain: LinearLayout? = null

    private var y1 = 0f
    private var y2 = 0f
    private var dy = 0f

    protected var onPageChangeListeners = arrayListOf<OnPageChangeListener>()

    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?) : this(context, null)


    init {
        if (context != null) {
            context.theme?.obtainStyledAttributes(
                attrs,
                R.styleable.ReboundScrollView,
                0, 0).also {

                try {
                    pageChangeThreshold = it?.getInt(R.styleable.ReboundScrollView_page_change_threshold, 20) ?:20
                    pageChangeThreshold = Math.min(100, pageChangeThreshold)
                    pageChangeThreshold = Math.max(0, pageChangeThreshold)
                } finally {
                    it?.recycle()
                }
            }
        }

        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        gestureDetector = GestureDetector(context, this)
        scroller = Scroller(context, DecelerateInterpolator())

        setOnTouchListener(this)

    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        val view = if (childCount > 0) getChildAt(0) else null

        if (view != null && view is LinearLayout) {
            llMain = view
        } else {
            throw IllegalArgumentException("The view under ReboundScrollView must be a LinearLayout")
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val parcelable = super.onSaveInstanceState()
        val result = ReboundScrollViewSavedState(parcelable)
        result.flingDisable = flingDisable
        result.activeItem = activeItem
        return result
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val reboundScrollViewSavedState = state as? ReboundScrollViewSavedState
        super.onRestoreInstanceState(reboundScrollViewSavedState?.superState)
        activeItem = reboundScrollViewSavedState?.activeItem ?: activeItem
        flingDisable = reboundScrollViewSavedState?.flingDisable ?: flingDisable
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (scroller!!.computeScrollOffset()) {
            // stop the fling
            scroller!!.forceFinished(true)
            this.scrollTo(scroller!!.currX, scroller!!.currY)

            // intercept down action when flinging to avoid unexpected click
            if (ev?.action == MotionEvent.ACTION_DOWN)
                return true
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouch(v: View, ev: MotionEvent?): Boolean {

        var returnValue: Boolean? = null

        try {
            if (gestureDetector != null && ev != null && v == this) {
                returnValue = gestureDetector?.onTouchEvent(ev)
            }

        } catch (e: NullPointerException) {
            Log.e(TAG, "onTouch()", e)
            return true
        }catch (e:IllegalArgumentException){
            Log.e(TAG, "onTouch()", e)
            return true
        }

        when (ev?.action) {
            MotionEvent.ACTION_MOVE ->
                if (isInitiation) {
                    y1 = ev.y
                    isInitiation = false
                }
            MotionEvent.ACTION_UP -> {
                if (!scroller!!.computeScrollOffset()) {
                    y2 = ev.y
                    dy = y2 - y1

                    isInitiation = true

                    if (dy > MIN_SCROLL_TRIGGER) {
                        // scrolling down
                        Log.i(TAG, "onTouch(): scrolling up")
                        reboundAfterMoveUpward()
                        returnValue = true
                    } else if (dy < -MIN_SCROLL_TRIGGER) {
                        // scrolling up
                        Log.i(TAG, "onTouch(): scrolling down")
                        reboundAfterMoveDownward()
                        returnValue = true
                    }

                }
            }
        }
        return returnValue ?: false
    }

    protected open fun reboundAfterMoveUpward() {
        val oldActiveItem = activeItem

        if (llMain == null || llMain?.childCount == 0) {
            Log.i(TAG, "reboundAfterMoveUpward(): llMain == null ? ${llMain == null}")
            return
        }

        if (activeItem > 0) {
            // calculate the threshold to trigger change of page
            val minScrollYToTriggerPageUp =
                llMain!!.getChildAt(activeItem).top - (Math.min(
                    llMain!!.getChildAt(activeItem - 1).measuredHeight,
                    this.measuredHeight) *
                        (pageChangeThreshold / 100f))

            Log.i(TAG, "Upward(), y2:$y2, y1:$y1, dy:$dy, minFactor:$minScrollYToTriggerPageUp")
            if (scrollY - dy < minScrollYToTriggerPageUp) {
                Log.i(TAG, "Upward, distance enough()")
                if (activeItem - 1 >= 0) {
                    activeItem--
                }
            }
        }

        val activeView = llMain!!.getChildAt(activeItem)

        // Fling is not available if the active view is the same size or smaller than screen
        flingDisable = activeView.measuredHeight <= this.measuredHeight

        if (oldActiveItem != activeItem) {
            // Rebound to bottom of the new page
            this.smoothScrollTo(0, Math.max(activeView.bottom - this.measuredHeight, activeView.top))
            onPageChangeListeners.forEach {
                it.onPageChange(this, activeItem)
            }

        } else if (flingDisable || scrollY < activeView.top) {
            // rebound top of this page if current scroll position is not having active view using the whole screen
            // rebound to the top of this page if current scroll position is not at top position
            this.smoothScrollTo(0, activeView.top)
        }

    }

    protected open fun reboundAfterMoveDownward() {
        val oldActiveItem = activeItem

        if (llMain == null || llMain?.childCount == 0) {
            Log.i(TAG, "reboundAfterMoveDownward(): llMain == null ? ${llMain == null}")
            return
        }

        if (activeItem < llMain!!.childCount - 1) {
            val minScrollYToTriggerPageDown =
                Math.min(
                    llMain!!.getChildAt(activeItem + 1).measuredHeight,
                    this.measuredHeight) *
                        (pageChangeThreshold / 100f) + llMain!!.getChildAt(activeItem).bottom

            if (scrollY + this.measuredHeight - dy > minScrollYToTriggerPageDown) {
                Log.i(TAG, "Downward(), distance enough()")
                if (activeItem + 1 < llMain!!.childCount) {
                    activeItem++
                }
            }
        }

        val activeView = llMain!!.getChildAt(activeItem)

        flingDisable = activeView.measuredHeight <= this.measuredHeight

        if (oldActiveItem != activeItem) {
            // Rebound to top of the new page
            this.smoothScrollTo(0, Math.min(activeView.bottom - this.measuredHeight, activeView.top))
            onPageChangeListeners.forEach {
                it.onPageChange(this, activeItem)
            }

        } else if (flingDisable || scrollY + this.measuredHeight > activeView.bottom){
            // Rebound to bottom of the old page
            this.smoothScrollTo(0, Math.max(activeView.bottom - this.measuredHeight, activeView.top))
        }

    }

    override fun computeScroll() {
        if (scroller!!.computeScrollOffset()) {
            val activeView = llMain!!.getChildAt(activeItem)

            when {
                scroller!!.currY < activeView.top -> {
                    // stop at top and don't allow over scroll to other view
                    scroller!!.forceFinished(true)
                    scrollTo(0, activeView.top)

                }
                scroller!!.currY > activeView.bottom - this.measuredHeight -> {
                    // stop at bottom and don't allow over scroll to other view
                    scroller!!.forceFinished(true)
                    scrollTo(0, activeView.bottom - this.measuredHeight)

                }
                else -> {
                    // Normal situation
                    scrollTo(scroller!!.currX, scroller!!.currY)
                    invalidate()
                }
            }
        } else super.computeScroll()
    }

    /**
     * Gesture Handler
     */
    override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float,
                         velocityY: Float): Boolean {
        Log.d(TAG, "onFling()")
        if (flingDisable)
            return false

        val scrollDistanceY = velocityY * FLING_DURATION_MILLISEC / 1000

        if ( activeItem - 1 >= 0 &&
            scrollY <= llMain!!.getChildAt(activeItem).top
            && scrollY - scrollDistanceY < (llMain!!.getChildAt(activeItem - 1).measuredHeight * (100 - pageChangeThreshold) / 100f) + llMain!!.getChildAt(activeItem - 1).top) {
            //if the scroll position is up one page
            // Filp up page
            activeItem -= 1
            flingDisable = llMain!!.getChildAt(activeItem).measuredHeight <= this.measuredHeight
            this.smoothScrollTo(0, Math.max(llMain!!.getChildAt(activeItem).top, llMain!!.getChildAt(activeItem).bottom - this.measuredHeight))

            onPageChangeListeners.forEach {
                it.onPageChange(this, activeItem)
            }

        } else if (activeItem + 1 < llMain!!.childCount &&
            scrollY >= llMain!!.getChildAt(activeItem).bottom - this.measuredHeight &&
            scrollY - scrollDistanceY > (llMain!!.getChildAt(activeItem + 1).measuredHeight * pageChangeThreshold / 100f) + llMain!!.getChildAt(activeItem).bottom - this.measuredHeight){
            // Filp down page
            activeItem += 1
            flingDisable = llMain!!.getChildAt(activeItem).measuredHeight <= this.measuredHeight
            this.smoothScrollTo(0, llMain!!.getChildAt(activeItem).top)

            onPageChangeListeners.forEach {
                it.onPageChange(this, activeItem)
            }

        } else {
            // Fling within the page
            val maxY = llMain!!.getChildAt(llMain!!.childCount - 1).bottom
            scroller!!.fling(scrollX, scrollY, 0, -velocityY.toInt(), 0, 0, 0, maxY)
            invalidate()
        }
        return true
    }

    override fun onDown(e: MotionEvent?): Boolean {
        if (scroller!!.computeScrollOffset()) {
            // Stop flinging immediate after touch
            scroller!!.forceFinished(true)
            this.scrollTo(scroller!!.currX, scroller!!.currY)
        }
        return false
    }

    override fun onLongPress(e: MotionEvent?) {}

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float,
                          distanceY: Float): Boolean {
        //Pass to scroll view to handle
        return false
    }

    override fun onShowPress(e: MotionEvent?) {}

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        //Pass to scroll view to handle
        return false
    }

    open fun addOnPageChangeListener(listener: OnPageChangeListener) {
        if (!onPageChangeListeners.contains(listener)) {
            onPageChangeListeners.add(listener)
        }
    }

    open fun removeOnPageChangeListener(listener: OnPageChangeListener) {
        onPageChangeListeners.remove(listener)
    }



    private class ReboundScrollViewSavedState: BaseSavedState {

        var activeItem = 0
        var flingDisable = true

        constructor(state: Parcelable?) : super(state)

        private constructor(parcel: Parcel?): super(parcel){
            activeItem = parcel?.readInt() ?: activeItem
            flingDisable = parcel?.readByte()?.compareTo(1) == 0
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(activeItem)
            out.writeByte(if (flingDisable) 1 else 0)
        }

        companion object CREATOR : Parcelable.Creator<ReboundScrollViewSavedState> {
            override fun createFromParcel(`in`: Parcel): ReboundScrollViewSavedState {
                return ReboundScrollViewSavedState(`in`)
            }

            override fun newArray(size: Int): Array<ReboundScrollViewSavedState?> {
                return arrayOfNulls(size)
            }
        }
    }


    companion object {
        private const val TAG = "ReboundScrollView"
        private const val FLING_DURATION_MILLISEC = 275
        private const val MIN_SCROLL_TRIGGER = 20
    }
}