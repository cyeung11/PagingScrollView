package com.jkjk.pagingscrollview

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Scroller

open class PagingScrollView(

    context: Context?,
    attrs: AttributeSet?,
    defStyleAttr: Int

) : ScrollView(context, attrs, defStyleAttr), GestureDetector.OnGestureListener {

    var pageChangeThreshold = 20

    protected var gestureDetector: GestureDetector

    protected var scroller: Scroller

    var page = 0
        private set

    private var isInitiation = true
    private var flingDisable = true

    private var llMain: LinearLayout? = null
        set(value) {
            field = value
            page = 0
        }

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
                R.styleable.PagingScrollView,
                0, 0).also {

                try {
                    pageChangeThreshold = it?.getInt(R.styleable.PagingScrollView_page_change_threshold, 20) ?:20
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
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        val view = if (childCount > 0) getChildAt(0) else null

        if (view != null) {
            if (view is LinearLayout) {
                llMain = view
            } else {
                throw IllegalArgumentException("The view under PagingScrollView can only be a LinearLayout")
            }
        }
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        super.addView(child, index, params)
        if (llMain == null) {
            if (child is LinearLayout) {
                llMain = child
            } else {
                throw IllegalArgumentException("The view under PagingScrollView can only be a LinearLayout")
            }
        }
    }

    override fun addViewInLayout(child: View?, index: Int, params: ViewGroup.LayoutParams?, preventRequestLayout: Boolean): Boolean {
        val result = super.addViewInLayout(child, index, params, preventRequestLayout)
        if (llMain == null) {
            if (child is LinearLayout) {
                llMain = child
            } else {
                throw IllegalArgumentException("The view under PagingScrollView can only be a LinearLayout")
            }
        }
        return result
    }

    override fun removeViews(start: Int, count: Int) {
        if (start == 0 && count > 0) {
            llMain = null
        }
        super.removeViews(start, count)
    }

    override fun removeViewsInLayout(start: Int, count: Int) {
        if (start == 0 && count > 0) {
            llMain = null
        }
        super.removeViewsInLayout(start, count)
    }
    
    override fun removeViewAt(index: Int) {
        if (index == 0) {
            llMain = null
        }
        super.removeViewAt(index)
    }
    
    override fun removeAllViews() {
        llMain = null
        super.removeAllViews()
    }

    override fun removeView(view: View?) {
        if (view == llMain) {
            llMain = null
        }
        super.removeView(view)
    }

    override fun removeAllViewsInLayout() {
        llMain = null
        super.removeAllViewsInLayout()
    }

    override fun onSaveInstanceState(): Parcelable? {
        val parcelable = super.onSaveInstanceState()
        val result = PagingScrollViewSavedState(parcelable)
        result.flingDisable = flingDisable
        result.activeItem = page
        return result
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val pagingScrollViewSavedState = state as? PagingScrollViewSavedState
        super.onRestoreInstanceState(pagingScrollViewSavedState?.superState)
        page = pagingScrollViewSavedState?.activeItem ?: page
        flingDisable = pagingScrollViewSavedState?.flingDisable ?: flingDisable
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (scroller.computeScrollOffset()) {
            // stop the fling
            scroller.forceFinished(true)
            this.scrollTo(scroller.currX, scroller.currY)

            // intercept down action when flinging to avoid unexpected click
            if (ev?.action == MotionEvent.ACTION_DOWN)
                return true
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        var returnValue = false

        if (ev != null) {
            // intercept fling gesture
            returnValue = gestureDetector.onTouchEvent(ev)
        }

        when (ev?.action) {
            MotionEvent.ACTION_MOVE ->
                if (isInitiation) {
                    y1 = ev.y
                    isInitiation = false
                }
            MotionEvent.ACTION_UP -> {
                if (!scroller.computeScrollOffset()) {
                    y2 = ev.y
                    dy = y2 - y1

                    isInitiation = true

                    if (dy > MIN_SCROLL_TRIGGER) {
                        // scrolling down
                        Log.v(TAG, "onTouch(): scrolling up")
                        reboundAfterMoveUpward()
                        returnValue = true
                    } else if (dy < -MIN_SCROLL_TRIGGER) {
                        // scrolling up
                        Log.v(TAG, "onTouch(): scrolling down")
                        reboundAfterMoveDownward()
                        returnValue = true
                    }

                }
            }
        }

        return if (returnValue) {
            true
        } else {
            super.onTouchEvent(ev)
        }
    }

    protected open fun reboundAfterMoveUpward() {
        val oldActiveItem = page

        if (llMain == null || llMain?.childCount == 0) {
            Log.v(TAG, "reboundAfterMoveUpward(): llMain == null ? ${llMain == null}")
            return
        }

        if (page > 0) {
            // calculate the threshold to trigger change of page
            val minScrollYToTriggerPageUp =
                llMain!!.getChildAt(page).top - (Math.min(
                    llMain!!.getChildAt(page - 1).measuredHeight,
                    this.measuredHeight) *
                        (pageChangeThreshold / 100f))

            Log.v(TAG, "Upward(), y2:$y2, y1:$y1, dy:$dy, minFactor:$minScrollYToTriggerPageUp")
            if (scrollY - dy < minScrollYToTriggerPageUp) {
                Log.v(TAG, "Upward, distance enough()")
                if (page - 1 >= 0) {
                    page--
                }
            }
        }

        val activeView = llMain!!.getChildAt(page)

        // Fling is not available if the active view is the same size or smaller than screen
        flingDisable = activeView.measuredHeight <= this.measuredHeight

        if (oldActiveItem != page) {
            // Rebound to bottom of the new page
            this.smoothScrollTo(0, Math.max(activeView.bottom - this.measuredHeight, activeView.top))
            onPageChangeListeners.forEach {
                it.onPageChange(this, page)
            }

        } else if (flingDisable || scrollY < activeView.top) {
            // rebound top of this page if current scroll position is not having active view using the whole screen
            // rebound to the top of this page if current scroll position is not at top position
            this.smoothScrollTo(0, activeView.top)
        }

    }

    protected open fun reboundAfterMoveDownward() {
        val oldActiveItem = page

        if (llMain == null || llMain?.childCount == 0) {
            Log.v(TAG, "reboundAfterMoveDownward(): llMain == null ? ${llMain == null}")
            return
        }

        if (page < llMain!!.childCount - 1) {
            val minScrollYToTriggerPageDown =
                Math.min(
                    llMain!!.getChildAt(page + 1).measuredHeight,
                    this.measuredHeight) *
                        (pageChangeThreshold / 100f) + llMain!!.getChildAt(page).bottom

            if (scrollY + this.measuredHeight - dy > minScrollYToTriggerPageDown) {
                Log.v(TAG, "Downward(), distance enough()")
                if (page + 1 < llMain!!.childCount) {
                    page++
                }
            }
        }

        val activeView = llMain!!.getChildAt(page)

        flingDisable = activeView.measuredHeight <= this.measuredHeight

        if (oldActiveItem != page) {
            // Rebound to top of the new page
            this.smoothScrollTo(0, Math.min(activeView.bottom - this.measuredHeight, activeView.top))
            onPageChangeListeners.forEach {
                it.onPageChange(this, page)
            }

        } else if (flingDisable || scrollY + this.measuredHeight > activeView.bottom){
            // Rebound to bottom of the old page
            this.smoothScrollTo(0, Math.max(activeView.bottom - this.measuredHeight, activeView.top))
        }

    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val activeView = llMain!!.getChildAt(page)

            when {
                scroller.currY < activeView.top -> {
                    // stop at top and don't allow over scroll to other view
                    scroller.forceFinished(true)
                    scrollTo(0, activeView.top)

                }
                scroller.currY > activeView.bottom - this.measuredHeight -> {
                    // stop at bottom and don't allow over scroll to other view
                    scroller.forceFinished(true)
                    scrollTo(0, activeView.bottom - this.measuredHeight)

                }
                else -> {
                    // Normal situation
                    scrollTo(scroller.currX, scroller.currY)
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
        Log.v(TAG, "onFling()")
        if (flingDisable)
            return false

        val scrollDistanceY = velocityY * FLING_DURATION_MILLISEC / 1000

        if ( page - 1 >= 0 &&
            scrollY <= llMain!!.getChildAt(page).top
            && scrollY - scrollDistanceY < (llMain!!.getChildAt(page - 1).measuredHeight * (100 - pageChangeThreshold) / 100f) + llMain!!.getChildAt(page - 1).top) {
            //if the scroll position is up one page
            // Filp up page
            page -= 1
            flingDisable = llMain!!.getChildAt(page).measuredHeight <= this.measuredHeight
            this.smoothScrollTo(0, Math.max(llMain!!.getChildAt(page).top, llMain!!.getChildAt(page).bottom - this.measuredHeight))

            onPageChangeListeners.forEach {
                it.onPageChange(this, page)
            }

        } else if (page + 1 < llMain!!.childCount &&
            scrollY >= llMain!!.getChildAt(page).bottom - this.measuredHeight &&
            scrollY - scrollDistanceY > (llMain!!.getChildAt(page + 1).measuredHeight * pageChangeThreshold / 100f) + llMain!!.getChildAt(page).bottom - this.measuredHeight){
            // Filp down page
            page += 1
            flingDisable = llMain!!.getChildAt(page).measuredHeight <= this.measuredHeight
            this.smoothScrollTo(0, llMain!!.getChildAt(page).top)

            onPageChangeListeners.forEach {
                it.onPageChange(this, page)
            }

        } else {
            // Fling within the page
            val maxY = llMain!!.getChildAt(llMain!!.childCount - 1).bottom
            scroller.fling(scrollX, scrollY, 0, -velocityY.toInt(), 0, 0, 0, maxY)
            invalidate()
        }
        return true
    }

    override fun onDown(e: MotionEvent?): Boolean {
        // Stop flinging immediate after touch
        stopFling()
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

    open fun moveToPage(page: Int) {
        if (llMain != null && page in llMain!!.childCount.downTo(0) && this.page != page) {
            stopFling()

            this.page = page

            val activeView = llMain!!.getChildAt(this.page)

            // Rebound to top of the new page
            this.smoothScrollTo(0, activeView.top)
            onPageChangeListeners.forEach {
                it.onPageChange(this, this.page)
            }
        }
    }

    fun goToNextPage() {
        if (llMain != null && (page + 1) in llMain!!.childCount.downTo(0)) {
            moveToPage(page + 1)
        }
    }

    fun goToLastPage() {
        if (llMain != null && (page - 1) in llMain!!.childCount.downTo(0)) {
            moveToPage(page - 1)
        }
    }

    protected fun stopFling() {
        if (scroller.computeScrollOffset()) {
            scroller.forceFinished(true)
            this.scrollTo(scroller.currX, scroller.currY)
        }
    }

    protected open class PagingScrollViewSavedState: BaseSavedState {

        var activeItem = 0
        var flingDisable = true

        constructor(state: Parcelable?) : super(state)

        protected constructor(parcel: Parcel?): super(parcel){
            activeItem = parcel?.readInt() ?: activeItem
            flingDisable = parcel?.readByte()?.compareTo(1) == 0
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(activeItem)
            out.writeByte(if (flingDisable) 1 else 0)
        }

        companion object CREATOR : Parcelable.Creator<PagingScrollViewSavedState> {
            override fun createFromParcel(`in`: Parcel): PagingScrollViewSavedState {
                return PagingScrollViewSavedState(`in`)
            }

            override fun newArray(size: Int): Array<PagingScrollViewSavedState?> {
                return arrayOfNulls(size)
            }
        }
    }


    companion object {
        private const val TAG = "PagingScrollView"
        private const val FLING_DURATION_MILLISEC = 275
        private const val MIN_SCROLL_TRIGGER = 20
    }
}