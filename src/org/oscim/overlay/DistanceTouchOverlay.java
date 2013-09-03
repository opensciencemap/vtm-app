/*
 * Copyright 2013 Ahmad Saleem
 * Copyright 2013 Hannes Janetzek
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.oscim.overlay;

import java.util.Timer;
import java.util.TimerTask;

import org.oscim.backend.input.MotionEvent;
import org.oscim.core.GeoPoint;
import org.oscim.layers.InputLayer;
import org.oscim.view.MapView;
import org.osmdroid.overlays.MapEventsReceiver;


public class DistanceTouchOverlay extends InputLayer {
	//private final static String TAG = DistanceTouchOverlay.class.getName();

	private static final int LONGPRESS_THRESHOLD = 800;

	private Timer mLongpressTimer;

	private float mPrevX1, mPrevX2, mPrevY1, mPrevY2;
	private float mCurX1, mCurX2, mCurY1, mCurY2;

	private final static int POINTER_UP = -1;
	private int mPointer1 = POINTER_UP;
	private int mPointer2 = POINTER_UP;

	/**
	 * @param mapView
	 *            the MapView
	 * @param receiver
	 *            the object that will receive/handle the events. It must
	 *            implement MapEventsReceiver interface.
	 */
	public DistanceTouchOverlay(MapView mapView, MapEventsReceiver receiver) {
		super(mapView);
		mReceiver = receiver;
	}

	private void cancel() {

		if (mLongpressTimer != null) {
			mLongpressTimer.cancel();
			mLongpressTimer = null;
		}
	}

//	@Override
//	public boolean onTouchEvent(MotionEvent e) {
//
//		int action = e.getActionMasked(); //(e.getAction() & e.getActionMasked());
//		// lens overlay is not active, cancel timer
//		if ((action == MotionEvent.ACTION_CANCEL)) {
//			cancel();
//			return false;
//		}
//
//		if (mLongpressTimer != null) {
//			// any pointer up while long press detection
//			// cancels timer
//			if (action == MotionEvent.ACTION_POINTER_UP ||
//					action == MotionEvent.ACTION_UP) {
//
//				cancel();
//				return false;
//			}
//
//			// two fingers must still be down, tested
//			// one above.
//			if (action == MotionEvent.ACTION_MOVE) {
//				// update pointer positions
//				int idx1 = e.findPointerIndex(mPointer1);
//				int idx2 = e.findPointerIndex(mPointer2);
//
//				mCurX1 = e.getX(idx1);
//				mCurY1 = e.getY(idx1);
//				mCurX2 = e.getX(idx2);
//				mCurY2 = e.getY(idx2);
//
//				// cancel if moved one finger more than 50 pixel
//				float maxSq = 10 * 10;
//				float d = (mCurX1 - mPrevX1) * (mCurX1 - mPrevX1) +
//						(mCurY1 - mPrevY1) * (mCurY1 - mPrevY1);
//				if (d > maxSq) {
//					cancel();
//					return false;
//				}
//				d = (mCurX2 - mPrevX2) * (mCurX2 - mPrevX2) +
//						(mCurY2 - mPrevY2) * (mCurY2 - mPrevY2);
//				if (d > maxSq) {
//					cancel();
//					return false;
//				}
//			}
//		}
//
//		if ((action == MotionEvent.ACTION_POINTER_DOWN)
//				&& (e.getPointerCount() == 2)) {
//
//			// keep track of pointer ids, only
//			// use these for gesture, ignoring
//			// more than two pointer
//			mPointer1 = e.getPointerId(0);
//			mPointer2 = e.getPointerId(1);
//
//			if (mLongpressTimer == null) {
//				// start timer, keep initial down position
//				mCurX1 = mPrevX1 = e.getX(0);
//				mCurY1 = mPrevY1 = e.getY(0);
//				mCurX2 = mPrevX2 = e.getX(1);
//				mCurY2 = mPrevY2 = e.getY(1);
//				runLongpressTimer();
//			}
//		}
//
//		return false;
//	}

	private MapEventsReceiver mReceiver;

	@Override
	public boolean onLongPress(MotionEvent e) {
		// dont forward long press when two fingers are down.
		// maybe should be only done if our timer is still running.
		// ... not sure if this is even needed
		GeoPoint p = mMapView.getMapViewPosition().fromScreenPixels(e.getX(), e.getY());
		return mReceiver.longPressHelper(p);

	}

	public void runLongpressTimer() {
		mLongpressTimer = new Timer();
		mLongpressTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				final GeoPoint p1 = mMapView.getMapViewPosition().fromScreenPixels(mCurX1, mCurY1);
				final GeoPoint p2 = mMapView.getMapViewPosition().fromScreenPixels(mCurX2, mCurY2);

				mMapView.post(new Runnable() {
					@Override
					public void run() {
						mReceiver.longPressHelper(p1, p2);
					}
				});
			}
		}, LONGPRESS_THRESHOLD);
	}

}
