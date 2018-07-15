package com.autoroll;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;

import com.autoroll.strategy.VerticalRollStrategy;

/**
 * Created by shenxl on 2018/7/11.
 */

public class AutoRollView extends ViewGroup {
    // 动画时间
    private static final int ANIM_TIME = 500;
    // 停留时间
    private static final int DEFAULT_INTERVAL = 3000;

    private long mRollInterval = DEFAULT_INTERVAL;
    private long mAnimDuration = ANIM_TIME;
    private AbsBannerAdapter mAdapter;
    private ChildViewFactory mViewFactory = new ChildViewFactory();
    private RollRunnable mRollRunnable;
    private DelayRunnable mDelayRunnable;
    private OnItemClickListener mItemClickListener;
    private SwitchAnimStrategy mAnimStrategy = new VerticalRollStrategy(true);
    private int mActionDownItemIndex = -1;

    public AutoRollView(Context context) {
        super(context);
    }

    public AutoRollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoRollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AutoRollView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxWidth = 0;
        int maxHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(widthMeasureSpec, heightMeasureSpec);
            if (child.getMeasuredWidth() > maxWidth) {
                maxWidth = child.getMeasuredWidth();
            }
            if (child.getMeasuredHeight() > maxHeight) {
                maxHeight = child.getMeasuredHeight();
            }
        }

        setMeasuredDimension(resolveSize(maxWidth, widthMeasureSpec), resolveSize(maxHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++){
            getChildAt(i).layout(0, 0, getMeasuredWidth(), getMeasuredHeight());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelRolling();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mRollRunnable != null) {
            postDelayed(mRollRunnable, mRollInterval);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                if (mItemClickListener != null && mAdapter != null && mAdapter.getItemCount() > 0) {
                    mActionDownItemIndex = mAdapter.getItemIndex();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mItemClickListener != null && mAdapter != null && mAdapter.getItemCount() > 0 && mAdapter.getItemIndex() == mActionDownItemIndex ){
                    mItemClickListener.onItemClick(this, mViewFactory.thisView(), mAdapter.getItemIndex());
                    return true;
                }
                performClick();
                mActionDownItemIndex = -1;
                break;
        }
        return false;
    }

    public void setOnItemClickListener(OnItemClickListener itemClickListener) {
        mItemClickListener = itemClickListener;
    }

    public long getRollInterval() {
        return mRollInterval;
    }

    public void setRollInterval(long rollInterval) {
        mRollInterval = rollInterval;
    }

    public long getAnimDuration() {
        return mAnimDuration;
    }

    public void setAnimDuration(long animDuration) {
        mAnimDuration = animDuration;
    }

    public void setAnimStrategy(SwitchAnimStrategy animStrategy) {
        mAnimStrategy = animStrategy;
    }

    public void setAdapter(AbsBannerAdapter adapter) {
        this.mAdapter = adapter;
        mViewFactory.setAdapter(adapter);
    }

    public void cancelRolling(){
        if (getChildCount() > 1){
            getChildAt(0).animate().cancel();
            getChildAt(1).animate().cancel();
        }
        removeCallbacks(mRollRunnable);
        removeCallbacks(mDelayRunnable);
        mRollRunnable = null;
        mDelayRunnable = null;
    }

    public void startRolling() {
        mAdapter.resetItemIndex();
        if (getChildCount() == 0){
            addView(mViewFactory.thisView());
            addView(mViewFactory.nextView());
        }

        cancelRolling();
        if (checkSpecCondtion()) {
            return;
        }

        post(new Runnable() {
            @Override
            public void run() {
                if (mRollRunnable == null) {
                    mRollRunnable = new RollRunnable();
                }
                postDelayed(mRollRunnable, mRollInterval);

                showIntervalState();
            }
        });
    }

    private void showIntervalState() {
        View childShow = mViewFactory.thisView();
        mViewFactory.updateViews(mViewFactory.thisView(), mAdapter.getItemIndex());
        childShow.setVisibility(VISIBLE);

        mViewFactory.nextView().setVisibility(INVISIBLE);
    }

    private boolean checkSpecCondtion() {
        if (mAdapter == null || mAdapter.getItemCount() == 0){
            cancelRolling();
            return true;
        } else if (mAdapter.getItemCount() == 1) {
            showIntervalState();
            cancelRolling();
            return true;
        }
        return false;
    }

    private class RollRunnable implements Runnable {
        @Override
        public void run() {
            if (mAnimStrategy != null) {
                View viewOut = mViewFactory.thisView();
                mAnimStrategy.beforeAnimOut(AutoRollView.this, viewOut);
                mAnimStrategy.animOut(AutoRollView.this, viewOut,
                        viewOut.animate().setDuration(mAnimDuration)).start();

                View viewIn = mViewFactory.nextView();
                mViewFactory.updateViews(viewIn, mAdapter.nextItemIndex());
                viewIn.setVisibility(VISIBLE);
                mAnimStrategy.beforeAnimIn(AutoRollView.this, viewIn);
                mAnimStrategy.animIn(AutoRollView.this, viewIn,
                        viewIn.animate().setDuration(mAnimDuration)).start();

                if (mDelayRunnable == null){
                    mDelayRunnable = new DelayRunnable();
                }
                postDelayed(mDelayRunnable, mAnimDuration);
            } else {
                gotoInterval();
            }
        }
    };

    private void gotoInterval() {
        mActionDownItemIndex = -1;
        mAdapter.step();
        mViewFactory.step();
        showIntervalState();
        postDelayed(mRollRunnable, mRollInterval);
    }

    private class DelayRunnable implements Runnable {
        @Override
        public void run() {
            gotoInterval();
        }
    }

    public static abstract class AbsBannerAdapter<T extends ViewHolder> {
        private int mItemIndex;

        public abstract T onCreateView(Context context);
        public abstract void updateItem(T holder, int position);
        public abstract int getItemCount();

        private void step() {
            mItemIndex = nextItemIndex();
        }

        private int nextItemIndex() {
            int i = mItemIndex;
            i++;
            if (i >= getItemCount()){
                i = 0;
            }
            return i;
        }

        private void resetItemIndex(){
            mItemIndex = 0;
        }

        private int getItemIndex() {
            return mItemIndex;
        }
    }

    private class ChildViewFactory {
        private ViewHolder[] mViewHolders = new ViewHolder[2];
        private int mViewIndex;
        private AbsBannerAdapter mAdapter;

        public void setAdapter(AbsBannerAdapter adapter) {
            mAdapter = adapter;
        }

        private View thisView() {
            return getView(mViewIndex);
        }

        private View nextView() {
            return getView(next(mViewIndex));
        }

        private View getView(int indext){
            if (indext >= mViewHolders.length) {
                return null;
            } else if (mViewHolders[indext] == null) {
                mViewHolders[indext] = mAdapter.onCreateView(getContext());
            }
            return mViewHolders[indext].getView();
        }

        private int next(int index) {
            index++;
            if (index >= mViewHolders.length){
                index = 0;
            }
            return index;
        }

        private void step(){
            mViewIndex = next(mViewIndex);
        }

        private void updateViews(View view, int index){
            if (view.getTag() != null) {
                ViewHolder holder = (ViewHolder) view.getTag();
                if (holder.mItemIndex != index) {
                    holder.mItemIndex = index;
                    mAdapter.updateItem(holder, index);
                }
            }
        }
    }

    public static class ViewHolder{
        private View mView;
        private int mItemIndex = -1;

        public ViewHolder(View view) {
            mView = view;
            view.setTag(this);
        }

        public View getView() {
            return mView;
        }

        public Context getContext(){
            return mView.getContext();
        }
    }

    public interface OnItemClickListener{
        void onItemClick(AutoRollView parent, View child, int position);
    }

    public interface SwitchAnimStrategy{
        void beforeAnimOut(AutoRollView parent, View child);
        ViewPropertyAnimator animOut(AutoRollView parent, View child, ViewPropertyAnimator animator);
        void beforeAnimIn(AutoRollView parent, View child);
        ViewPropertyAnimator animIn(AutoRollView parent, View child, ViewPropertyAnimator animator);
    }
}