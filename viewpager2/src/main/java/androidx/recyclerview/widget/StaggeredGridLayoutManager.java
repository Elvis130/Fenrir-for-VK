/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.recyclerview.widget;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.view.ViewCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

/**
 * A LayoutManager that lays out children in a staggered grid formation.
 * It supports horizontal & vertical layout as well as an ability to layout children in reverse.
 * <p>
 * Staggered grids are likely to have gaps at the edges of the layout. To avoid these gaps,
 * StaggeredGridLayoutManager can offset spans independently or move items between spans. You can
 * control this behavior via {@link #setGapStrategy(int)}.
 */
public class StaggeredGridLayoutManager extends RecyclerView.LayoutManager implements
        RecyclerView.SmoothScroller.ScrollVectorProvider {

    public static final int HORIZONTAL = RecyclerView.HORIZONTAL;
    public static final int VERTICAL = RecyclerView.VERTICAL;
    /**
     * Does not do anything to hide gaps.
     */
    public static final int GAP_HANDLING_NONE = 0;
    /**
     * @deprecated No longer supported.
     */
    @SuppressWarnings("unused")
    @Deprecated
    public static final int GAP_HANDLING_LAZY = 1;
    /**
     * When scroll state is changed to {@link RecyclerView#SCROLL_STATE_IDLE}, StaggeredGrid will
     * check if there are gaps in the because of full span items. If it finds, it will re-layout
     * and move items to correct positions with animations.
     * <p>
     * For example, if LayoutManager ends up with the following layout due to adapter changes:
     * <pre>
     * AAA
     * _BC
     * DDD
     * </pre>
     * <p>
     * It will animate to the following state:
     * <pre>
     * AAA
     * BC_
     * DDD
     * </pre>
     */
    public static final int GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS = 2;
    static final boolean DEBUG = false;
    static final int INVALID_OFFSET = Integer.MIN_VALUE;
    private static final String TAG = "StaggeredGridLManager";
    /**
     * While trying to find next view to focus, LayoutManager will not try to scroll more
     * than this factor times the total space of the list. If layout is vertical, total space is the
     * height minus padding, if layout is horizontal, total space is the width minus padding.
     */
    private static final float MAX_SCROLL_FACTOR = 1 / 3f;
    /**
     * Keeps the mapping between the adapter positions and spans. This is necessary to provide
     * a consistent experience when user scrolls the list.
     */
    final LazySpanLookup mLazySpanLookup = new LazySpanLookup();
    @NonNull
    private final LayoutState mLayoutState;
    /**
     * Re-used rectangle to get child decor offsets.
     */
    private final Rect mTmpRect = new Rect();
    /**
     * Re-used anchor info.
     */
    private final AnchorInfo mAnchorInfo = new AnchorInfo();
    /**
     * Works the same way as {@link android.widget.AbsListView#setSmoothScrollbarEnabled(boolean)}.
     * see {@link android.widget.AbsListView#setSmoothScrollbarEnabled(boolean)}
     */
    private final boolean mSmoothScrollbarEnabled = true;
    Span[] mSpans;
    /**
     * Primary orientation is the layout's orientation, secondary orientation is the orientation
     * for spans. Having both makes code much cleaner for calculations.
     */
    @NonNull
    OrientationHelper mPrimaryOrientation;
    @NonNull
    OrientationHelper mSecondaryOrientation;
    boolean mReverseLayout;
    /**
     * Aggregated reverse layout value that takes RTL into account.
     */
    boolean mShouldReverseLayout;
    /**
     * When LayoutManager needs to scroll to a position, it sets this variable and requests a
     * layout which will check this variable and re-layout accordingly.
     */
    int mPendingScrollPosition = RecyclerView.NO_POSITION;
    /**
     * Used to keep the offset value when {@link #scrollToPositionWithOffset(int, int)} is
     * called.
     */
    int mPendingScrollPositionOffset = INVALID_OFFSET;
    /**
     * Number of spans
     */
    private int mSpanCount = -1;
    private int mOrientation;
    /**
     * The width or height per span, depending on the orientation.
     */
    private int mSizePerSpan;
    /**
     * Temporary variable used during fill method to check which spans needs to be filled.
     */
    private BitSet mRemainingSpans;
    /**
     * how we handle gaps in UI.
     */
    private int mGapStrategy = GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS;
    /**
     * Saved state needs this information to properly layout on restore.
     */
    private boolean mLastLayoutFromEnd;
    /**
     * Saved state and onLayout needs this information to re-layout properly
     */
    private boolean mLastLayoutRTL;
    /**
     * SavedState is not handled until a layout happens. This is where we keep it until next
     * layout.
     */
    private StaggeredGridLayoutManager_SavedState mPendingSavedState;
    /**
     * Re-used measurement specs. updated by onLayout.
     */
    private int mFullSizeSpec;
    /**
     * If a full span item is invalid / or created in reverse direction; it may create gaps in
     * the UI. While laying out, if such case is detected, we set this flag.
     * <p>
     * After scrolling stops, we check this flag and if it is set, re-layout.
     */
    private boolean mLaidOutInvalidFullSpan;
    private final Runnable mCheckForGapsRunnable = this::checkForGaps;
    /**
     * Temporary array used (solely in {@link #collectAdjacentPrefetchPositions}) for stashing and
     * sorting distances to views being prefetched.
     */
    private int[] mPrefetchDistances;

    /**
     * Constructor used when layout manager is set in XML by RecyclerView attribute
     * "layoutManager". Defaults to single column and vertical.
     */
    @SuppressWarnings("unused")
    public StaggeredGridLayoutManager(@NonNull Context context, AttributeSet attrs, int defStyleAttr,
                                      int defStyleRes) {
        Properties properties = getProperties(context, attrs, defStyleAttr, defStyleRes);
        setOrientation(properties.orientation);
        setSpanCount(properties.spanCount);
        setReverseLayout(properties.reverseLayout);
        mLayoutState = new LayoutState();
        createOrientationHelpers();
    }

    /**
     * Creates a StaggeredGridLayoutManager with given parameters.
     *
     * @param spanCount   If orientation is vertical, spanCount is number of columns. If
     *                    orientation is horizontal, spanCount is number of rows.
     * @param orientation {@link #VERTICAL} or {@link #HORIZONTAL}
     */
    public StaggeredGridLayoutManager(int spanCount, int orientation) {
        mOrientation = orientation;
        setSpanCount(spanCount);
        mLayoutState = new LayoutState();
        createOrientationHelpers();
    }

    @Override
    public boolean isAutoMeasureEnabled() {
        return mGapStrategy != GAP_HANDLING_NONE;
    }

    private void createOrientationHelpers() {
        mPrimaryOrientation = OrientationHelper.createOrientationHelper(this, mOrientation);
        mSecondaryOrientation = OrientationHelper
                .createOrientationHelper(this, 1 - mOrientation);
    }

    /**
     * Checks for gaps in the UI that may be caused by adapter changes.
     * <p>
     * When a full span item is laid out in reverse direction, it sets a flag which we check when
     * scroll is stopped (or re-layout happens) and re-layout after first valid item.
     */
    boolean checkForGaps() {
        if (getChildCount() == 0 || mGapStrategy == GAP_HANDLING_NONE || !isAttachedToWindow()) {
            return false;
        }
        int minPos, maxPos;
        if (mShouldReverseLayout) {
            minPos = getLastChildPosition();
            maxPos = getFirstChildPosition();
        } else {
            minPos = getFirstChildPosition();
            maxPos = getLastChildPosition();
        }
        if (minPos == 0) {
            View gapView = hasGapsToFix();
            if (gapView != null) {
                mLazySpanLookup.clear();
                requestSimpleAnimationsInNextLayout();
                requestLayout();
                return true;
            }
        }
        if (!mLaidOutInvalidFullSpan) {
            return false;
        }
        int invalidGapDir = mShouldReverseLayout ? LayoutState.LAYOUT_START : LayoutState.LAYOUT_END;
        FullSpanItem invalidFsi = mLazySpanLookup
                .getFirstFullSpanItemInRange(minPos, maxPos + 1, invalidGapDir, true);
        if (invalidFsi == null) {
            mLaidOutInvalidFullSpan = false;
            mLazySpanLookup.forceInvalidateAfter(maxPos + 1);
            return false;
        }
        FullSpanItem validFsi = mLazySpanLookup
                .getFirstFullSpanItemInRange(minPos, invalidFsi.getMPosition(),
                        invalidGapDir * -1, true);
        if (validFsi == null) {
            mLazySpanLookup.forceInvalidateAfter(invalidFsi.getMPosition());
        } else {
            mLazySpanLookup.forceInvalidateAfter(validFsi.getMPosition() + 1);
        }
        requestSimpleAnimationsInNextLayout();
        requestLayout();
        return true;
    }

    @Override
    public void onScrollStateChanged(int state) {
        if (state == RecyclerView.SCROLL_STATE_IDLE) {
            checkForGaps();
        }
    }

    @Override
    public void onDetachedFromWindow(@NonNull RecyclerView view, @NonNull RecyclerView.Recycler recycler) {
        super.onDetachedFromWindow(view, recycler);

        removeCallbacks(mCheckForGapsRunnable);
        for (int i = 0; i < mSpanCount; i++) {
            mSpans[i].clear();
        }
        // SGLM will require fresh layout call to recover state after detach
        view.requestLayout();
    }

    /**
     * Checks for gaps if we've reached to the top of the list.
     * <p>
     * Intermediate gaps created by full span items are tracked via mLaidOutInvalidFullSpan field.
     */
    View hasGapsToFix() {
        int startChildIndex = 0;
        int endChildIndex = getChildCount() - 1;
        BitSet mSpansToCheck = new BitSet(mSpanCount);
        mSpansToCheck.set(0, mSpanCount, true);

        int firstChildIndex, childLimit;
        int preferredSpanDir = mOrientation == VERTICAL && isLayoutRTL() ? 1 : -1;

        if (mShouldReverseLayout) {
            firstChildIndex = endChildIndex;
            childLimit = startChildIndex - 1;
        } else {
            firstChildIndex = startChildIndex;
            childLimit = endChildIndex + 1;
        }
        int nextChildDiff = firstChildIndex < childLimit ? 1 : -1;
        for (int i = firstChildIndex; i != childLimit; i += nextChildDiff) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (mSpansToCheck.get(lp.mSpan.mIndex)) {
                if (checkSpanForGap(lp.mSpan)) {
                    return child;
                }
                mSpansToCheck.clear(lp.mSpan.mIndex);
            }
            if (lp.mFullSpan) {
                continue; // quick reject
            }

            if (i + nextChildDiff != childLimit) {
                View nextChild = getChildAt(i + nextChildDiff);
                boolean compareSpans = false;
                if (mShouldReverseLayout) {
                    // ensure child's end is below nextChild's end
                    int myEnd = mPrimaryOrientation.getDecoratedEnd(child);
                    int nextEnd = mPrimaryOrientation.getDecoratedEnd(nextChild);
                    if (myEnd < nextEnd) {
                        return child; //i should have a better position
                    } else if (myEnd == nextEnd) {
                        compareSpans = true;
                    }
                } else {
                    int myStart = mPrimaryOrientation.getDecoratedStart(child);
                    int nextStart = mPrimaryOrientation.getDecoratedStart(nextChild);
                    if (myStart > nextStart) {
                        return child; //i should have a better position
                    } else if (myStart == nextStart) {
                        compareSpans = true;
                    }
                }
                if (compareSpans) {
                    // equal, check span indices.
                    LayoutParams nextLp = (LayoutParams) nextChild.getLayoutParams();
                    if (lp.mSpan.mIndex - nextLp.mSpan.mIndex < 0 != preferredSpanDir < 0) {
                        return child;
                    }
                }
            }
        }
        // everything looks good
        return null;
    }

    private boolean checkSpanForGap(Span span) {
        if (mShouldReverseLayout) {
            if (span.getEndLine() < mPrimaryOrientation.getEndAfterPadding()) {
                // if it is full span, it is OK
                View endView = span.mViews.get(span.mViews.size() - 1);
                LayoutParams lp = span.getLayoutParams(endView);
                return !lp.mFullSpan;
            }
        } else if (span.getStartLine() > mPrimaryOrientation.getStartAfterPadding()) {
            // if it is full span, it is OK
            View startView = span.mViews.get(0);
            LayoutParams lp = span.getLayoutParams(startView);
            return !lp.mFullSpan;
        }
        return false;
    }

    /**
     * Returns the current gap handling strategy for StaggeredGridLayoutManager.
     * <p>
     * Staggered grid may have gaps in the layout due to changes in the adapter. To avoid gaps,
     * StaggeredGridLayoutManager provides 2 options. Check {@link #GAP_HANDLING_NONE} and
     * {@link #GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS} for details.
     * <p>
     * By default, StaggeredGridLayoutManager uses {@link #GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS}.
     *
     * @return Current gap handling strategy.
     * @see #setGapStrategy(int)
     * @see #GAP_HANDLING_NONE
     * @see #GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
     */
    public int getGapStrategy() {
        return mGapStrategy;
    }

    /**
     * Sets the gap handling strategy for StaggeredGridLayoutManager. If the gapStrategy parameter
     * is different than the current strategy, calling this method will trigger a layout request.
     *
     * @param gapStrategy The new gap handling strategy. Should be
     *                    {@link #GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS} or {@link
     *                    #GAP_HANDLING_NONE}.
     * @see #getGapStrategy()
     */
    public void setGapStrategy(int gapStrategy) {
        assertNotInLayoutOrScroll(null);
        if (gapStrategy == mGapStrategy) {
            return;
        }
        if (gapStrategy != GAP_HANDLING_NONE
                && gapStrategy != GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS) {
            throw new IllegalArgumentException("invalid gap strategy. Must be GAP_HANDLING_NONE "
                    + "or GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS");
        }
        mGapStrategy = gapStrategy;
        requestLayout();
    }

    @Override
    public void assertNotInLayoutOrScroll(String message) {
        if (mPendingSavedState == null) {
            super.assertNotInLayoutOrScroll(message);
        }
    }

    /**
     * Returns the number of spans laid out by StaggeredGridLayoutManager.
     *
     * @return Number of spans in the layout
     */
    public int getSpanCount() {
        return mSpanCount;
    }

    /**
     * Sets the number of spans for the layout. This will invalidate all of the span assignments
     * for Views.
     * <p>
     * Calling this method will automatically result in a new layout request unless the spanCount
     * parameter is equal to current span count.
     *
     * @param spanCount Number of spans to layout
     */
    public void setSpanCount(int spanCount) {
        assertNotInLayoutOrScroll(null);
        if (spanCount != mSpanCount) {
            invalidateSpanAssignments();
            mSpanCount = spanCount;
            mRemainingSpans = new BitSet(mSpanCount);
            mSpans = new Span[mSpanCount];
            for (int i = 0; i < mSpanCount; i++) {
                mSpans[i] = new Span(i);
            }
            requestLayout();
        }
    }

    /**
     * For consistency, StaggeredGridLayoutManager keeps a mapping between spans and items.
     * <p>
     * If you need to cancel current assignments, you can call this method which will clear all
     * assignments and request a new layout.
     */
    public void invalidateSpanAssignments() {
        mLazySpanLookup.clear();
        requestLayout();
    }

    /**
     * Calculates the views' layout order. (e.g. from end to start or start to end)
     * RTL layout support is applied automatically. So if layout is RTL and
     * {@link #getReverseLayout()} is {@code true}, elements will be laid out starting from left.
     */
    private void resolveShouldLayoutReverse() {
        // A == B is the same result, but we rather keep it readable
        if (mOrientation == VERTICAL || !isLayoutRTL()) {
            mShouldReverseLayout = mReverseLayout;
        } else {
            mShouldReverseLayout = !mReverseLayout;
        }
    }

    boolean isLayoutRTL() {
        return getLayoutDirection() == ViewCompat.LAYOUT_DIRECTION_RTL;
    }

    /**
     * Returns whether views are laid out in reverse order or not.
     * <p>
     * Not that this value is not affected by RecyclerView's layout direction.
     *
     * @return True if layout is reversed, false otherwise
     * @see #setReverseLayout(boolean)
     */
    public boolean getReverseLayout() {
        return mReverseLayout;
    }

    /**
     * Sets whether LayoutManager should start laying out items from the end of the UI. The order
     * items are traversed is not affected by this call.
     * <p>
     * For vertical layout, if it is set to <code>true</code>, first item will be at the bottom of
     * the list.
     * <p>
     * For horizontal layouts, it depends on the layout direction.
     * When set to true, If {@link RecyclerView} is LTR, than it will layout from RTL, if
     * {@link RecyclerView}} is RTL, it will layout from LTR.
     *
     * @param reverseLayout Whether layout should be in reverse or not
     */
    public void setReverseLayout(boolean reverseLayout) {
        assertNotInLayoutOrScroll(null);
        if (mPendingSavedState != null && mPendingSavedState.getMReverseLayout() != reverseLayout) {
            mPendingSavedState.setMReverseLayout(reverseLayout);
        }
        mReverseLayout = reverseLayout;
        requestLayout();
    }

    @Override
    public void setMeasuredDimension(Rect childrenBounds, int wSpec, int hSpec) {
        // we don't like it to wrap content in our non-scroll direction.
        int width, height;
        int horizontalPadding = getPaddingLeft() + getPaddingRight();
        int verticalPadding = getPaddingTop() + getPaddingBottom();
        if (mOrientation == VERTICAL) {
            int usedHeight = childrenBounds.height() + verticalPadding;
            height = chooseSize(hSpec, usedHeight, getMinimumHeight());
            width = chooseSize(wSpec, mSizePerSpan * mSpanCount + horizontalPadding,
                    getMinimumWidth());
        } else {
            int usedWidth = childrenBounds.width() + horizontalPadding;
            width = chooseSize(wSpec, usedWidth, getMinimumWidth());
            height = chooseSize(hSpec, mSizePerSpan * mSpanCount + verticalPadding,
                    getMinimumHeight());
        }
        setMeasuredDimension(width, height);
    }

    @Override
    public void onLayoutChildren(@NonNull RecyclerView.Recycler recycler, @NonNull RecyclerView.State state) {
        onLayoutChildren(recycler, state, true);
    }

    @Override
    public void onAdapterChanged(@Nullable RecyclerView.Adapter oldAdapter,
                                 @Nullable RecyclerView.Adapter newAdapter) {
        // RV will remove all views so we should clear all spans and assignments of views into spans
        mLazySpanLookup.clear();
        for (int i = 0; i < mSpanCount; i++) {
            mSpans[i].clear();
        }
    }

    private void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state,
                                  boolean shouldCheckForGaps) {
        AnchorInfo anchorInfo = mAnchorInfo;
        if (mPendingSavedState != null || mPendingScrollPosition != RecyclerView.NO_POSITION) {
            if (state.getItemCount() == 0) {
                removeAndRecycleAllViews(recycler);
                anchorInfo.reset();
                return;
            }
        }

        boolean recalculateAnchor = !anchorInfo.mValid || mPendingScrollPosition != RecyclerView.NO_POSITION
                || mPendingSavedState != null;
        if (recalculateAnchor) {
            anchorInfo.reset();
            if (mPendingSavedState != null) {
                applyPendingSavedState(anchorInfo);
            } else {
                resolveShouldLayoutReverse();
                anchorInfo.mLayoutFromEnd = mShouldReverseLayout;
            }
            updateAnchorInfoForLayout(state, anchorInfo);
            anchorInfo.mValid = true;
        }
        if (mPendingSavedState == null && mPendingScrollPosition == RecyclerView.NO_POSITION) {
            if (anchorInfo.mLayoutFromEnd != mLastLayoutFromEnd
                    || isLayoutRTL() != mLastLayoutRTL) {
                mLazySpanLookup.clear();
                anchorInfo.mInvalidateOffsets = true;
            }
        }

        if (getChildCount() > 0 && (mPendingSavedState == null
                || mPendingSavedState.getMSpanOffsetsSize() < 1)) {
            if (anchorInfo.mInvalidateOffsets) {
                for (int i = 0; i < mSpanCount; i++) {
                    // Scroll to position is set, clear.
                    mSpans[i].clear();
                    if (anchorInfo.mOffset != INVALID_OFFSET) {
                        mSpans[i].setLine(anchorInfo.mOffset);
                    }
                }
            } else {
                if (recalculateAnchor || mAnchorInfo.mSpanReferenceLines == null) {
                    for (int i = 0; i < mSpanCount; i++) {
                        mSpans[i].cacheReferenceLineAndClear(mShouldReverseLayout,
                                anchorInfo.mOffset);
                    }
                    mAnchorInfo.saveSpanReferenceLines(mSpans);
                } else {
                    for (int i = 0; i < mSpanCount; i++) {
                        Span span = mSpans[i];
                        span.clear();
                        span.setLine(mAnchorInfo.mSpanReferenceLines[i]);
                    }
                }
            }
        }
        detachAndScrapAttachedViews(recycler);
        mLayoutState.mRecycle = false;
        mLaidOutInvalidFullSpan = false;
        updateMeasureSpecs(mSecondaryOrientation.getTotalSpace());
        updateLayoutState(anchorInfo.mPosition, state);
        if (anchorInfo.mLayoutFromEnd) {
            // Layout start.
            setLayoutStateDirection(LayoutState.LAYOUT_START);
            fill(recycler, mLayoutState, state);
            // Layout end.
            setLayoutStateDirection(LayoutState.LAYOUT_END);
        } else {
            // Layout end.
            setLayoutStateDirection(LayoutState.LAYOUT_END);
            fill(recycler, mLayoutState, state);
            // Layout start.
            setLayoutStateDirection(LayoutState.LAYOUT_START);
        }
        mLayoutState.mCurrentPosition = anchorInfo.mPosition + mLayoutState.mItemDirection;
        fill(recycler, mLayoutState, state);

        repositionToWrapContentIfNecessary();

        if (getChildCount() > 0) {
            if (mShouldReverseLayout) {
                fixEndGap(recycler, state, true);
                fixStartGap(recycler, state, false);
            } else {
                fixStartGap(recycler, state, true);
                fixEndGap(recycler, state, false);
            }
        }
        boolean hasGaps = false;
        if (shouldCheckForGaps && !state.isPreLayout()) {
            boolean needToCheckForGaps = mGapStrategy != GAP_HANDLING_NONE
                    && getChildCount() > 0
                    && (mLaidOutInvalidFullSpan || hasGapsToFix() != null);
            if (needToCheckForGaps) {
                removeCallbacks(mCheckForGapsRunnable);
                if (checkForGaps()) {
                    hasGaps = true;
                }
            }
        }
        if (state.isPreLayout()) {
            mAnchorInfo.reset();
        }
        mLastLayoutFromEnd = anchorInfo.mLayoutFromEnd;
        mLastLayoutRTL = isLayoutRTL();
        if (hasGaps) {
            mAnchorInfo.reset();
            onLayoutChildren(recycler, state, false);
        }
    }

    @Override
    public void onLayoutCompleted(@NonNull RecyclerView.State state) {
        super.onLayoutCompleted(state);
        mPendingScrollPosition = RecyclerView.NO_POSITION;
        mPendingScrollPositionOffset = INVALID_OFFSET;
        mPendingSavedState = null; // we don't need this anymore
        mAnchorInfo.reset();
    }

    private void repositionToWrapContentIfNecessary() {
        if (mSecondaryOrientation.getMode() == View.MeasureSpec.EXACTLY) {
            return; // nothing to do
        }
        float maxSize = 0;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            float size = mSecondaryOrientation.getDecoratedMeasurement(child);
            if (size < maxSize) {
                continue;
            }
            LayoutParams layoutParams = (LayoutParams) child.getLayoutParams();
            if (layoutParams.isFullSpan()) {
                size = size / mSpanCount;
            }
            maxSize = Math.max(maxSize, size);
        }
        int before = mSizePerSpan;
        int desired = Math.round(maxSize * mSpanCount);
        if (mSecondaryOrientation.getMode() == View.MeasureSpec.AT_MOST) {
            desired = Math.min(desired, mSecondaryOrientation.getTotalSpace());
        }
        updateMeasureSpecs(desired);
        if (mSizePerSpan == before) {
            return; // nothing has changed
        }
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp.mFullSpan) {
                continue;
            }
            if (isLayoutRTL() && mOrientation == VERTICAL) {
                int newOffset = -(mSpanCount - 1 - lp.mSpan.mIndex) * mSizePerSpan;
                int prevOffset = -(mSpanCount - 1 - lp.mSpan.mIndex) * before;
                child.offsetLeftAndRight(newOffset - prevOffset);
            } else {
                int newOffset = lp.mSpan.mIndex * mSizePerSpan;
                int prevOffset = lp.mSpan.mIndex * before;
                if (mOrientation == VERTICAL) {
                    child.offsetLeftAndRight(newOffset - prevOffset);
                } else {
                    child.offsetTopAndBottom(newOffset - prevOffset);
                }
            }
        }
    }

    private void applyPendingSavedState(AnchorInfo anchorInfo) {
        if (DEBUG) {
            Log.d(TAG, "found saved state: " + mPendingSavedState);
        }
        if (mPendingSavedState.getMSpanOffsetsSize() > 0) {
            if (mPendingSavedState.getMSpanOffsetsSize() == mSpanCount) {
                for (int i = 0; i < mSpanCount; i++) {
                    mSpans[i].clear();
                    int line = mPendingSavedState.getMSpanOffsets()[i];
                    if (line != Span.INVALID_LINE) {
                        if (mPendingSavedState.getMAnchorLayoutFromEnd()) {
                            line += mPrimaryOrientation.getEndAfterPadding();
                        } else {
                            line += mPrimaryOrientation.getStartAfterPadding();
                        }
                    }
                    mSpans[i].setLine(line);
                }
            } else {
                mPendingSavedState.invalidateSpanInfo();
                mPendingSavedState.setMAnchorPosition(mPendingSavedState.getMVisibleAnchorPosition());
            }
        }
        mLastLayoutRTL = mPendingSavedState.getMLastLayoutRTL();
        setReverseLayout(mPendingSavedState.getMReverseLayout());
        resolveShouldLayoutReverse();

        if (mPendingSavedState.getMAnchorPosition() != RecyclerView.NO_POSITION) {
            mPendingScrollPosition = mPendingSavedState.getMAnchorPosition();
            anchorInfo.mLayoutFromEnd = mPendingSavedState.getMAnchorLayoutFromEnd();
        } else {
            anchorInfo.mLayoutFromEnd = mShouldReverseLayout;
        }
        if (mPendingSavedState.getMSpanLookupSize() > 1) {
            mLazySpanLookup.mData = mPendingSavedState.getMSpanLookup();
            mLazySpanLookup.mFullSpanItems = mPendingSavedState.getMFullSpanItems();
        }
    }

    void updateAnchorInfoForLayout(RecyclerView.State state, AnchorInfo anchorInfo) {
        if (updateAnchorFromPendingData(state, anchorInfo)) {
            return;
        }
        if (updateAnchorFromChildren(state, anchorInfo)) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Deciding anchor info from fresh state");
        }
        anchorInfo.assignCoordinateFromPadding();
        anchorInfo.mPosition = 0;
    }

    private boolean updateAnchorFromChildren(RecyclerView.State state, AnchorInfo anchorInfo) {
        // We don't recycle views out of adapter order. This way, we can rely on the first or
        // last child as the anchor position.
        // Layout direction may change but we should select the child depending on the latest
        // layout direction. Otherwise, we'll choose the wrong child.
        anchorInfo.mPosition = mLastLayoutFromEnd
                ? findLastReferenceChildPosition(state.getItemCount())
                : findFirstReferenceChildPosition(state.getItemCount());
        anchorInfo.mOffset = INVALID_OFFSET;
        return true;
    }

    boolean updateAnchorFromPendingData(RecyclerView.State state, AnchorInfo anchorInfo) {
        // Validate scroll position if exists.
        if (state.isPreLayout() || mPendingScrollPosition == RecyclerView.NO_POSITION) {
            return false;
        }
        // Validate it.
        if (mPendingScrollPosition < 0 || mPendingScrollPosition >= state.getItemCount()) {
            mPendingScrollPosition = RecyclerView.NO_POSITION;
            mPendingScrollPositionOffset = INVALID_OFFSET;
            return false;
        }

        if (mPendingSavedState == null || mPendingSavedState.getMAnchorPosition() == RecyclerView.NO_POSITION
                || mPendingSavedState.getMSpanOffsetsSize() < 1) {
            // If item is visible, make it fully visible.
            View child = findViewByPosition(mPendingScrollPosition);
            if (child != null) {
                // Use regular anchor position, offset according to pending offset and target
                // child
                anchorInfo.mPosition = mShouldReverseLayout ? getLastChildPosition()
                        : getFirstChildPosition();
                if (mPendingScrollPositionOffset != INVALID_OFFSET) {
                    if (anchorInfo.mLayoutFromEnd) {
                        int target = mPrimaryOrientation.getEndAfterPadding()
                                - mPendingScrollPositionOffset;
                        anchorInfo.mOffset = target - mPrimaryOrientation.getDecoratedEnd(child);
                    } else {
                        int target = mPrimaryOrientation.getStartAfterPadding()
                                + mPendingScrollPositionOffset;
                        anchorInfo.mOffset = target - mPrimaryOrientation.getDecoratedStart(child);
                    }
                    return true;
                }

                // no offset provided. Decide according to the child location
                int childSize = mPrimaryOrientation.getDecoratedMeasurement(child);
                if (childSize > mPrimaryOrientation.getTotalSpace()) {
                    // Item does not fit. Fix depending on layout direction.
                    anchorInfo.mOffset = anchorInfo.mLayoutFromEnd
                            ? mPrimaryOrientation.getEndAfterPadding()
                            : mPrimaryOrientation.getStartAfterPadding();
                    return true;
                }

                int startGap = mPrimaryOrientation.getDecoratedStart(child)
                        - mPrimaryOrientation.getStartAfterPadding();
                if (startGap < 0) {
                    anchorInfo.mOffset = -startGap;
                    return true;
                }
                int endGap = mPrimaryOrientation.getEndAfterPadding()
                        - mPrimaryOrientation.getDecoratedEnd(child);
                if (endGap < 0) {
                    anchorInfo.mOffset = endGap;
                    return true;
                }
                // child already visible. just layout as usual
                anchorInfo.mOffset = INVALID_OFFSET;
            } else {
                // Child is not visible. Set anchor coordinate depending on in which direction
                // child will be visible.
                anchorInfo.mPosition = mPendingScrollPosition;
                if (mPendingScrollPositionOffset == INVALID_OFFSET) {
                    int position = calculateScrollDirectionForPosition(
                            anchorInfo.mPosition);
                    anchorInfo.mLayoutFromEnd = position == LayoutState.LAYOUT_END;
                    anchorInfo.assignCoordinateFromPadding();
                } else {
                    anchorInfo.assignCoordinateFromPadding(mPendingScrollPositionOffset);
                }
                anchorInfo.mInvalidateOffsets = true;
            }
        } else {
            anchorInfo.mOffset = INVALID_OFFSET;
            anchorInfo.mPosition = mPendingScrollPosition;
        }
        return true;
    }

    void updateMeasureSpecs(int totalSpace) {
        mSizePerSpan = totalSpace / mSpanCount;
        //noinspection ResourceType
        mFullSizeSpec = View.MeasureSpec.makeMeasureSpec(
                totalSpace, mSecondaryOrientation.getMode());
    }

    @Override
    public boolean supportsPredictiveItemAnimations() {
        return mPendingSavedState == null;
    }

    /**
     * Returns the adapter position of the first visible view for each span.
     * <p>
     * Note that, this value is not affected by layout orientation or item order traversal.
     * ({@link #setReverseLayout(boolean)}). Views are sorted by their positions in the adapter,
     * not in the layout.
     * <p>
     * If RecyclerView has item decorators, they will be considered in calculations as well.
     * <p>
     * StaggeredGridLayoutManager may pre-cache some views that are not necessarily visible. Those
     * views are ignored in this method.
     *
     * @param into An array to put the results into. If you don't provide any, LayoutManager will
     *             create a new one.
     * @return The adapter position of the first visible item in each span. If a span does not have
     * any items, {@link RecyclerView#NO_POSITION} is returned for that span.
     * @see #findFirstCompletelyVisibleItemPositions(int[])
     * @see #findLastVisibleItemPositions(int[])
     */
    public int[] findFirstVisibleItemPositions(int[] into) {
        if (into == null) {
            into = new int[mSpanCount];
        } else if (into.length < mSpanCount) {
            throw new IllegalArgumentException("Provided int[]'s size must be more than or equal"
                    + " to span count. Expected:" + mSpanCount + ", array size:" + into.length);
        }
        for (int i = 0; i < mSpanCount; i++) {
            into[i] = mSpans[i].findFirstVisibleItemPosition();
        }
        return into;
    }

    /**
     * Returns the adapter position of the first completely visible view for each span.
     * <p>
     * Note that, this value is not affected by layout orientation or item order traversal.
     * ({@link #setReverseLayout(boolean)}). Views are sorted by their positions in the adapter,
     * not in the layout.
     * <p>
     * If RecyclerView has item decorators, they will be considered in calculations as well.
     * <p>
     * StaggeredGridLayoutManager may pre-cache some views that are not necessarily visible. Those
     * views are ignored in this method.
     *
     * @param into An array to put the results into. If you don't provide any, LayoutManager will
     *             create a new one.
     * @return The adapter position of the first fully visible item in each span. If a span does
     * not have any items, {@link RecyclerView#NO_POSITION} is returned for that span.
     * @see #findFirstVisibleItemPositions(int[])
     * @see #findLastCompletelyVisibleItemPositions(int[])
     */
    public int[] findFirstCompletelyVisibleItemPositions(int[] into) {
        if (into == null) {
            into = new int[mSpanCount];
        } else if (into.length < mSpanCount) {
            throw new IllegalArgumentException("Provided int[]'s size must be more than or equal"
                    + " to span count. Expected:" + mSpanCount + ", array size:" + into.length);
        }
        for (int i = 0; i < mSpanCount; i++) {
            into[i] = mSpans[i].findFirstCompletelyVisibleItemPosition();
        }
        return into;
    }

    /**
     * Returns the adapter position of the last visible view for each span.
     * <p>
     * Note that, this value is not affected by layout orientation or item order traversal.
     * ({@link #setReverseLayout(boolean)}). Views are sorted by their positions in the adapter,
     * not in the layout.
     * <p>
     * If RecyclerView has item decorators, they will be considered in calculations as well.
     * <p>
     * StaggeredGridLayoutManager may pre-cache some views that are not necessarily visible. Those
     * views are ignored in this method.
     *
     * @param into An array to put the results into. If you don't provide any, LayoutManager will
     *             create a new one.
     * @return The adapter position of the last visible item in each span. If a span does not have
     * any items, {@link RecyclerView#NO_POSITION} is returned for that span.
     * @see #findLastCompletelyVisibleItemPositions(int[])
     * @see #findFirstVisibleItemPositions(int[])
     */
    public int[] findLastVisibleItemPositions(int[] into) {
        if (into == null) {
            into = new int[mSpanCount];
        } else if (into.length < mSpanCount) {
            throw new IllegalArgumentException("Provided int[]'s size must be more than or equal"
                    + " to span count. Expected:" + mSpanCount + ", array size:" + into.length);
        }
        for (int i = 0; i < mSpanCount; i++) {
            into[i] = mSpans[i].findLastVisibleItemPosition();
        }
        return into;
    }

    /**
     * Returns the adapter position of the last completely visible view for each span.
     * <p>
     * Note that, this value is not affected by layout orientation or item order traversal.
     * ({@link #setReverseLayout(boolean)}). Views are sorted by their positions in the adapter,
     * not in the layout.
     * <p>
     * If RecyclerView has item decorators, they will be considered in calculations as well.
     * <p>
     * StaggeredGridLayoutManager may pre-cache some views that are not necessarily visible. Those
     * views are ignored in this method.
     *
     * @param into An array to put the results into. If you don't provide any, LayoutManager will
     *             create a new one.
     * @return The adapter position of the last fully visible item in each span. If a span does not
     * have any items, {@link RecyclerView#NO_POSITION} is returned for that span.
     * @see #findFirstCompletelyVisibleItemPositions(int[])
     * @see #findLastVisibleItemPositions(int[])
     */
    public int[] findLastCompletelyVisibleItemPositions(int[] into) {
        if (into == null) {
            into = new int[mSpanCount];
        } else if (into.length < mSpanCount) {
            throw new IllegalArgumentException("Provided int[]'s size must be more than or equal"
                    + " to span count. Expected:" + mSpanCount + ", array size:" + into.length);
        }
        for (int i = 0; i < mSpanCount; i++) {
            into[i] = mSpans[i].findLastCompletelyVisibleItemPosition();
        }
        return into;
    }

    @Override
    public int computeHorizontalScrollOffset(@NonNull RecyclerView.State state) {
        return computeScrollOffset(state);
    }

    private int computeScrollOffset(RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }
        return ScrollbarHelper.computeScrollOffset(state, mPrimaryOrientation,
                findFirstVisibleItemClosestToStart(!mSmoothScrollbarEnabled),
                findFirstVisibleItemClosestToEnd(!mSmoothScrollbarEnabled),
                this, mSmoothScrollbarEnabled, mShouldReverseLayout);
    }

    @Override
    public int computeVerticalScrollOffset(@NonNull RecyclerView.State state) {
        return computeScrollOffset(state);
    }

    @Override
    public int computeHorizontalScrollExtent(@NonNull RecyclerView.State state) {
        return computeScrollExtent(state);
    }

    private int computeScrollExtent(RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }
        return ScrollbarHelper.computeScrollExtent(state, mPrimaryOrientation,
                findFirstVisibleItemClosestToStart(!mSmoothScrollbarEnabled),
                findFirstVisibleItemClosestToEnd(!mSmoothScrollbarEnabled),
                this, mSmoothScrollbarEnabled);
    }

    @Override
    public int computeVerticalScrollExtent(@NonNull RecyclerView.State state) {
        return computeScrollExtent(state);
    }

    @Override
    public int computeHorizontalScrollRange(@NonNull RecyclerView.State state) {
        return computeScrollRange(state);
    }

    private int computeScrollRange(RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }
        return ScrollbarHelper.computeScrollRange(state, mPrimaryOrientation,
                findFirstVisibleItemClosestToStart(!mSmoothScrollbarEnabled),
                findFirstVisibleItemClosestToEnd(!mSmoothScrollbarEnabled),
                this, mSmoothScrollbarEnabled);
    }

    @Override
    public int computeVerticalScrollRange(@NonNull RecyclerView.State state) {
        return computeScrollRange(state);
    }

    private void measureChildWithDecorationsAndMargin(View child, LayoutParams lp,
                                                      boolean alreadyMeasured) {
        if (lp.mFullSpan) {
            if (mOrientation == VERTICAL) {
                measureChildWithDecorationsAndMargin(child, mFullSizeSpec,
                        getChildMeasureSpec(
                                getHeight(),
                                getHeightMode(),
                                getPaddingTop() + getPaddingBottom(),
                                lp.height,
                                true),
                        alreadyMeasured);
            } else {
                measureChildWithDecorationsAndMargin(
                        child,
                        getChildMeasureSpec(
                                getWidth(),
                                getWidthMode(),
                                getPaddingLeft() + getPaddingRight(),
                                lp.width,
                                true),
                        mFullSizeSpec,
                        alreadyMeasured);
            }
        } else {
            if (mOrientation == VERTICAL) {
                // Padding for width measure spec is 0 because left and right padding were already
                // factored into mSizePerSpan.
                measureChildWithDecorationsAndMargin(
                        child,
                        getChildMeasureSpec(
                                mSizePerSpan,
                                getWidthMode(),
                                0,
                                lp.width,
                                false),
                        getChildMeasureSpec(
                                getHeight(),
                                getHeightMode(),
                                getPaddingTop() + getPaddingBottom(),
                                lp.height,
                                true),
                        alreadyMeasured);
            } else {
                // Padding for height measure spec is 0 because top and bottom padding were already
                // factored into mSizePerSpan.
                measureChildWithDecorationsAndMargin(
                        child,
                        getChildMeasureSpec(
                                getWidth(),
                                getWidthMode(),
                                getPaddingLeft() + getPaddingRight(),
                                lp.width,
                                true),
                        getChildMeasureSpec(
                                mSizePerSpan,
                                getHeightMode(),
                                0,
                                lp.height,
                                false),
                        alreadyMeasured);
            }
        }
    }

    private void measureChildWithDecorationsAndMargin(View child, int widthSpec,
                                                      int heightSpec, boolean alreadyMeasured) {
        calculateItemDecorationsForChild(child, mTmpRect);
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        widthSpec = updateSpecWithExtra(widthSpec, lp.leftMargin + mTmpRect.left,
                lp.rightMargin + mTmpRect.right);
        heightSpec = updateSpecWithExtra(heightSpec, lp.topMargin + mTmpRect.top,
                lp.bottomMargin + mTmpRect.bottom);
        boolean measure = alreadyMeasured
                ? shouldReMeasureChild(child, widthSpec, heightSpec, lp)
                : shouldMeasureChild(child, widthSpec, heightSpec, lp);
        if (measure) {
            child.measure(widthSpec, heightSpec);
        }

    }

    private int updateSpecWithExtra(int spec, int startInset, int endInset) {
        if (startInset == 0 && endInset == 0) {
            return spec;
        }
        int mode = View.MeasureSpec.getMode(spec);
        if (mode == View.MeasureSpec.AT_MOST || mode == View.MeasureSpec.EXACTLY) {
            return View.MeasureSpec.makeMeasureSpec(
                    Math.max(0, View.MeasureSpec.getSize(spec) - startInset - endInset), mode);
        }
        return spec;
    }

    @Override
    public void onRestoreInstanceState(@NonNull Parcelable state) {
        if (state instanceof StaggeredGridLayoutManager_SavedState) {
            mPendingSavedState = (StaggeredGridLayoutManager_SavedState) state;
            if (mPendingScrollPosition != RecyclerView.NO_POSITION) {
                mPendingSavedState.invalidateAnchorPositionInfo();
                mPendingSavedState.invalidateSpanInfo();
            }
            requestLayout();
        } else if (DEBUG) {
            Log.d(TAG, "invalid saved state class");
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        if (mPendingSavedState != null) {
            return new StaggeredGridLayoutManager_SavedState(mPendingSavedState);
        }
        StaggeredGridLayoutManager_SavedState state = new StaggeredGridLayoutManager_SavedState();
        state.setMReverseLayout(mReverseLayout);
        state.setMAnchorLayoutFromEnd(mLastLayoutFromEnd);
        state.setMLastLayoutRTL(mLastLayoutRTL);

        if (mLazySpanLookup != null && mLazySpanLookup.mData != null) {
            state.setMSpanLookup(mLazySpanLookup.mData);
            state.setMSpanLookupSize(state.getMSpanLookup().length);
            state.setMFullSpanItems(mLazySpanLookup.mFullSpanItems);
        } else {
            state.setMSpanLookupSize(0);
        }

        if (getChildCount() > 0) {
            state.setMAnchorPosition(mLastLayoutFromEnd ? getLastChildPosition() : getFirstChildPosition());
            state.setMVisibleAnchorPosition(findFirstVisibleItemPositionInt());
            state.setMSpanOffsetsSize(mSpanCount);
            state.setMSpanOffsets(new int[mSpanCount]);
            for (int i = 0; i < mSpanCount; i++) {
                int line;
                if (mLastLayoutFromEnd) {
                    line = mSpans[i].getEndLine(Span.INVALID_LINE);
                    if (line != Span.INVALID_LINE) {
                        line -= mPrimaryOrientation.getEndAfterPadding();
                    }
                } else {
                    line = mSpans[i].getStartLine(Span.INVALID_LINE);
                    if (line != Span.INVALID_LINE) {
                        line -= mPrimaryOrientation.getStartAfterPadding();
                    }
                }
                state.getMSpanOffsets()[i] = line;
            }
        } else {
            state.setMAnchorPosition(RecyclerView.NO_POSITION);
            state.setMVisibleAnchorPosition(RecyclerView.NO_POSITION);
            state.setMSpanOffsetsSize(0);
        }
        if (DEBUG) {
            Log.d(TAG, "saved state:\n" + state);
        }
        return state;
    }

    @Override
    public void onInitializeAccessibilityEvent(@NonNull AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        if (getChildCount() > 0) {
            View start = findFirstVisibleItemClosestToStart(false);
            View end = findFirstVisibleItemClosestToEnd(false);
            if (start == null || end == null) {
                return;
            }
            int startPos = getPosition(start);
            int endPos = getPosition(end);
            if (startPos < endPos) {
                event.setFromIndex(startPos);
                event.setToIndex(endPos);
            } else {
                event.setFromIndex(endPos);
                event.setToIndex(startPos);
            }
        }
    }

    /**
     * Finds the first fully visible child to be used as an anchor child if span count changes when
     * state is restored. If no children is fully visible, returns a partially visible child instead
     * of returning null.
     */
    int findFirstVisibleItemPositionInt() {
        View first = mShouldReverseLayout ? findFirstVisibleItemClosestToEnd(true) :
                findFirstVisibleItemClosestToStart(true);
        return first == null ? RecyclerView.NO_POSITION : getPosition(first);
    }

    /**
     * This is for internal use. Not necessarily the child closest to start but the first child
     * we find that matches the criteria.
     * This method does not do any sorting based on child's start coordinate, instead, it uses
     * children order.
     */
    View findFirstVisibleItemClosestToStart(boolean fullyVisible) {
        int boundsStart = mPrimaryOrientation.getStartAfterPadding();
        int boundsEnd = mPrimaryOrientation.getEndAfterPadding();
        int limit = getChildCount();
        View partiallyVisible = null;
        for (int i = 0; i < limit; i++) {
            View child = getChildAt(i);
            int childStart = mPrimaryOrientation.getDecoratedStart(child);
            int childEnd = mPrimaryOrientation.getDecoratedEnd(child);
            if (childEnd <= boundsStart || childStart >= boundsEnd) {
                continue; // not visible at all
            }
            if (childStart >= boundsStart || !fullyVisible) {
                // when checking for start, it is enough even if part of the child's top is visible
                // as long as fully visible is not requested.
                return child;
            }
            if (partiallyVisible == null) {
                partiallyVisible = child;
            }
        }
        return partiallyVisible;
    }

    /**
     * This is for internal use. Not necessarily the child closest to bottom but the first child
     * we find that matches the criteria.
     * This method does not do any sorting based on child's end coordinate, instead, it uses
     * children order.
     */
    View findFirstVisibleItemClosestToEnd(boolean fullyVisible) {
        int boundsStart = mPrimaryOrientation.getStartAfterPadding();
        int boundsEnd = mPrimaryOrientation.getEndAfterPadding();
        View partiallyVisible = null;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            int childStart = mPrimaryOrientation.getDecoratedStart(child);
            int childEnd = mPrimaryOrientation.getDecoratedEnd(child);
            if (childEnd <= boundsStart || childStart >= boundsEnd) {
                continue; // not visible at all
            }
            if (childEnd <= boundsEnd || !fullyVisible) {
                // when checking for end, it is enough even if part of the child's bottom is visible
                // as long as fully visible is not requested.
                return child;
            }
            if (partiallyVisible == null) {
                partiallyVisible = child;
            }
        }
        return partiallyVisible;
    }

    private void fixEndGap(RecyclerView.Recycler recycler, RecyclerView.State state,
                           boolean canOffsetChildren) {
        int maxEndLine = getMaxEnd(Integer.MIN_VALUE);
        if (maxEndLine == Integer.MIN_VALUE) {
            return;
        }
        int gap = mPrimaryOrientation.getEndAfterPadding() - maxEndLine;
        int fixOffset;
        if (gap > 0) {
            fixOffset = -scrollBy(-gap, recycler, state);
        } else {
            return; // nothing to fix
        }
        gap -= fixOffset;
        if (canOffsetChildren && gap > 0) {
            mPrimaryOrientation.offsetChildren(gap);
        }
    }

    private void fixStartGap(RecyclerView.Recycler recycler, RecyclerView.State state,
                             boolean canOffsetChildren) {
        int minStartLine = getMinStart(Integer.MAX_VALUE);
        if (minStartLine == Integer.MAX_VALUE) {
            return;
        }
        int gap = minStartLine - mPrimaryOrientation.getStartAfterPadding();
        int fixOffset;
        if (gap > 0) {
            fixOffset = scrollBy(gap, recycler, state);
        } else {
            return; // nothing to fix
        }
        gap -= fixOffset;
        if (canOffsetChildren && gap > 0) {
            mPrimaryOrientation.offsetChildren(-gap);
        }
    }

    private void updateLayoutState(int anchorPosition, RecyclerView.State state) {
        mLayoutState.mAvailable = 0;
        mLayoutState.mCurrentPosition = anchorPosition;
        int startExtra = 0;
        int endExtra = 0;
        if (isSmoothScrolling()) {
            int targetPos = state.getTargetScrollPosition();
            if (targetPos != RecyclerView.NO_POSITION) {
                if (mShouldReverseLayout == targetPos < anchorPosition) {
                    endExtra = mPrimaryOrientation.getTotalSpace();
                } else {
                    startExtra = mPrimaryOrientation.getTotalSpace();
                }
            }
        }

        // Line of the furthest row.
        boolean clipToPadding = getClipToPadding();
        if (clipToPadding) {
            mLayoutState.mStartLine = mPrimaryOrientation.getStartAfterPadding() - startExtra;
            mLayoutState.mEndLine = mPrimaryOrientation.getEndAfterPadding() + endExtra;
        } else {
            mLayoutState.mEndLine = mPrimaryOrientation.getEnd() + endExtra;
            mLayoutState.mStartLine = -startExtra;
        }
        mLayoutState.mStopInFocusable = false;
        mLayoutState.mRecycle = true;
        mLayoutState.mInfinite = mPrimaryOrientation.getMode() == View.MeasureSpec.UNSPECIFIED
                && mPrimaryOrientation.getEnd() == 0;
    }

    private void setLayoutStateDirection(int direction) {
        mLayoutState.mLayoutDirection = direction;
        mLayoutState.mItemDirection = (mShouldReverseLayout == (direction == LayoutState.LAYOUT_START))
                ? LayoutState.ITEM_DIRECTION_TAIL : LayoutState.ITEM_DIRECTION_HEAD;
    }

    @Override
    public void offsetChildrenHorizontal(int dx) {
        super.offsetChildrenHorizontal(dx);
        for (int i = 0; i < mSpanCount; i++) {
            mSpans[i].onOffset(dx);
        }
    }

    @Override
    public void offsetChildrenVertical(int dy) {
        super.offsetChildrenVertical(dy);
        for (int i = 0; i < mSpanCount; i++) {
            mSpans[i].onOffset(dy);
        }
    }

    @Override
    public void onItemsRemoved(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        handleUpdate(positionStart, itemCount, AdapterHelper.UpdateOp.REMOVE);
    }

    @Override
    public void onItemsAdded(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        handleUpdate(positionStart, itemCount, AdapterHelper.UpdateOp.ADD);
    }

    @Override
    public void onItemsChanged(@NonNull RecyclerView recyclerView) {
        mLazySpanLookup.clear();
        requestLayout();
    }

    @Override
    public void onItemsMoved(@NonNull RecyclerView recyclerView, int from, int to, int itemCount) {
        handleUpdate(from, to, AdapterHelper.UpdateOp.MOVE);
    }

    @Override
    public void onItemsUpdated(@NonNull RecyclerView recyclerView, int positionStart, int itemCount,
                               Object payload) {
        handleUpdate(positionStart, itemCount, AdapterHelper.UpdateOp.UPDATE);
    }

    /**
     * Checks whether it should invalidate span assignments in response to an adapter change.
     */
    private void handleUpdate(int positionStart, int itemCountOrToPosition, int cmd) {
        int minPosition = mShouldReverseLayout ? getLastChildPosition() : getFirstChildPosition();
        int affectedRangeEnd; // exclusive
        int affectedRangeStart; // inclusive

        if (cmd == AdapterHelper.UpdateOp.MOVE) {
            if (positionStart < itemCountOrToPosition) {
                affectedRangeEnd = itemCountOrToPosition + 1;
                affectedRangeStart = positionStart;
            } else {
                affectedRangeEnd = positionStart + 1;
                affectedRangeStart = itemCountOrToPosition;
            }
        } else {
            affectedRangeStart = positionStart;
            affectedRangeEnd = positionStart + itemCountOrToPosition;
        }

        mLazySpanLookup.invalidateAfter(affectedRangeStart);
        switch (cmd) {
            case AdapterHelper.UpdateOp.ADD:
                mLazySpanLookup.offsetForAddition(positionStart, itemCountOrToPosition);
                break;
            case AdapterHelper.UpdateOp.REMOVE:
                mLazySpanLookup.offsetForRemoval(positionStart, itemCountOrToPosition);
                break;
            case AdapterHelper.UpdateOp.MOVE:
                // TODO optimize
                mLazySpanLookup.offsetForRemoval(positionStart, 1);
                mLazySpanLookup.offsetForAddition(itemCountOrToPosition, 1);
                break;
        }

        if (affectedRangeEnd <= minPosition) {
            return;
        }

        int maxPosition = mShouldReverseLayout ? getFirstChildPosition() : getLastChildPosition();
        if (affectedRangeStart <= maxPosition) {
            requestLayout();
        }
    }

    private int fill(RecyclerView.Recycler recycler, LayoutState layoutState,
                     RecyclerView.State state) {
        mRemainingSpans.set(0, mSpanCount, true);
        // The target position we are trying to reach.
        int targetLine;

        // Line of the furthest row.
        if (mLayoutState.mInfinite) {
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
                targetLine = Integer.MAX_VALUE;
            } else { // LAYOUT_START
                targetLine = Integer.MIN_VALUE;
            }
        } else {
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
                targetLine = layoutState.mEndLine + layoutState.mAvailable;
            } else { // LAYOUT_START
                targetLine = layoutState.mStartLine - layoutState.mAvailable;
            }
        }

        updateAllRemainingSpans(layoutState.mLayoutDirection, targetLine);
        if (DEBUG) {
            Log.d(TAG, "FILLING targetLine: " + targetLine + ","
                    + "remaining spans:" + mRemainingSpans + ", state: " + layoutState);
        }

        // the default coordinate to add new view.
        int defaultNewViewLine = mShouldReverseLayout
                ? mPrimaryOrientation.getEndAfterPadding()
                : mPrimaryOrientation.getStartAfterPadding();
        boolean added = false;
        while (layoutState.hasMore(state)
                && (mLayoutState.mInfinite || !mRemainingSpans.isEmpty())) {
            View view = layoutState.next(recycler);
            LayoutParams lp = ((LayoutParams) view.getLayoutParams());
            int position = lp.getViewLayoutPosition();
            int spanIndex = mLazySpanLookup.getSpan(position);
            Span currentSpan;
            boolean assignSpan = spanIndex == LayoutParams.INVALID_SPAN_ID;
            if (assignSpan) {
                currentSpan = lp.mFullSpan ? mSpans[0] : getNextSpan(layoutState);
                mLazySpanLookup.setSpan(position, currentSpan);
                if (DEBUG) {
                    Log.d(TAG, "assigned " + currentSpan.mIndex + " for " + position);
                }
            } else {
                if (DEBUG) {
                    Log.d(TAG, "using " + spanIndex + " for pos " + position);
                }
                currentSpan = mSpans[spanIndex];
            }
            // assign span before measuring so that item decorators can get updated span index
            lp.mSpan = currentSpan;
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
                addView(view);
            } else {
                addView(view, 0);
            }
            measureChildWithDecorationsAndMargin(view, lp, false);

            int start;
            int end;
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
                start = lp.mFullSpan ? getMaxEnd(defaultNewViewLine)
                        : currentSpan.getEndLine(defaultNewViewLine);
                end = start + mPrimaryOrientation.getDecoratedMeasurement(view);
                if (assignSpan && lp.mFullSpan) {
                    FullSpanItem fullSpanItem;
                    fullSpanItem = createFullSpanItemFromEnd(start);
                    fullSpanItem.setMGapDir(LayoutState.LAYOUT_START);
                    fullSpanItem.setMPosition(position);
                    mLazySpanLookup.addFullSpanItem(fullSpanItem);
                }
            } else {
                end = lp.mFullSpan ? getMinStart(defaultNewViewLine)
                        : currentSpan.getStartLine(defaultNewViewLine);
                start = end - mPrimaryOrientation.getDecoratedMeasurement(view);
                if (assignSpan && lp.mFullSpan) {
                    FullSpanItem fullSpanItem;
                    fullSpanItem = createFullSpanItemFromStart(end);
                    fullSpanItem.setMGapDir(LayoutState.LAYOUT_END);
                    fullSpanItem.setMPosition(position);
                    mLazySpanLookup.addFullSpanItem(fullSpanItem);
                }
            }

            // check if this item may create gaps in the future
            if (lp.mFullSpan && layoutState.mItemDirection == LayoutState.ITEM_DIRECTION_HEAD) {
                if (assignSpan) {
                    mLaidOutInvalidFullSpan = true;
                } else {
                    boolean hasInvalidGap;
                    if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
                        hasInvalidGap = !areAllEndsEqual();
                    } else { // layoutState.mLayoutDirection == LAYOUT_START
                        hasInvalidGap = !areAllStartsEqual();
                    }
                    if (hasInvalidGap) {
                        FullSpanItem fullSpanItem = mLazySpanLookup
                                .getFullSpanItem(position);
                        if (fullSpanItem != null) {
                            fullSpanItem.setMHasUnwantedGapAfter(true);
                        }
                        mLaidOutInvalidFullSpan = true;
                    }
                }
            }
            attachViewToSpans(view, lp, layoutState);
            int otherStart;
            int otherEnd;
            if (isLayoutRTL() && mOrientation == VERTICAL) {
                otherEnd = lp.mFullSpan ? mSecondaryOrientation.getEndAfterPadding() :
                        mSecondaryOrientation.getEndAfterPadding()
                                - (mSpanCount - 1 - currentSpan.mIndex) * mSizePerSpan;
                otherStart = otherEnd - mSecondaryOrientation.getDecoratedMeasurement(view);
            } else {
                otherStart = lp.mFullSpan ? mSecondaryOrientation.getStartAfterPadding()
                        : currentSpan.mIndex * mSizePerSpan
                        + mSecondaryOrientation.getStartAfterPadding();
                otherEnd = otherStart + mSecondaryOrientation.getDecoratedMeasurement(view);
            }

            if (mOrientation == VERTICAL) {
                layoutDecoratedWithMargins(view, otherStart, start, otherEnd, end);
            } else {
                layoutDecoratedWithMargins(view, start, otherStart, end, otherEnd);
            }

            if (lp.mFullSpan) {
                updateAllRemainingSpans(mLayoutState.mLayoutDirection, targetLine);
            } else {
                updateRemainingSpans(currentSpan, mLayoutState.mLayoutDirection, targetLine);
            }
            recycle(recycler, mLayoutState);
            if (mLayoutState.mStopInFocusable && view.hasFocusable()) {
                if (lp.mFullSpan) {
                    mRemainingSpans.clear();
                } else {
                    mRemainingSpans.set(currentSpan.mIndex, false);
                }
            }
            added = true;
        }
        if (!added) {
            recycle(recycler, mLayoutState);
        }
        int diff;
        if (mLayoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
            int minStart = getMinStart(mPrimaryOrientation.getStartAfterPadding());
            diff = mPrimaryOrientation.getStartAfterPadding() - minStart;
        } else {
            int maxEnd = getMaxEnd(mPrimaryOrientation.getEndAfterPadding());
            diff = maxEnd - mPrimaryOrientation.getEndAfterPadding();
        }
        return diff > 0 ? Math.min(layoutState.mAvailable, diff) : 0;
    }

    private FullSpanItem createFullSpanItemFromEnd(int newItemTop) {
        FullSpanItem fsi = new FullSpanItem();
        fsi.setMGapPerSpan(new int[mSpanCount]);
        for (int i = 0; i < mSpanCount; i++) {
            fsi.getMGapPerSpan()[i] = newItemTop - mSpans[i].getEndLine(newItemTop);
        }
        return fsi;
    }

    private FullSpanItem createFullSpanItemFromStart(int newItemBottom) {
        FullSpanItem fsi = new FullSpanItem();
        fsi.setMGapPerSpan(new int[mSpanCount]);
        for (int i = 0; i < mSpanCount; i++) {
            fsi.getMGapPerSpan()[i] = mSpans[i].getStartLine(newItemBottom) - newItemBottom;
        }
        return fsi;
    }

    private void attachViewToSpans(View view, LayoutParams lp, LayoutState layoutState) {
        if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
            if (lp.mFullSpan) {
                appendViewToAllSpans(view);
            } else {
                lp.mSpan.appendToSpan(view);
            }
        } else {
            if (lp.mFullSpan) {
                prependViewToAllSpans(view);
            } else {
                lp.mSpan.prependToSpan(view);
            }
        }
    }

    private void recycle(RecyclerView.Recycler recycler, LayoutState layoutState) {
        if (!layoutState.mRecycle || layoutState.mInfinite) {
            return;
        }
        if (layoutState.mAvailable == 0) {
            // easy, recycle line is still valid
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
                recycleFromEnd(recycler, layoutState.mEndLine);
            } else {
                recycleFromStart(recycler, layoutState.mStartLine);
            }
        } else {
            // scrolling case, recycle line can be shifted by how much space we could cover
            // by adding new views
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
                // calculate recycle line
                int scrolled = layoutState.mStartLine - getMaxStart(layoutState.mStartLine);
                int line;
                if (scrolled < 0) {
                    line = layoutState.mEndLine;
                } else {
                    line = layoutState.mEndLine - Math.min(scrolled, layoutState.mAvailable);
                }
                recycleFromEnd(recycler, line);
            } else {
                // calculate recycle line
                int scrolled = getMinEnd(layoutState.mEndLine) - layoutState.mEndLine;
                int line;
                if (scrolled < 0) {
                    line = layoutState.mStartLine;
                } else {
                    line = layoutState.mStartLine + Math.min(scrolled, layoutState.mAvailable);
                }
                recycleFromStart(recycler, line);
            }
        }

    }

    private void appendViewToAllSpans(View view) {
        // traverse in reverse so that we end up assigning full span items to 0
        for (int i = mSpanCount - 1; i >= 0; i--) {
            mSpans[i].appendToSpan(view);
        }
    }

    private void prependViewToAllSpans(View view) {
        // traverse in reverse so that we end up assigning full span items to 0
        for (int i = mSpanCount - 1; i >= 0; i--) {
            mSpans[i].prependToSpan(view);
        }
    }

    private void updateAllRemainingSpans(int layoutDir, int targetLine) {
        for (int i = 0; i < mSpanCount; i++) {
            if (mSpans[i].mViews.isEmpty()) {
                continue;
            }
            updateRemainingSpans(mSpans[i], layoutDir, targetLine);
        }
    }

    private void updateRemainingSpans(Span span, int layoutDir, int targetLine) {
        int deletedSize = span.getDeletedSize();
        if (layoutDir == LayoutState.LAYOUT_START) {
            int line = span.getStartLine();
            if (line + deletedSize <= targetLine) {
                mRemainingSpans.set(span.mIndex, false);
            }
        } else {
            int line = span.getEndLine();
            if (line - deletedSize >= targetLine) {
                mRemainingSpans.set(span.mIndex, false);
            }
        }
    }

    private int getMaxStart(int def) {
        int maxStart = mSpans[0].getStartLine(def);
        for (int i = 1; i < mSpanCount; i++) {
            int spanStart = mSpans[i].getStartLine(def);
            if (spanStart > maxStart) {
                maxStart = spanStart;
            }
        }
        return maxStart;
    }

    private int getMinStart(int def) {
        int minStart = mSpans[0].getStartLine(def);
        for (int i = 1; i < mSpanCount; i++) {
            int spanStart = mSpans[i].getStartLine(def);
            if (spanStart < minStart) {
                minStart = spanStart;
            }
        }
        return minStart;
    }

    boolean areAllEndsEqual() {
        int end = mSpans[0].getEndLine(Span.INVALID_LINE);
        for (int i = 1; i < mSpanCount; i++) {
            if (mSpans[i].getEndLine(Span.INVALID_LINE) != end) {
                return false;
            }
        }
        return true;
    }

    boolean areAllStartsEqual() {
        int start = mSpans[0].getStartLine(Span.INVALID_LINE);
        for (int i = 1; i < mSpanCount; i++) {
            if (mSpans[i].getStartLine(Span.INVALID_LINE) != start) {
                return false;
            }
        }
        return true;
    }

    private int getMaxEnd(int def) {
        int maxEnd = mSpans[0].getEndLine(def);
        for (int i = 1; i < mSpanCount; i++) {
            int spanEnd = mSpans[i].getEndLine(def);
            if (spanEnd > maxEnd) {
                maxEnd = spanEnd;
            }
        }
        return maxEnd;
    }

    private int getMinEnd(int def) {
        int minEnd = mSpans[0].getEndLine(def);
        for (int i = 1; i < mSpanCount; i++) {
            int spanEnd = mSpans[i].getEndLine(def);
            if (spanEnd < minEnd) {
                minEnd = spanEnd;
            }
        }
        return minEnd;
    }

    private void recycleFromStart(RecyclerView.Recycler recycler, int line) {
        while (getChildCount() > 0) {
            View child = getChildAt(0);
            if (mPrimaryOrientation.getDecoratedEnd(child) <= line
                    && mPrimaryOrientation.getTransformedEndWithDecoration(child) <= line) {
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                // Don't recycle the last View in a span not to lose span's start/end lines
                if (lp.mFullSpan) {
                    for (int j = 0; j < mSpanCount; j++) {
                        if (mSpans[j].mViews.size() == 1) {
                            return;
                        }
                    }
                    for (int j = 0; j < mSpanCount; j++) {
                        mSpans[j].popStart();
                    }
                } else {
                    if (lp.mSpan.mViews.size() == 1) {
                        return;
                    }
                    lp.mSpan.popStart();
                }
                removeAndRecycleView(child, recycler);
            } else {
                return; // done
            }
        }
    }

    private void recycleFromEnd(RecyclerView.Recycler recycler, int line) {
        int childCount = getChildCount();
        int i;
        for (i = childCount - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (mPrimaryOrientation.getDecoratedStart(child) >= line
                    && mPrimaryOrientation.getTransformedStartWithDecoration(child) >= line) {
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                // Don't recycle the last View in a span not to lose span's start/end lines
                if (lp.mFullSpan) {
                    for (int j = 0; j < mSpanCount; j++) {
                        if (mSpans[j].mViews.size() == 1) {
                            return;
                        }
                    }
                    for (int j = 0; j < mSpanCount; j++) {
                        mSpans[j].popEnd();
                    }
                } else {
                    if (lp.mSpan.mViews.size() == 1) {
                        return;
                    }
                    lp.mSpan.popEnd();
                }
                removeAndRecycleView(child, recycler);
            } else {
                return; // done
            }
        }
    }

    /**
     * @return True if last span is the first one we want to fill
     */
    private boolean preferLastSpan(int layoutDir) {
        if (mOrientation == HORIZONTAL) {
            return (layoutDir == LayoutState.LAYOUT_START) != mShouldReverseLayout;
        }
        return ((layoutDir == LayoutState.LAYOUT_START) == mShouldReverseLayout) == isLayoutRTL();
    }

    /**
     * Finds the span for the next view.
     */
    private Span getNextSpan(LayoutState layoutState) {
        boolean preferLastSpan = preferLastSpan(layoutState.mLayoutDirection);
        int startIndex, endIndex, diff;
        if (preferLastSpan) {
            startIndex = mSpanCount - 1;
            endIndex = -1;
            diff = -1;
        } else {
            startIndex = 0;
            endIndex = mSpanCount;
            diff = 1;
        }
        if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
            Span min = null;
            int minLine = Integer.MAX_VALUE;
            int defaultLine = mPrimaryOrientation.getStartAfterPadding();
            for (int i = startIndex; i != endIndex; i += diff) {
                Span other = mSpans[i];
                int otherLine = other.getEndLine(defaultLine);
                if (otherLine < minLine) {
                    min = other;
                    minLine = otherLine;
                }
            }
            return min;
        } else {
            Span max = null;
            int maxLine = Integer.MIN_VALUE;
            int defaultLine = mPrimaryOrientation.getEndAfterPadding();
            for (int i = startIndex; i != endIndex; i += diff) {
                Span other = mSpans[i];
                int otherLine = other.getStartLine(defaultLine);
                if (otherLine > maxLine) {
                    max = other;
                    maxLine = otherLine;
                }
            }
            return max;
        }
    }

    @Override
    public boolean canScrollVertically() {
        return mOrientation == VERTICAL;
    }

    @Override
    public boolean canScrollHorizontally() {
        return mOrientation == HORIZONTAL;
    }

    @Override
    public int scrollHorizontallyBy(int dx, @NonNull RecyclerView.Recycler recycler,
                                    @NonNull RecyclerView.State state) {
        return scrollBy(dx, recycler, state);
    }

    @Override
    public int scrollVerticallyBy(int dy, @NonNull RecyclerView.Recycler recycler,
                                  @NonNull RecyclerView.State state) {
        return scrollBy(dy, recycler, state);
    }

    private int calculateScrollDirectionForPosition(int position) {
        if (getChildCount() == 0) {
            return mShouldReverseLayout ? LayoutState.LAYOUT_END : LayoutState.LAYOUT_START;
        }
        int firstChildPos = getFirstChildPosition();
        return position < firstChildPos != mShouldReverseLayout ? LayoutState.LAYOUT_START : LayoutState.LAYOUT_END;
    }

    @Override
    public PointF computeScrollVectorForPosition(int targetPosition) {
        int direction = calculateScrollDirectionForPosition(targetPosition);
        PointF outVector = new PointF();
        if (direction == 0) {
            return null;
        }
        if (mOrientation == HORIZONTAL) {
            outVector.x = direction;
            outVector.y = 0;
        } else {
            outVector.x = 0;
            outVector.y = direction;
        }
        return outVector;
    }

    @Override
    public void smoothScrollToPosition(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.State state,
                                       int position) {
        LinearSmoothScroller scroller = new LinearSmoothScroller(recyclerView.getContext());
        scroller.setTargetPosition(position);
        startSmoothScroll(scroller);
    }

    @Override
    public void scrollToPosition(int position) {
        if (mPendingSavedState != null && mPendingSavedState.getMAnchorPosition() != position) {
            mPendingSavedState.invalidateAnchorPositionInfo();
        }
        mPendingScrollPosition = position;
        mPendingScrollPositionOffset = INVALID_OFFSET;
        requestLayout();
    }

    /**
     * Scroll to the specified adapter position with the given offset from layout start.
     * <p>
     * Note that scroll position change will not be reflected until the next layout call.
     * <p>
     * If you are just trying to make a position visible, use {@link #scrollToPosition(int)}.
     *
     * @param position Index (starting at 0) of the reference item.
     * @param offset   The distance (in pixels) between the start edge of the item view and
     *                 start edge of the RecyclerView.
     * @see #setReverseLayout(boolean)
     * @see #scrollToPosition(int)
     */
    public void scrollToPositionWithOffset(int position, int offset) {
        if (mPendingSavedState != null) {
            mPendingSavedState.invalidateAnchorPositionInfo();
        }
        mPendingScrollPosition = position;
        mPendingScrollPositionOffset = offset;
        requestLayout();
    }

    /**
     * @hide
     */
    @Override
    @RestrictTo(LIBRARY)
    public void collectAdjacentPrefetchPositions(int dx, int dy, @NonNull RecyclerView.State state,
                                                 @NonNull LayoutPrefetchRegistry layoutPrefetchRegistry) {
        /* This method uses the simplifying assumption that the next N items (where N = span count)
         * will be assigned, one-to-one, to spans, where ordering is based on which span  extends
         * least beyond the viewport.
         *
         * While this simplified model will be incorrect in some cases, it's difficult to know
         * item heights, or whether individual items will be full span prior to construction.
         *
         * While this greedy estimation approach may underestimate the distance to prefetch items,
         * it's very unlikely to overestimate them, so distances can be conservatively used to know
         * the soonest (in terms of scroll distance) a prefetched view may come on screen.
         */
        int delta = (mOrientation == HORIZONTAL) ? dx : dy;
        if (getChildCount() == 0 || delta == 0) {
            // can't support this scroll, so don't bother prefetching
            return;
        }
        prepareLayoutStateForDelta(delta, state);

        // build sorted list of distances to end of each span (though we don't care which is which)
        if (mPrefetchDistances == null || mPrefetchDistances.length < mSpanCount) {
            mPrefetchDistances = new int[mSpanCount];
        }

        int itemPrefetchCount = 0;
        for (int i = 0; i < mSpanCount; i++) {
            // compute number of pixels past the edge of the viewport that the current span extends
            int distance = mLayoutState.mItemDirection == LayoutState.LAYOUT_START
                    ? mLayoutState.mStartLine - mSpans[i].getStartLine(mLayoutState.mStartLine)
                    : mSpans[i].getEndLine(mLayoutState.mEndLine) - mLayoutState.mEndLine;
            if (distance >= 0) {
                // span extends to the edge, so prefetch next item
                mPrefetchDistances[itemPrefetchCount] = distance;
                itemPrefetchCount++;
            }
        }
        Arrays.sort(mPrefetchDistances, 0, itemPrefetchCount);

        // then assign them in order to the next N views (where N = span count)
        for (int i = 0; i < itemPrefetchCount && mLayoutState.hasMore(state); i++) {
            layoutPrefetchRegistry.addPosition(mLayoutState.mCurrentPosition,
                    mPrefetchDistances[i]);
            mLayoutState.mCurrentPosition += mLayoutState.mItemDirection;
        }
    }

    void prepareLayoutStateForDelta(int delta, RecyclerView.State state) {
        int referenceChildPosition;
        int layoutDir;
        if (delta > 0) { // layout towards end
            layoutDir = LayoutState.LAYOUT_END;
            referenceChildPosition = getLastChildPosition();
        } else {
            layoutDir = LayoutState.LAYOUT_START;
            referenceChildPosition = getFirstChildPosition();
        }
        mLayoutState.mRecycle = true;
        updateLayoutState(referenceChildPosition, state);
        setLayoutStateDirection(layoutDir);
        mLayoutState.mCurrentPosition = referenceChildPosition + mLayoutState.mItemDirection;
        mLayoutState.mAvailable = Math.abs(delta);
    }

    int scrollBy(int dt, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getChildCount() == 0 || dt == 0) {
            return 0;
        }

        prepareLayoutStateForDelta(dt, state);
        int consumed = fill(recycler, mLayoutState, state);
        int available = mLayoutState.mAvailable;
        int totalScroll;
        if (available < consumed) {
            totalScroll = dt;
        } else if (dt < 0) {
            totalScroll = -consumed;
        } else { // dt > 0
            totalScroll = consumed;
        }
        if (DEBUG) {
            Log.d(TAG, "asked " + dt + " scrolled" + totalScroll);
        }

        mPrimaryOrientation.offsetChildren(-totalScroll);
        // always reset this if we scroll for a proper save instance state
        mLastLayoutFromEnd = mShouldReverseLayout;
        mLayoutState.mAvailable = 0;
        recycle(recycler, mLayoutState);
        return totalScroll;
    }

    int getLastChildPosition() {
        int childCount = getChildCount();
        return childCount == 0 ? 0 : getPosition(getChildAt(childCount - 1));
    }

    int getFirstChildPosition() {
        int childCount = getChildCount();
        return childCount == 0 ? 0 : getPosition(getChildAt(0));
    }

    /**
     * Finds the first View that can be used as an anchor View.
     *
     * @return Position of the View or 0 if it cannot find any such View.
     */
    private int findFirstReferenceChildPosition(int itemCount) {
        int limit = getChildCount();
        for (int i = 0; i < limit; i++) {
            View view = getChildAt(i);
            int position = getPosition(view);
            if (position >= 0 && position < itemCount) {
                return position;
            }
        }
        return 0;
    }

    /**
     * Finds the last View that can be used as an anchor View.
     *
     * @return Position of the View or 0 if it cannot find any such View.
     */
    private int findLastReferenceChildPosition(int itemCount) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View view = getChildAt(i);
            int position = getPosition(view);
            if (position >= 0 && position < itemCount) {
                return position;
            }
        }
        return 0;
    }

    @NonNull
    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        if (mOrientation == HORIZONTAL) {
            return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        } else {
            return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(Context c, AttributeSet attrs) {
        return new LayoutParams(c, attrs);
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            return new LayoutParams((ViewGroup.MarginLayoutParams) lp);
        } else {
            return new LayoutParams(lp);
        }
    }

    @Override
    public boolean checkLayoutParams(RecyclerView.LayoutParams lp) {
        return lp instanceof LayoutParams;
    }

    public int getOrientation() {
        return mOrientation;
    }

    /**
     * Sets the orientation of the layout. StaggeredGridLayoutManager will do its best to keep
     * scroll position if this method is called after views are laid out.
     *
     * @param orientation {@link #HORIZONTAL} or {@link #VERTICAL}
     */
    public void setOrientation(int orientation) {
        if (orientation != HORIZONTAL && orientation != VERTICAL) {
            throw new IllegalArgumentException("invalid orientation.");
        }
        assertNotInLayoutOrScroll(null);
        if (orientation == mOrientation) {
            return;
        }
        mOrientation = orientation;
        OrientationHelper tmp = mPrimaryOrientation;
        mPrimaryOrientation = mSecondaryOrientation;
        mSecondaryOrientation = tmp;
        requestLayout();
    }

    @Nullable
    @Override
    public View onFocusSearchFailed(@NonNull View focused, int direction, @NonNull RecyclerView.Recycler recycler,
                                    @NonNull RecyclerView.State state) {
        if (getChildCount() == 0) {
            return null;
        }

        View directChild = findContainingItemView(focused);
        if (directChild == null) {
            return null;
        }

        resolveShouldLayoutReverse();
        int layoutDir = convertFocusDirectionToLayoutDirection(direction);
        if (layoutDir == LayoutState.INVALID_LAYOUT) {
            return null;
        }
        LayoutParams prevFocusLayoutParams = (LayoutParams) directChild.getLayoutParams();
        boolean prevFocusFullSpan = prevFocusLayoutParams.mFullSpan;
        Span prevFocusSpan = prevFocusLayoutParams.mSpan;
        int referenceChildPosition;
        if (layoutDir == LayoutState.LAYOUT_END) { // layout towards end
            referenceChildPosition = getLastChildPosition();
        } else {
            referenceChildPosition = getFirstChildPosition();
        }
        updateLayoutState(referenceChildPosition, state);
        setLayoutStateDirection(layoutDir);

        mLayoutState.mCurrentPosition = referenceChildPosition + mLayoutState.mItemDirection;
        mLayoutState.mAvailable = (int) (MAX_SCROLL_FACTOR * mPrimaryOrientation.getTotalSpace());
        mLayoutState.mStopInFocusable = true;
        mLayoutState.mRecycle = false;
        fill(recycler, mLayoutState, state);
        mLastLayoutFromEnd = mShouldReverseLayout;
        if (!prevFocusFullSpan) {
            View view = prevFocusSpan.getFocusableViewAfter(referenceChildPosition, layoutDir);
            if (view != null && view != directChild) {
                return view;
            }
        }

        // either could not find from the desired span or prev view is full span.
        // traverse all spans
        if (preferLastSpan(layoutDir)) {
            for (int i = mSpanCount - 1; i >= 0; i--) {
                View view = mSpans[i].getFocusableViewAfter(referenceChildPosition, layoutDir);
                if (view != null && view != directChild) {
                    return view;
                }
            }
        } else {
            for (int i = 0; i < mSpanCount; i++) {
                View view = mSpans[i].getFocusableViewAfter(referenceChildPosition, layoutDir);
                if (view != null && view != directChild) {
                    return view;
                }
            }
        }

        // Could not find any focusable views from any of the existing spans. Now start the search
        // to find the best unfocusable candidate to become visible on the screen next. The search
        // is done in the same fashion: first, check the views in the desired span and if no
        // candidate is found, traverse the views in all the remaining spans.
        boolean shouldSearchFromStart = !mReverseLayout == (layoutDir == LayoutState.LAYOUT_START);
        View unfocusableCandidate;
        if (!prevFocusFullSpan) {
            unfocusableCandidate = findViewByPosition(shouldSearchFromStart
                    ? prevFocusSpan.findFirstPartiallyVisibleItemPosition() :
                    prevFocusSpan.findLastPartiallyVisibleItemPosition());
            if (unfocusableCandidate != null && unfocusableCandidate != directChild) {
                return unfocusableCandidate;
            }
        }

        if (preferLastSpan(layoutDir)) {
            for (int i = mSpanCount - 1; i >= 0; i--) {
                if (i == prevFocusSpan.mIndex) {
                    continue;
                }
                unfocusableCandidate = findViewByPosition(shouldSearchFromStart
                        ? mSpans[i].findFirstPartiallyVisibleItemPosition() :
                        mSpans[i].findLastPartiallyVisibleItemPosition());
                if (unfocusableCandidate != null && unfocusableCandidate != directChild) {
                    return unfocusableCandidate;
                }
            }
        } else {
            for (int i = 0; i < mSpanCount; i++) {
                unfocusableCandidate = findViewByPosition(shouldSearchFromStart
                        ? mSpans[i].findFirstPartiallyVisibleItemPosition() :
                        mSpans[i].findLastPartiallyVisibleItemPosition());
                if (unfocusableCandidate != null && unfocusableCandidate != directChild) {
                    return unfocusableCandidate;
                }
            }
        }
        return null;
    }

    /**
     * Converts a focusDirection to orientation.
     *
     * @param focusDirection One of {@link View#FOCUS_UP}, {@link View#FOCUS_DOWN},
     *                       {@link View#FOCUS_LEFT}, {@link View#FOCUS_RIGHT},
     *                       {@link View#FOCUS_BACKWARD}, {@link View#FOCUS_FORWARD}
     *                       or 0 for not applicable
     * @return {@link LayoutState#LAYOUT_START} or {@link LayoutState#LAYOUT_END} if focus direction
     * is applicable to current state, {@link LayoutState#INVALID_LAYOUT} otherwise.
     */
    private int convertFocusDirectionToLayoutDirection(int focusDirection) {
        switch (focusDirection) {
            case View.FOCUS_BACKWARD:
                if (mOrientation == VERTICAL) {
                    return LayoutState.LAYOUT_START;
                } else if (isLayoutRTL()) {
                    return LayoutState.LAYOUT_END;
                } else {
                    return LayoutState.LAYOUT_START;
                }
            case View.FOCUS_FORWARD:
                if (mOrientation == VERTICAL) {
                    return LayoutState.LAYOUT_END;
                } else if (isLayoutRTL()) {
                    return LayoutState.LAYOUT_START;
                } else {
                    return LayoutState.LAYOUT_END;
                }
            case View.FOCUS_UP:
                return mOrientation == VERTICAL ? LayoutState.LAYOUT_START
                        : LayoutState.INVALID_LAYOUT;
            case View.FOCUS_DOWN:
                return mOrientation == VERTICAL ? LayoutState.LAYOUT_END
                        : LayoutState.INVALID_LAYOUT;
            case View.FOCUS_LEFT:
                return mOrientation == HORIZONTAL ? LayoutState.LAYOUT_START
                        : LayoutState.INVALID_LAYOUT;
            case View.FOCUS_RIGHT:
                return mOrientation == HORIZONTAL ? LayoutState.LAYOUT_END
                        : LayoutState.INVALID_LAYOUT;
            default:
                if (DEBUG) {
                    Log.d(TAG, "Unknown focus request:" + focusDirection);
                }
                return LayoutState.INVALID_LAYOUT;
        }

    }

    /**
     * LayoutParams used by StaggeredGridLayoutManager.
     * <p>
     * Note that if the orientation is {@link #VERTICAL}, the width parameter is ignored and if the
     * orientation is {@link #HORIZONTAL} the height parameter is ignored because child view is
     * expected to fill all of the space given to it.
     */
    public static class LayoutParams extends RecyclerView.LayoutParams {

        /**
         * Span Id for Views that are not laid out yet.
         */
        public static final int INVALID_SPAN_ID = -1;

        // Package scope to be able to access from tests.
        Span mSpan;

        boolean mFullSpan;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(RecyclerView.LayoutParams source) {
            super(source);
        }

        /**
         * Returns whether this View occupies all available spans or just one.
         *
         * @return True if the View occupies all spans or false otherwise.
         * @see #setFullSpan(boolean)
         */
        public boolean isFullSpan() {
            return mFullSpan;
        }

        /**
         * When set to true, the item will layout using all span area. That means, if orientation
         * is vertical, the view will have full width; if orientation is horizontal, the view will
         * have full height.
         *
         * @param fullSpan True if this item should traverse all spans.
         * @see #isFullSpan()
         */
        public void setFullSpan(boolean fullSpan) {
            mFullSpan = fullSpan;
        }

        /**
         * Returns the Span index to which this View is assigned.
         *
         * @return The Span index of the View. If View is not yet assigned to any span, returns
         * {@link #INVALID_SPAN_ID}.
         */
        public final int getSpanIndex() {
            if (mSpan == null) {
                return INVALID_SPAN_ID;
            }
            return mSpan.mIndex;
        }
    }

    /**
     * An array of mappings from adapter position to span.
     * This only grows when a write happens and it grows up to the size of the adapter.
     */
    static class LazySpanLookup {

        private static final int MIN_SIZE = 10;
        int[] mData;
        ArrayList<FullSpanItem> mFullSpanItems;


        /**
         * Invalidates everything after this position, including full span information
         */
        int forceInvalidateAfter(int position) {
            if (mFullSpanItems != null) {
                for (int i = mFullSpanItems.size() - 1; i >= 0; i--) {
                    FullSpanItem fsi = mFullSpanItems.get(i);
                    if (fsi.getMPosition() >= position) {
                        mFullSpanItems.remove(i);
                    }
                }
            }
            return invalidateAfter(position);
        }

        /**
         * returns end position for invalidation.
         */
        int invalidateAfter(int position) {
            if (mData == null) {
                return RecyclerView.NO_POSITION;
            }
            if (position >= mData.length) {
                return RecyclerView.NO_POSITION;
            }
            int endPosition = invalidateFullSpansAfter(position);
            if (endPosition == RecyclerView.NO_POSITION) {
                Arrays.fill(mData, position, mData.length, LayoutParams.INVALID_SPAN_ID);
                return mData.length;
            } else {
                // Just invalidate items in between `position` and the next full span item, or the
                // end of the tracked spans in mData if it's not been lengthened yet.
                int invalidateToIndex = Math.min(endPosition + 1, mData.length);
                Arrays.fill(mData, position, invalidateToIndex, LayoutParams.INVALID_SPAN_ID);
                return invalidateToIndex;
            }
        }

        int getSpan(int position) {
            if (mData == null || position >= mData.length) {
                return LayoutParams.INVALID_SPAN_ID;
            } else {
                return mData[position];
            }
        }

        void setSpan(int position, Span span) {
            ensureSize(position);
            mData[position] = span.mIndex;
        }

        int sizeForPosition(int position) {
            int len = mData.length;
            while (len <= position) {
                len *= 2;
            }
            return len;
        }

        void ensureSize(int position) {
            if (mData == null) {
                mData = new int[Math.max(position, MIN_SIZE) + 1];
                Arrays.fill(mData, LayoutParams.INVALID_SPAN_ID);
            } else if (position >= mData.length) {
                int[] old = mData;
                mData = new int[sizeForPosition(position)];
                System.arraycopy(old, 0, mData, 0, old.length);
                Arrays.fill(mData, old.length, mData.length, LayoutParams.INVALID_SPAN_ID);
            }
        }

        void clear() {
            if (mData != null) {
                Arrays.fill(mData, LayoutParams.INVALID_SPAN_ID);
            }
            mFullSpanItems = null;
        }

        void offsetForRemoval(int positionStart, int itemCount) {
            if (mData == null || positionStart >= mData.length) {
                return;
            }
            ensureSize(positionStart + itemCount);
            System.arraycopy(mData, positionStart + itemCount, mData, positionStart,
                    mData.length - positionStart - itemCount);
            Arrays.fill(mData, mData.length - itemCount, mData.length,
                    LayoutParams.INVALID_SPAN_ID);
            offsetFullSpansForRemoval(positionStart, itemCount);
        }

        private void offsetFullSpansForRemoval(int positionStart, int itemCount) {
            if (mFullSpanItems == null) {
                return;
            }
            int end = positionStart + itemCount;
            for (int i = mFullSpanItems.size() - 1; i >= 0; i--) {
                FullSpanItem fsi = mFullSpanItems.get(i);
                if (fsi.getMPosition() < positionStart) {
                    continue;
                }
                if (fsi.getMPosition() < end) {
                    mFullSpanItems.remove(i);
                } else {
                    fsi.setMPosition(fsi.getMPosition() - itemCount);
                }
            }
        }

        void offsetForAddition(int positionStart, int itemCount) {
            if (mData == null || positionStart >= mData.length) {
                return;
            }
            ensureSize(positionStart + itemCount);
            System.arraycopy(mData, positionStart, mData, positionStart + itemCount,
                    mData.length - positionStart - itemCount);
            Arrays.fill(mData, positionStart, positionStart + itemCount,
                    LayoutParams.INVALID_SPAN_ID);
            offsetFullSpansForAddition(positionStart, itemCount);
        }

        private void offsetFullSpansForAddition(int positionStart, int itemCount) {
            if (mFullSpanItems == null) {
                return;
            }
            for (int i = mFullSpanItems.size() - 1; i >= 0; i--) {
                FullSpanItem fsi = mFullSpanItems.get(i);
                if (fsi.getMPosition() < positionStart) {
                    continue;
                }
                fsi.setMPosition(fsi.getMPosition() + itemCount);
            }
        }

        /**
         * Returns when invalidation should end. e.g. hitting a full span position.
         * Returned position SHOULD BE invalidated.
         */
        private int invalidateFullSpansAfter(int position) {
            if (mFullSpanItems == null) {
                return RecyclerView.NO_POSITION;
            }
            FullSpanItem item = getFullSpanItem(position);
            // if there is an fsi at this position, get rid of it.
            if (item != null) {
                mFullSpanItems.remove(item);
            }
            int nextFsiIndex = -1;
            int count = mFullSpanItems.size();
            for (int i = 0; i < count; i++) {
                FullSpanItem fsi = mFullSpanItems.get(i);
                if (fsi.getMPosition() >= position) {
                    nextFsiIndex = i;
                    break;
                }
            }
            if (nextFsiIndex != -1) {
                FullSpanItem fsi = mFullSpanItems.get(nextFsiIndex);
                mFullSpanItems.remove(nextFsiIndex);
                return fsi.getMPosition();
            }
            return RecyclerView.NO_POSITION;
        }

        public void addFullSpanItem(FullSpanItem fullSpanItem) {
            if (mFullSpanItems == null) {
                mFullSpanItems = new ArrayList<>();
            }
            int size = mFullSpanItems.size();
            for (int i = 0; i < size; i++) {
                FullSpanItem other = mFullSpanItems.get(i);
                if (other.getMPosition() == fullSpanItem.getMPosition()) {
                    if (DEBUG) {
                        throw new IllegalStateException("two fsis for same position");
                    } else {
                        mFullSpanItems.remove(i);
                    }
                }
                if (other.getMPosition() >= fullSpanItem.getMPosition()) {
                    mFullSpanItems.add(i, fullSpanItem);
                    return;
                }
            }
            // if it is not added to a position.
            mFullSpanItems.add(fullSpanItem);
        }

        public FullSpanItem getFullSpanItem(int position) {
            if (mFullSpanItems == null) {
                return null;
            }
            for (int i = mFullSpanItems.size() - 1; i >= 0; i--) {
                FullSpanItem fsi = mFullSpanItems.get(i);
                if (fsi.getMPosition() == position) {
                    return fsi;
                }
            }
            return null;
        }

        /**
         * @param minPos              inclusive
         * @param maxPos              exclusive
         * @param gapDir              if not 0, returns FSIs on in that direction
         * @param hasUnwantedGapAfter If true, when full span item has unwanted gaps, it will be
         *                            returned even if its gap direction does not match.
         */
        public FullSpanItem getFirstFullSpanItemInRange(int minPos, int maxPos, int gapDir,
                                                        boolean hasUnwantedGapAfter) {
            if (mFullSpanItems == null) {
                return null;
            }
            int limit = mFullSpanItems.size();
            for (int i = 0; i < limit; i++) {
                FullSpanItem fsi = mFullSpanItems.get(i);
                if (fsi.getMPosition() >= maxPos) {
                    return null;
                }
                if (fsi.getMPosition() >= minPos
                        && (gapDir == 0 || fsi.getMGapDir() == gapDir
                        || (hasUnwantedGapAfter && fsi.getMHasUnwantedGapAfter()))) {
                    return fsi;
                }
            }
            return null;
        }
    }

    // Package scoped to access from tests.
    class Span {

        static final int INVALID_LINE = Integer.MIN_VALUE;
        final int mIndex;
        final ArrayList<View> mViews = new ArrayList<>();
        int mCachedStart = INVALID_LINE;
        int mCachedEnd = INVALID_LINE;
        int mDeletedSize;

        Span(int index) {
            mIndex = index;
        }

        int getStartLine(int def) {
            if (mCachedStart != INVALID_LINE) {
                return mCachedStart;
            }
            if (mViews.size() == 0) {
                return def;
            }
            calculateCachedStart();
            return mCachedStart;
        }

        void calculateCachedStart() {
            View startView = mViews.get(0);
            LayoutParams lp = getLayoutParams(startView);
            mCachedStart = mPrimaryOrientation.getDecoratedStart(startView);
            if (lp.mFullSpan) {
                FullSpanItem fsi = mLazySpanLookup
                        .getFullSpanItem(lp.getViewLayoutPosition());
                if (fsi != null && fsi.getMGapDir() == LayoutState.LAYOUT_START) {
                    mCachedStart -= fsi.getGapForSpan(mIndex);
                }
            }
        }

        // Use this one when default value does not make sense and not having a value means a bug.
        int getStartLine() {
            if (mCachedStart != INVALID_LINE) {
                return mCachedStart;
            }
            calculateCachedStart();
            return mCachedStart;
        }

        int getEndLine(int def) {
            if (mCachedEnd != INVALID_LINE) {
                return mCachedEnd;
            }
            int size = mViews.size();
            if (size == 0) {
                return def;
            }
            calculateCachedEnd();
            return mCachedEnd;
        }

        void calculateCachedEnd() {
            View endView = mViews.get(mViews.size() - 1);
            LayoutParams lp = getLayoutParams(endView);
            mCachedEnd = mPrimaryOrientation.getDecoratedEnd(endView);
            if (lp.mFullSpan) {
                FullSpanItem fsi = mLazySpanLookup
                        .getFullSpanItem(lp.getViewLayoutPosition());
                if (fsi != null && fsi.getMGapDir() == LayoutState.LAYOUT_END) {
                    mCachedEnd += fsi.getGapForSpan(mIndex);
                }
            }
        }

        // Use this one when default value does not make sense and not having a value means a bug.
        int getEndLine() {
            if (mCachedEnd != INVALID_LINE) {
                return mCachedEnd;
            }
            calculateCachedEnd();
            return mCachedEnd;
        }

        void prependToSpan(View view) {
            LayoutParams lp = getLayoutParams(view);
            lp.mSpan = this;
            mViews.add(0, view);
            mCachedStart = INVALID_LINE;
            if (mViews.size() == 1) {
                mCachedEnd = INVALID_LINE;
            }
            if (lp.isItemRemoved() || lp.isItemChanged()) {
                mDeletedSize += mPrimaryOrientation.getDecoratedMeasurement(view);
            }
        }

        void appendToSpan(View view) {
            LayoutParams lp = getLayoutParams(view);
            lp.mSpan = this;
            mViews.add(view);
            mCachedEnd = INVALID_LINE;
            if (mViews.size() == 1) {
                mCachedStart = INVALID_LINE;
            }
            if (lp.isItemRemoved() || lp.isItemChanged()) {
                mDeletedSize += mPrimaryOrientation.getDecoratedMeasurement(view);
            }
        }

        // Useful method to preserve positions on a re-layout.
        void cacheReferenceLineAndClear(boolean reverseLayout, int offset) {
            int reference;
            if (reverseLayout) {
                reference = getEndLine(INVALID_LINE);
            } else {
                reference = getStartLine(INVALID_LINE);
            }
            clear();
            if (reference == INVALID_LINE) {
                return;
            }
            if (reverseLayout ? reference < mPrimaryOrientation.getEndAfterPadding() : reference > mPrimaryOrientation.getStartAfterPadding()) {
                return;
            }
            if (offset != INVALID_OFFSET) {
                reference += offset;
            }
            mCachedStart = mCachedEnd = reference;
        }

        void clear() {
            mViews.clear();
            invalidateCache();
            mDeletedSize = 0;
        }

        void invalidateCache() {
            mCachedStart = INVALID_LINE;
            mCachedEnd = INVALID_LINE;
        }

        void setLine(int line) {
            mCachedEnd = mCachedStart = line;
        }

        void popEnd() {
            int size = mViews.size();
            View end = mViews.remove(size - 1);
            LayoutParams lp = getLayoutParams(end);
            lp.mSpan = null;
            if (lp.isItemRemoved() || lp.isItemChanged()) {
                mDeletedSize -= mPrimaryOrientation.getDecoratedMeasurement(end);
            }
            if (size == 1) {
                mCachedStart = INVALID_LINE;
            }
            mCachedEnd = INVALID_LINE;
        }

        void popStart() {
            View start = mViews.remove(0);
            LayoutParams lp = getLayoutParams(start);
            lp.mSpan = null;
            if (mViews.size() == 0) {
                mCachedEnd = INVALID_LINE;
            }
            if (lp.isItemRemoved() || lp.isItemChanged()) {
                mDeletedSize -= mPrimaryOrientation.getDecoratedMeasurement(start);
            }
            mCachedStart = INVALID_LINE;
        }

        public int getDeletedSize() {
            return mDeletedSize;
        }

        LayoutParams getLayoutParams(View view) {
            return (LayoutParams) view.getLayoutParams();
        }

        void onOffset(int dt) {
            if (mCachedStart != INVALID_LINE) {
                mCachedStart += dt;
            }
            if (mCachedEnd != INVALID_LINE) {
                mCachedEnd += dt;
            }
        }

        public int findFirstVisibleItemPosition() {
            return mReverseLayout
                    ? findOneVisibleChild(mViews.size() - 1, -1, false)
                    : findOneVisibleChild(0, mViews.size(), false);
        }

        public int findFirstPartiallyVisibleItemPosition() {
            return mReverseLayout
                    ? findOnePartiallyVisibleChild(mViews.size() - 1, -1, true)
                    : findOnePartiallyVisibleChild(0, mViews.size(), true);
        }

        public int findFirstCompletelyVisibleItemPosition() {
            return mReverseLayout
                    ? findOneVisibleChild(mViews.size() - 1, -1, true)
                    : findOneVisibleChild(0, mViews.size(), true);
        }

        public int findLastVisibleItemPosition() {
            return mReverseLayout
                    ? findOneVisibleChild(0, mViews.size(), false)
                    : findOneVisibleChild(mViews.size() - 1, -1, false);
        }

        public int findLastPartiallyVisibleItemPosition() {
            return mReverseLayout
                    ? findOnePartiallyVisibleChild(0, mViews.size(), true)
                    : findOnePartiallyVisibleChild(mViews.size() - 1, -1, true);
        }

        public int findLastCompletelyVisibleItemPosition() {
            return mReverseLayout
                    ? findOneVisibleChild(0, mViews.size(), true)
                    : findOneVisibleChild(mViews.size() - 1, -1, true);
        }

        /**
         * Returns the first view within this span that is partially or fully visible. Partially
         * visible refers to a view that overlaps but is not fully contained within RV's padded
         * bounded area. This view returned can be defined to have an area of overlap strictly
         * greater than zero if acceptEndPointInclusion is false. If true, the view's endpoint
         * inclusion is enough to consider it partially visible. The latter case can then refer to
         * an out-of-bounds view positioned right at the top (or bottom) boundaries of RV's padded
         * area. This is used e.g. inside
         * {@link #onFocusSearchFailed(View, int, RecyclerView.Recycler, RecyclerView.State)} for
         * calculating the next unfocusable child to become visible on the screen.
         *
         * @param fromIndex               The child position index to start the search from.
         * @param toIndex                 The child position index to end the search at.
         * @param completelyVisible       True if we have to only consider completely visible views,
         *                                false otherwise.
         * @param acceptCompletelyVisible True if we can consider both partially or fully visible
         *                                views, false, if only a partially visible child should be
         *                                returned.
         * @param acceptEndPointInclusion If the view's endpoint intersection with RV's padded
         *                                bounded area is enough to consider it partially visible,
         *                                false otherwise
         * @return The adapter position of the first view that's either partially or fully visible.
         * {@link RecyclerView#NO_POSITION} if no such view is found.
         */
        int findOnePartiallyOrCompletelyVisibleChild(int fromIndex, int toIndex,
                                                     boolean completelyVisible,
                                                     boolean acceptCompletelyVisible,
                                                     boolean acceptEndPointInclusion) {
            int start = mPrimaryOrientation.getStartAfterPadding();
            int end = mPrimaryOrientation.getEndAfterPadding();
            int next = toIndex > fromIndex ? 1 : -1;
            for (int i = fromIndex; i != toIndex; i += next) {
                View child = mViews.get(i);
                int childStart = mPrimaryOrientation.getDecoratedStart(child);
                int childEnd = mPrimaryOrientation.getDecoratedEnd(child);
                boolean childStartInclusion = acceptEndPointInclusion ? (childStart <= end)
                        : (childStart < end);
                boolean childEndInclusion = acceptEndPointInclusion ? (childEnd >= start)
                        : (childEnd > start);
                if (childStartInclusion && childEndInclusion) {
                    if (completelyVisible && acceptCompletelyVisible) {
                        // the child has to be completely visible to be returned.
                        if (childStart >= start && childEnd <= end) {
                            return getPosition(child);
                        }
                    } else if (acceptCompletelyVisible) {
                        // can return either a partially or completely visible child.
                        return getPosition(child);
                    } else if (childStart < start || childEnd > end) {
                        // should return a partially visible child if exists and a completely
                        // visible child is not acceptable in this case.
                        return getPosition(child);
                    }
                }
            }
            return RecyclerView.NO_POSITION;
        }

        int findOneVisibleChild(int fromIndex, int toIndex, boolean completelyVisible) {
            return findOnePartiallyOrCompletelyVisibleChild(fromIndex, toIndex, completelyVisible,
                    true, false);
        }

        int findOnePartiallyVisibleChild(int fromIndex, int toIndex,
                                         boolean acceptEndPointInclusion) {
            return findOnePartiallyOrCompletelyVisibleChild(fromIndex, toIndex, false, false,
                    acceptEndPointInclusion);
        }

        /**
         * Depending on the layout direction, returns the View that is after the given position.
         */
        public View getFocusableViewAfter(int referenceChildPosition, int layoutDir) {
            View candidate = null;
            if (layoutDir == LayoutState.LAYOUT_START) {
                int limit = mViews.size();
                for (int i = 0; i < limit; i++) {
                    View view = mViews.get(i);
                    if (mReverseLayout ? getPosition(view) <= referenceChildPosition : getPosition(view) >= referenceChildPosition) {
                        break;
                    }
                    if (view.hasFocusable()) {
                        candidate = view;
                    } else {
                        break;
                    }
                }
            } else {
                for (int i = mViews.size() - 1; i >= 0; i--) {
                    View view = mViews.get(i);
                    if (mReverseLayout ? getPosition(view) >= referenceChildPosition : getPosition(view) <= referenceChildPosition) {
                        break;
                    }
                    if (view.hasFocusable()) {
                        candidate = view;
                    } else {
                        break;
                    }
                }
            }
            return candidate;
        }
    }

    /**
     * Data class to hold the information about an anchor position which is used in onLayout call.
     */
    class AnchorInfo {

        int mPosition;
        int mOffset;
        boolean mLayoutFromEnd;
        boolean mInvalidateOffsets;
        boolean mValid;
        // this is where we save span reference lines in case we need to re-use them for multi-pass
        // measure steps
        int[] mSpanReferenceLines;

        AnchorInfo() {
            reset();
        }

        void reset() {
            mPosition = RecyclerView.NO_POSITION;
            mOffset = INVALID_OFFSET;
            mLayoutFromEnd = false;
            mInvalidateOffsets = false;
            mValid = false;
            if (mSpanReferenceLines != null) {
                Arrays.fill(mSpanReferenceLines, -1);
            }
        }

        void saveSpanReferenceLines(Span[] spans) {
            int spanCount = spans.length;
            if (mSpanReferenceLines == null || mSpanReferenceLines.length < spanCount) {
                mSpanReferenceLines = new int[mSpans.length];
            }
            for (int i = 0; i < spanCount; i++) {
                // does not matter start or end since this is only recorded when span is reset
                mSpanReferenceLines[i] = spans[i].getStartLine(Span.INVALID_LINE);
            }
        }

        void assignCoordinateFromPadding() {
            mOffset = mLayoutFromEnd ? mPrimaryOrientation.getEndAfterPadding()
                    : mPrimaryOrientation.getStartAfterPadding();
        }

        void assignCoordinateFromPadding(int addedDistance) {
            if (mLayoutFromEnd) {
                mOffset = mPrimaryOrientation.getEndAfterPadding() - addedDistance;
            } else {
                mOffset = mPrimaryOrientation.getStartAfterPadding() + addedDistance;
            }
        }
    }
}
