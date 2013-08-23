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

package org.oscim.app.location;

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
	private final ImageView mArrowView;

	private final float[] mRotationM = new float[9];
	private final float[] mRotationV = new float[3];

	private float mCurRotation;
	private float mCurTilt;

	private boolean mControlOrientation;

	@Override
	public void onUpdate(MapPosition mapPosition, boolean changed, boolean clear) {
		if (!mControlOrientation) {
			float rotation = -mapPosition.angle;
			adjustArrow(rotation, rotation);
		}
		super.onUpdate(mapPosition, changed, clear);
	}

	public Compass(Context context, MapView mapView) {
		super(mapView);

		mSensorManager = (SensorManager) context
				.getSystemService(Context.SENSOR_SERVICE);

		mArrowView = (ImageView) App.activity.findViewById(R.id.compass);

		setEnabled(false);
	}

	public synchronized float getRotation(){
		return mCurRotation;
	}

	public void controlOrientation(boolean enable) {
		mControlOrientation = enable;
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (isEnabled() == enabled)
			return;

		super.setEnabled(enabled);

		if (isEnabled()){
			Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
			mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
		} else{
			mSensorManager.unregisterListener(this);
		}
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

			if (mControlOrientation && Math.abs(change) > 0.01) {
				adjustArrow(mCurRotation, rotation);
				mMapView.getMapViewPosition().setRotation(-rotation);

				float tilt = (float) Math.toDegrees(mCurTilt);
				mMapView.getMapViewPosition().setTilt(-tilt * 1.6f);
				mMapView.redrawMap(true);
			}

			mCurRotation = rotation;
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}
}
