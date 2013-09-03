/*
 * Copyright 2010, 2011, 2012 mapsforge.org
 * Copyright 2013 Hannes Janetzek
 * Copyright 2013 Ahmad Al-saleem
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

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

public class LocationHandler implements LocationListener {
	private final static String TAG = LocationHandler.class.getName();

	public enum Mode {
		OFF,
		SHOW,
		SNAP,
	}

	private final static int DIALOG_LOCATION_PROVIDER_DISABLED = 2;
	private final static int SHOW_LOCATION_ZOOM = 14;

	private final LocationManager mLocationManager;
	private final LocationOverlay mLocationOverlay;

	private Mode mMode = Mode.OFF;

	private boolean mSetCenter;

	public LocationHandler(TileMap tileMap, Compass compass) {
		mLocationManager = (LocationManager) tileMap
				.getSystemService(Context.LOCATION_SERVICE);

		mLocationOverlay = new LocationOverlay(App.map, compass);
	}

	public boolean setMode(Mode mode) {
		if (mode == mMode)
			return true;

		if (mode == Mode.OFF) {
			disableShowMyLocation();

			if (mMode == Mode.SNAP)
				App.map.getEventLayer().enableMove(true);
		}

		if (mMode == Mode.OFF) {
			if (!enableShowMyLocation())
				return false;
		}

		if (mode == Mode.SNAP) {
			App.map.getEventLayer().enableMove(false);
			gotoLastKnownPosition();
		} else {
			App.map.getEventLayer().enableMove(true);
		}

		// FIXME?
		mSetCenter = false;

		mMode = mode;

		return true;
	}

	public Mode getMode() {
		return mMode;
	}

	public boolean isFirstCenter() {
		return mSetCenter;
	}

	@SuppressWarnings("deprecation")
	private boolean enableShowMyLocation() {

		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		String bestProvider = mLocationManager.getBestProvider(criteria, true);

		if (bestProvider == null) {
			App.activity.showDialog(DIALOG_LOCATION_PROVIDER_DISABLED);
			return false;
		}

		mLocationManager.requestLocationUpdates(bestProvider, 10000, 10, this);

		Location location = gotoLastKnownPosition();
		if (location == null)
			return false;

		mLocationOverlay.setEnabled(true);
		mLocationOverlay.setPosition(location.getLatitude(),
				location.getLongitude(),
				location.getAccuracy());

		// FIXME -> implement LayerGroup
		App.map.getLayers().add(4, mLocationOverlay);

		App.map.updateMap(true);
		return true;
	}

	/**
	 * Disable "show my location" mode.
	 */
	private boolean disableShowMyLocation() {

		mLocationManager.removeUpdates(this);
		mLocationOverlay.setEnabled(false);

		App.map.getLayers().remove(mLocationOverlay);
		App.map.updateMap(true);

		return true;
	}

	public Location gotoLastKnownPosition() {
		Location location = null;
		float bestAccuracy = Float.MAX_VALUE;

		for (String provider : mLocationManager.getProviders(true)) {
			Location l = mLocationManager.getLastKnownLocation(provider);
			if (l == null)
				continue;

			float accuracy = l.getAccuracy();
			if (accuracy <= 0)
				accuracy = Float.MAX_VALUE;

			if (location == null || accuracy <= bestAccuracy) {
				location = l;
				bestAccuracy = accuracy;
			}
		}

		if (location == null) {
			App.activity.showToastOnUiThread(App.activity
					.getString(R.string.error_last_location_unknown));
			return null;
		}

		MapPosition mapPosition = new MapPosition();
		App.map.getMapPosition(mapPosition);

		if (mapPosition.zoomLevel < SHOW_LOCATION_ZOOM)
			mapPosition.setZoomLevel(SHOW_LOCATION_ZOOM);

		mapPosition.setPosition(location.getLatitude(), location.getLongitude());
		App.map.setMapPosition(mapPosition);
		App.map.updateMap(true);

		return location;
	}

	/*** LocationListener ***/
	@Override
	public void onLocationChanged(Location location) {

		if (mMode == Mode.OFF)
			return;

		double lat = location.getLatitude();
		double lon = location.getLongitude();

		Log.d(TAG, "update location " + lat + ":" + lon);

		if (mSetCenter || mMode == Mode.SNAP) {
			mSetCenter = false;

			GeoPoint point = new GeoPoint(lat, lon);
			App.map.setCenter(point);
			App.map.updateMap(true);
		}

		mLocationOverlay.setPosition(lat, lon, location.getAccuracy());
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

	public void setCenterOnFirstFix() {
		mSetCenter = true;
	}

}
