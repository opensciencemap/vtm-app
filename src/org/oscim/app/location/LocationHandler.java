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

	private final static int DIALOG_LOCATION_PROVIDER_DISABLED = 2;
	private final static int SHOW_LOCATION_ZOOM = 15;

	private final LocationManager mLocationManager;
	private final LocationOverlay mLocationOverlay;

	private boolean mShowMyLocation;
	private boolean mSnapToLocation;

	private boolean mSetCenter;

	public LocationHandler(TileMap tileMap, Compass compass) {
		mLocationManager = (LocationManager) tileMap
				.getSystemService(Context.LOCATION_SERVICE);

		mLocationOverlay = new LocationOverlay(App.map, compass);
	}

	@SuppressWarnings("deprecation")
	public boolean enableShowMyLocation(boolean centerAtFirstFix) {

		if (mShowMyLocation)
			return true;

		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		String bestProvider = mLocationManager.getBestProvider(criteria, true);

		if (bestProvider == null) {
			App.activity.showDialog(DIALOG_LOCATION_PROVIDER_DISABLED);
			return false;
		}

		mShowMyLocation = true;
		mSetCenter = centerAtFirstFix;

		mLocationManager.requestLocationUpdates(bestProvider, 10000, 10, this);

		Location location = gotoLastKnownPosition();
		if (location == null)
			return false;

		mLocationOverlay.setEnabled(true);
		mLocationOverlay.setPosition(location.getLatitude(),
				location.getLongitude(),
				location.getAccuracy());

		App.map.getOverlays().add(2, mLocationOverlay);

		App.map.redrawMap(true);
		return true;
	}

	/**
	 * Disable "show my location" mode.
	 */
	public boolean disableShowMyLocation() {
		if (!mShowMyLocation)
			return false;

		mShowMyLocation = false;

		disableSnapToLocation();

		mLocationManager.removeUpdates(this);
		mLocationOverlay.setEnabled(false);

		App.map.getOverlays().remove(mLocationOverlay);
		App.map.redrawMap(true);

		return true;
	}

	public Location gotoLastKnownPosition() {
		Location location = null;

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
			return null;
		}

		MapPosition mapPosition = new MapPosition();
		mapPosition.setPosition(location.getLatitude(), location.getLongitude());
		mapPosition.setZoomLevel(SHOW_LOCATION_ZOOM);

		App.map.setMapPosition(mapPosition);
		App.map.redrawMap(true);

		return location;
	}

	public boolean isShowMyLocationEnabled() {
		return mShowMyLocation;
	}

	public boolean isSnapToLocationEnabled() {
		return mSnapToLocation;
	}

	public void disableSnapToLocation() {
		if (mSnapToLocation) {
			mSnapToLocation = false;

			App.map.getEventLayer().enableMove(true);
		}
	}

	public void enableSnapToLocation() {
		if (!mSnapToLocation) {
			mSnapToLocation = true;
			App.map.getEventLayer().enableMove(false);
			gotoLastKnownPosition();
		}
	}

	boolean isFirstCenter() {
		return mSetCenter;
	}

	void setFirstCenter(boolean center) {
		mSetCenter = center;
	}

	/*** LocationListener ***/
	@Override
	public void onLocationChanged(Location location) {

		if (!mShowMyLocation)
			return;

		double lat = location.getLatitude();
		double lon = location.getLongitude();

		Log.d(TAG, "update location " + lat + ":" + lon);

		if (mSetCenter || mSnapToLocation) {
			mSetCenter = false;

			GeoPoint point = new GeoPoint(lat, lon);
			App.map.setCenter(point);
			App.map.redrawMap(true);
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

}
