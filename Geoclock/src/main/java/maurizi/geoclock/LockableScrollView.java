package maurizi.geoclock;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ScrollView;

/**
 * Adapted from http://stackoverflow.com/a/5763815
 */
class LockableScrollView extends ScrollView {

	// true if we can scroll (not locked)
	// false if we cannot scroll (locked)
	private boolean mScrollable = true;

	public void setScrollingEnabled(boolean enabled) {
		mScrollable = enabled;
	}


	public LockableScrollView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public LockableScrollView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public LockableScrollView(Context context) {
		super(context);
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		switch (ev.getAction()) {
			case MotionEvent.ACTION_DOWN:
				return mScrollable && super.onTouchEvent(ev);
			default:
				return super.onTouchEvent(ev);
		}
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		return mScrollable && super.onInterceptTouchEvent(ev);
	}

}