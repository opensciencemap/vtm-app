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

import org.oscim.app.App;
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

	// compass arrow to rotate
	private final ImageView mArrowView;

	private final float[] mRotationM = new float[9];
	private final float[] mRotationV = new float[3];
	private float mCurRotation;
	private float mCurTilt;

	private boolean mEnabled;

	@Override
	public void onUpdate(MapPosition mapPosition, boolean changed, boolean clear) {
		if (!isEnabled()) {
			mCurRotation = -mapPosition.angle;
			this.adjustArrow(mCurRotation, mCurRotation);
		}
		super.onUpdate(mapPosition, changed, clear);
	}

	public Compass(Context context, MapView mapView) {
		super(mapView);

		mSensorManager = (SensorManager) context
				.getSystemService(Context.SENSOR_SERVICE);

		mArrowView = (ImageView) App.activity.findViewById(R.id.compass);
	}

	public boolean isEnabled() {
		return mEnabled;
	}

	public void start() {
		if (mEnabled)
			return;
		mEnabled = true;

		Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
		mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);

		//mMapView.getMapViewPosition().setRotation(-mCurRotation);
	}

	public void stop() {
		if (!mEnabled)
			return;

		mEnabled = false;
		mSensorManager.unregisterListener(this);
	}

	public void adjustArrow(float prev, float cur) {
		Animation an = new RotateAnimation(-prev, -cur,
				Animation.RELATIVE_TO_SELF, 0.5f,
				Animation.RELATIVE_TO_SELF, 0.5f);

		an.setDuration(100);
		an.setRepeatCount(0);
		an.setFillAfter(true);

		mArrowView.startAnimation(an);
	}

	@Override
	public void onSensorChanged(SensorEvent event) {

		synchronized (this) {

			if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR)
				return;

			SensorManager.getRotationMatrixFromVector(mRotationM, event.values);
			SensorManager.getOrientation(mRotationM, mRotationV);

			float rotation = (float)Math.toDegrees(mRotationV[0]);

			float change = rotation - mCurRotation;
			if (change > 180)
				change -= 360;
			else if (change < -180)
				change += 360;

			// low-pass
			change *= 0.25;

			rotation = mCurRotation + change;

			if (rotation >  180)
				rotation -= 360;
			else if (rotation <  -180)
				rotation += 360;

			mCurTilt = mCurTilt + 0.25f * (mRotationV[1] - mCurTilt);

			if (Math.abs(change) > 0.01) {
				adjustArrow(mCurRotation, rotation);
				mMapView.getMapViewPosition().setRotation(-rotation);

				float tilt = (float) Math.toDegrees(mCurTilt);
				mMapView.getMapViewPosition().setTilt(-tilt * 1.6f);
				mMapView.redrawMap(true);
			}

			mCurRotation = rotation;
		}
	}

	public void rest() {
		mMapView.getMapViewPosition().setRotation(0);

		stop();
		adjustArrow(0, 0);

		//mMapView.getMapViewPosition().setRotation(-mCurRotation);

		mMapView.redrawMap(true);

	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}
}
