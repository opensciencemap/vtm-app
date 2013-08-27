/*Copyright 2013 Ahmad Al-saleem
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

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

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

	private static final int DIALOG_LOCATION_PROVIDER_DISABLED = 2;

	private MyLocationListener mLocationListener;
	private LocationManager mLocationManager;
	private boolean mShowMyLocation;

	private boolean mSnapToLocation;

	/* package */final TileMap mTileMap;

	List<GeoPoint> points = new ArrayList<GeoPoint>();

	LocationMarker mLocationMaker;

	boolean preLocation;

	GeoPoint myLocation;

	Timer mTimer = new Timer();

	public LocationHandler(TileMap tileMap) {
		mTileMap = tileMap;

		mLocationManager = (LocationManager) tileMap
				.getSystemService(Context.LOCATION_SERVICE);
		mLocationListener = new MyLocationListener(App.map);

	}

	int preZoomLevel;

	@SuppressWarnings("deprecation")
	public boolean enableShowMyLocation(boolean centerAtFirstFix) {
		Log.d(TAG, "enableShowMyLocation " + mShowMyLocation);

		gotoLastKnownPosition();

		if (!mShowMyLocation) {
			Criteria criteria = new Criteria();
			criteria.setAccuracy(Criteria.ACCURACY_FINE);
			String bestProvider = mLocationManager.getBestProvider(criteria, true);

			if (bestProvider == null) {
				mTileMap.showDialog(DIALOG_LOCATION_PROVIDER_DISABLED);
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

	GeoPoint prePosition;
	int preZoomMylocation;

	public void gotoLastKnownPosition() {

		Location currentLocation = null;
		Location bestLocation = null;
		MapPosition Positontemp = new MapPosition();

		App.map.getMapViewPosition().getMapPosition(Positontemp);

		prePosition = Positontemp.getGeoPoint();    // SAVE LAST POSITION

		preZoomLevel = Positontemp.zoomLevel;

		for (String provider : mLocationManager.getProviders(true)) {
			currentLocation = mLocationManager.getLastKnownLocation(provider);
			if (currentLocation == null)
				continue;
			if (bestLocation == null
					|| currentLocation.getAccuracy() < bestLocation.getAccuracy()) {
				bestLocation = currentLocation;
			}
		}

		if (bestLocation != null) {

			byte zoom = (byte) 12;

			MapPosition mapPosition = new MapPosition();
			mapPosition.setPosition(bestLocation.getLatitude(), bestLocation.getLongitude());
			mapPosition.setZoomLevel(zoom);

			if (App.map.getOverlays().size() > 2) {
				App.map.getOverlays().remove(myLocationOverlay);
				App.map.getOverlays().remove(ts);
				App.map.getOverlays().remove(orientationOverlay);
			}

			myLocationOverlay = new LocationMarker(App.map);
			//	return ;}

			myLocationOverlay.setPosition(mapPosition.getGeoPoint());

			MyLocation2 = new GeoPoint(bestLocation.getLatitude(), bestLocation.getLongitude());

			myLocation = new GeoPoint(mapPosition.getGeoPoint().getLatitude(), mapPosition
					.getGeoPoint().getLongitude());

			// calculation related to offset the lat/log  ... make it general function

			//	double ground=	MercatorProjection.calculateGroundResolution(
			// App.map.getMapPosition().getMapCenter().getLatitude(),
			//App.map.getMapPosition().getMapPosition().zoomLevel);

			//double  raduisInPixle = ((double) currentLocation.getAccuracy()) /ground;

			//double latt = MercatorProjection.latitudeToPixelY(
			//App.map.getMapPosition().getMapCenter().getLatitude()	,
			//App.map.getMapPosition().getMapPosition().zoomLevel);

			//Toast.makeText(App.activity,"raduis in pixle"+ String.valueOf(latt), Toast.LENGTH_LONG).show();

			if (currentLocation != null) {
				ts = new LocationOverlay(App.map, (float) currentLocation.getAccuracy(), 0);
				ts.setLat(((float) mapPosition.getGeoPoint().getLatitude()));
				ts.setLon(((float) mapPosition.getGeoPoint().getLongitude()));

			} else if (bestLocation != null) {
				ts = new LocationOverlay(App.map, (float) bestLocation.getAccuracy(), 0);
				ts.setLat(((float) mapPosition.getGeoPoint().getLatitude()));
				ts.setLon(((float) mapPosition.getGeoPoint().getLongitude()));
			}
			//else
			//	return;

			// ts.setRaduis((float) currentLocation.getAccuracy());
			App.map.getOverlays().add(1, ts);

			App.map.getOverlays().add(2, myLocationOverlay);
			orientationOverlay = new GenericOverlay(App.map, tempZeft);
			App.map.getOverlays().add(orientationOverlay);

			App.map.setMapPosition(mapPosition);

			App.map.redrawMap(true);

		} else {
			mTileMap.showToastOnUiThread(mTileMap
					.getString(R.string.error_last_location_unknown));
		}

	}

	GenericOverlay orientationOverlay;

	GeoPoint MyLocation2;

	int i = 0;

	/**
	 * Disables the "show my location" mode.
	 * @return ...
	 */
	public boolean disableShowMyLocation() {
		if (mShowMyLocation) {
			mShowMyLocation = false;
			disableSnapToLocation(false);

			if (App.map.getOverlays().size() > 2) {
				App.map.getOverlays().remove(myLocationOverlay);
				App.map.getOverlays().remove(ts);
				App.map.getOverlays().remove(orientationOverlay);
			}
			App.map.redrawMap(true);
			mLocationManager.removeUpdates(mLocationListener);
			// if (circleOverlay != null) {
			// mapView.getOverlays().remove(circleOverlay);
			// mapView.getOverlays().remove(itemizedOverlay);
			// circleOverlay = null;
			// itemizedOverlay = null;
			// }

			//mSnapToLocationView.setVisibility(View.GONE);

			return true;
		}
		return false;
	}

	LocationMarker myLocationOverlay;
	public LocationOverlay ts;

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
			//mSnapToLocationView.setChecked(false);

			App.map.setClickable(true);

			if (showToast) {
				mTileMap.showToastOnUiThread(mTileMap
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

			App.map.setClickable(false);

			if (showToast) {
				mTileMap.showToastOnUiThread(mTileMap
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

	DirectionRenderLayer tempZeft = null;

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

			tempZeft = new DirectionRenderLayer(mapView, prePosition);

			arrowBitmap = BitmapFactory.decodeResource(App.activity.getResources(),
					R.drawable.direction);
			canvasBitmap = arrowBitmap.copy(Bitmap.Config.ARGB_8888, true);

			mCanvas = new Canvas(canvasBitmap);
		}

		public void rotateDrawable(float angle) {

			if (MyLocation2 != null && tempZeft != null)
				tempZeft.setLocation(MyLocation2);

			synchronized (canvasBitmap) {

			// arrowBitmap= Bitmap.createScaledBitmap(arrowBitmap, 10, 10, false);
			// Create blank bitmap of equal size

			canvasBitmap.eraseColor(0x00000000);

			// Create rotation matrix
			//Matrix scaleMatrix = new Matrix();
			//rotateMatrix.setScale(.1f, .1f);
			mRotateMatrix.setRotate(angle, mCanvas.getWidth() / 2, mCanvas.getHeight() / 2);

			// Draw bitmap onto canvas using matrix

			mCanvas.drawBitmap(arrowBitmap, mRotateMatrix, null);
			// rotateMatrix.setScale(.1f, .1f);
			mCanvas.drawBitmap(arrowBitmap, mRotateMatrix, null);
			// canvas.setMatrix(matrix)

			if (tempZeft != null)
				tempZeft.locationBitmap = canvasBitmap;
			}
		}

		@Override
		public void onLocationChanged(Location location) {

			rotateDrawable(val);

			Log.d(TAG, "onLocationChanged, "
					+ " lon:" + location.getLongitude()
					+ " lat:" + location.getLatitude());

			if (!isShowMyLocationEnabled()) {
				return;
			}

			GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());

			// this.advancedMapViewer.overlayCircle.setCircleData(point, location.getAccuracy());
			// this.advancedMapViewer.overlayItem.setPoint(point);
			// this.advancedMapViewer.circleOverlay.requestRedraw();
			// this.advancedMapViewer.itemizedOverlay.requestRedraw();

			if (mSetCenter || isSnapToLocationEnabled()) {
				mSetCenter = false;
				App.map.setCenter(point);
			}
			if (mSnapToLocation) {

				App.activity.getCompass().stop();
				App.map.enableRotation(true);
				gotoLastKnownPosition();

			}
			//gotoLastKnownPosition();
		}

		@Override
		public void onProviderDisabled(String provider) {
			// do nothing
		}

		@Override
		public void onProviderEnabled(String provider) {
			// do nothing
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			// do nothing
		}

		boolean isFirstCenter() {
			return mSetCenter;
		}

		void setFirstCenter(boolean center) {
			mSetCenter = center;
		}

		@Override
		public void onAccuracyChanged(Sensor arg0, int arg1) {
			// TODO Auto-generated method stub

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
