/* Copyright 2013 Ahmad Al-saleem
 * Copyright 2010, 2011, 2012 mapsforge.org
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
import org.oscim.app.TileMap;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.layers.overlay.GenericOverlay;
import org.oscim.view.MapView;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

public class LocationHandler {
	private final static String TAG = LocationHandler.class.getName();

	private final static int DIALOG_LOCATION_PROVIDER_DISABLED = 2;

	private final static int SHOW_LOCATION_ZOOM = 15;

	private MyLocationListener mLocationListener;
	private LocationManager mLocationManager;
	private boolean mShowMyLocation;

	private boolean mSnapToLocation;

	private GenericOverlay mOrientationOverlay;
	private GeoPoint mCurrentLocation;

	private GeoPoint prePosition;

	public LocationHandler(TileMap tileMap) {

		mLocationManager = (LocationManager) tileMap
				.getSystemService(Context.LOCATION_SERVICE);
		mLocationListener = new MyLocationListener(App.map);

	}

	@SuppressWarnings("deprecation")
	public boolean enableShowMyLocation(boolean centerAtFirstFix) {
		Log.d(TAG, "enableShowMyLocation " + mShowMyLocation);

		gotoLastKnownPosition();

		if (!mShowMyLocation) {
			Criteria criteria = new Criteria();
			criteria.setAccuracy(Criteria.ACCURACY_FINE);
			String bestProvider = mLocationManager.getBestProvider(criteria, true);

			if (bestProvider == null) {
				App.activity.showDialog(DIALOG_LOCATION_PROVIDER_DISABLED);
				return false;
			}

			mShowMyLocation = true;

			Log.d(TAG, "enableShowMyLocation " + mShowMyLocation);

			mLocationListener.setFirstCenter(centerAtFirstFix);

			mLocationManager.requestLocationUpdates(bestProvider, 1000, 0,
					mLocationListener);

			return true;
		}
		return false;
	}

	public void gotoLastKnownPosition() {

		Location location = null;

		MapPosition Positontemp = new MapPosition();
		App.map.getMapViewPosition().getMapPosition(Positontemp);

		prePosition = Positontemp.getGeoPoint();

		for (String provider : mLocationManager.getProviders(true)) {
			Location l = mLocationManager.getLastKnownLocation(provider);
			if (l == null)
				continue;

			if (location == null || l.getAccuracy() < location.getAccuracy()) {
				location = l;
			}
		}

		if (location == null) {
			App.activity.showToastOnUiThread(App.activity
					.getString(R.string.error_last_location_unknown));
			return;
		}

		MapPosition mapPosition = new MapPosition();
		mapPosition.setPosition(location.getLatitude(), location.getLongitude());
		mapPosition.setZoomLevel(SHOW_LOCATION_ZOOM);

		//App.map.getOverlays().remove(mLocationMarker);
		App.map.getOverlays().remove(mLocationOverlay);
		App.map.getOverlays().remove(mOrientationOverlay);

		mCurrentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
		mLocationOverlay = new LocationOverlay(App.map);
		App.map.getOverlays().add(mLocationOverlay);

		//mLocationMarker = new LocationMarker(App.map);
		//mLocationMarker.setPosition(mCurrentLocation);
		//App.map.getOverlays().add(mLocationMarker);

		mLocationOverlay.setPosition(mCurrentLocation, location.getAccuracy());

		mOrientationOverlay = new GenericOverlay(App.map, mDirectionRenderLayer);
		App.map.getOverlays().add(mOrientationOverlay);

		App.map.setMapPosition(mapPosition);
		App.map.redrawMap(true);

	}

	/**
	 * Disables the "show my location" mode.
	 * @return ...
	 */
	public boolean disableShowMyLocation() {
		if (mShowMyLocation) {
			mShowMyLocation = false;
			disableSnapToLocation(false);

			//App.map.getOverlays().remove(mLocationMarker);
			App.map.getOverlays().remove(mLocationOverlay);
			App.map.getOverlays().remove(mOrientationOverlay);

			App.map.redrawMap(true);
			mLocationManager.removeUpdates(mLocationListener);

			return true;
		}
		return false;
	}

	//private LocationMarker mLocationMarker;
	private LocationOverlay mLocationOverlay;

	/**
	 * Returns the status of the "show my location" mode.
	 * @return true if the "show my location" mode is enabled, false otherwise.
	 */
	public boolean isShowMyLocationEnabled() {
		return mShowMyLocation;
	}

	/**
	 * Disables the "snap to location" mode.
	 * @param showToast
	 *            defines whether a toast message is displayed or not.
	 */
	public void disableSnapToLocation(boolean showToast) {
		if (mSnapToLocation) {
			mSnapToLocation = false;

			App.map.getEventLayer().enableMove(false);
			//App.map.setClickable(true);

			if (showToast) {
				App.activity.showToastOnUiThread(App.activity
						.getString(R.string.snap_to_location_disabled));
			}
		}
	}

	/**
	 * Enables the "snap to location" mode.
	 * @param showToast
	 *            defines whether a toast message is displayed or not.
	 */
	public void enableSnapToLocation(boolean showToast) {
		if (!mSnapToLocation) {
			mSnapToLocation = true;

			App.map.getEventLayer().enableMove(false);
			//App.map.setClickable(false);

			if (showToast) {
				App.activity.showToastOnUiThread(App.activity
						.getString(R.string.snap_to_location_enabled));
			}
		}
	}

	/**
	 * Returns the status of the "snap to location" mode.
	 * @return true if the "snap to location" mode is enabled, false otherwise.
	 */
	public boolean isSnapToLocationEnabled() {
		return mSnapToLocation;
	}

	DirectionRenderLayer mDirectionRenderLayer = null;

	class MyLocationListener implements LocationListener, SensorEventListener {
		SensorManager mSensorManager;

		private boolean mSetCenter;

		private final Bitmap arrowBitmap;
		private final Bitmap canvasBitmap;
		private final Canvas mCanvas;
		private final Matrix mRotateMatrix = new Matrix();

		@SuppressWarnings("deprecation")
		public MyLocationListener(MapView mapView) {
			mSensorManager = (SensorManager) App.activity.getSystemService(Context.SENSOR_SERVICE);
			mSensorManager.registerListener(this,
					mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
					SensorManager.SENSOR_DELAY_NORMAL);

			mDirectionRenderLayer = new DirectionRenderLayer(mapView, prePosition);

			arrowBitmap = BitmapFactory.decodeResource(App.activity.getResources(),
					R.drawable.direction);
			canvasBitmap = arrowBitmap.copy(Bitmap.Config.ARGB_8888, true);

			mCanvas = new Canvas(canvasBitmap);
		}

		public void rotateDrawable(float angle) {
			if (mCurrentLocation != null && mDirectionRenderLayer != null)
				mDirectionRenderLayer.setLocation(mCurrentLocation);

			synchronized (canvasBitmap) {
				canvasBitmap.eraseColor(0x00000000);
				mRotateMatrix.setRotate(angle, mCanvas.getWidth() / 2, mCanvas.getHeight() / 2);

				// Draw bitmap onto canvas using matrix
				mCanvas.drawBitmap(arrowBitmap, mRotateMatrix, null);
				mCanvas.drawBitmap(arrowBitmap, mRotateMatrix, null);

				if (mDirectionRenderLayer != null)
					mDirectionRenderLayer.locationBitmap = canvasBitmap;
			}
		}

		@Override
		public void onLocationChanged(Location location) {
			rotateDrawable(val);

			if (!isShowMyLocationEnabled()) {
				return;
			}

			GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());

			if (mSetCenter || isSnapToLocationEnabled()) {
				mSetCenter = false;
				App.map.setCenter(point);
			}
			if (mSnapToLocation) {

				App.activity.getCompass().stop();
				App.map.getEventLayer().enableRotation(true);
				gotoLastKnownPosition();

			}
			//gotoLastKnownPosition();
		}

		@Override
		public void onProviderDisabled(String provider) {
		}

		@Override
		public void onProviderEnabled(String provider) {
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
		}

		boolean isFirstCenter() {
			return mSetCenter;
		}

		void setFirstCenter(boolean center) {
			mSetCenter = center;
		}

		@Override
		public void onAccuracyChanged(Sensor arg0, int arg1) {
		}

		float val;

		@SuppressWarnings("deprecation")
		@Override
		public void onSensorChanged(SensorEvent event) {

			if (event.sensor.getType() == Sensor.TYPE_ORIENTATION)
				if (event.values != null) {

					if (val != -event.values[0]) {
						val = -event.values[0];
						rotateDrawable(val);

					}
				}
		}
	}
}
