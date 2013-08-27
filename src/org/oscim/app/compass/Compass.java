/*
 * Copyright 2013 Ahmad Saleem
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

package org.oscim.app.compass;

import org.oscim.app.R;
import org.oscim.core.MapPosition;
import org.oscim.layers.Layer;
import org.oscim.view.MapView;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;

public class Compass extends Layer implements SensorEventListener {
	//private static final String TAG = Compass.class.getName();

	private final SensorManager mSensorManager;
	private final Sensor mAccSensor;
	private final Sensor mMagSensor;
	private final float[] mGravity = new float[3];
	private final float[] mGeomagnetic = new float[3];
	private float mAzimuth = 0f;
	private float mCurrectAzimuth = 0;
	// compass arrow to rotate
	private final ImageView mArrowView;

	private float mRotation[] = new float[9];
	private float mInclination[] = new float[9];
	private float mOrientation[] = new float[3];

	private boolean mEnabled;

	@Override
	public void onUpdate(MapPosition mapPosition, boolean changed, boolean clear) {
		if (!isEnabled()) {
			mAzimuth = -mapPosition.angle;
			this.adjustArrow();
		}
		super.onUpdate(mapPosition, changed, clear);
	}

	public Compass(Context context, MapView mapView) {
		super(mapView);
		mSensorManager = (SensorManager) context
				.getSystemService(Context.SENSOR_SERVICE);
		mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mMagSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

		mArrowView = (ImageView) mapView.findViewById(R.id.imageView2);
	}


	public boolean isEnabled() {
		return mEnabled;
	}

	public void start() {
		if (mEnabled)
			return;

		mEnabled = true;
		mSensorManager.registerListener(this, mAccSensor,
				SensorManager.SENSOR_DELAY_FASTEST);
		mSensorManager.registerListener(this, mMagSensor,
				SensorManager.SENSOR_DELAY_FASTEST);
		mMapView.getMapViewPosition().setRotation(-mCurrectAzimuth);
	}

	public void stop() {
		if (!mEnabled)
			return;

		mEnabled = false;

		mSensorManager.unregisterListener(this);
	}

	public void adjustArrow() {
		if (mArrowView == null) {
			//Log.i(TAG, "arrow view is not set");
			return;
		}

		//	Log.i(TAG, "will set rotation from " + currectAzimuth + " to "
		//		+ azimuth);

		Animation an = new RotateAnimation(-mCurrectAzimuth, -mAzimuth,
				Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
				0.5f);
		mCurrectAzimuth = mAzimuth;

		an.setDuration(100);
		an.setRepeatCount(0);
		an.setFillAfter(true);

		mArrowView.startAnimation(an);
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		final float alpha = 0.97f;

		synchronized (this) {
			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

				mGravity[0] = alpha * mGravity[0] + (1 - alpha)
						* event.values[0];
				mGravity[1] = alpha * mGravity[1] + (1 - alpha)
						* event.values[1];
				mGravity[2] = alpha * mGravity[2] + (1 - alpha)
						* event.values[2];

				// mGravity = event.values;

				// Log.e(TAG, Float.toString(mGravity[0]));
			}

			if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
				// mGeomagnetic = event.values;

				mGeomagnetic[0] = alpha * mGeomagnetic[0] + (1 - alpha)
						* event.values[0];
				mGeomagnetic[1] = alpha * mGeomagnetic[1] + (1 - alpha)
						* event.values[1];
				mGeomagnetic[2] = alpha * mGeomagnetic[2] + (1 - alpha)
						* event.values[2];
				// Log.e(TAG, Float.toString(event.values[0]));

			}

			boolean success = SensorManager.getRotationMatrix(mRotation, mInclination, mGravity,
					mGeomagnetic);
			if (success) {
				SensorManager.getOrientation(mRotation, mOrientation);
				// Log.d(TAG, "azimuth (rad): " + azimuth);
				mAzimuth = (float) Math.toDegrees(mOrientation[0]); // orientation
				mAzimuth = (mAzimuth + 360) % 360;
				// Log.d(TAG, "azimuth (deg): " + azimuth);
				adjustArrow();
				if (Math.abs(mAzimuth - angle) > .01f) {

					angle = mAzimuth;
					this.mMapView.getMapViewPosition().setRotation(-this.mAzimuth);
					mMapView.redrawMap(true);
				}
			}
		}

	}

	public void rest() {
		this.mMapView.getMapViewPosition().setRotation(0);
		stop();

		mCurrectAzimuth = 0;
		mAzimuth = 0;
		adjustArrow();
		this.mMapView.getMapViewPosition().setRotation(-this.mCurrectAzimuth);
		mMapView.redrawMap(true);

	}

	private float angle;

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}
}
