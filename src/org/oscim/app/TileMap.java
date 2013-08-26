/* Copyright 2010, 2011, 2012 mapsforge.org
 * Copyright 2012 Hannes Janetzek
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

package org.oscim.app;

import org.oscim.android.MapActivity;
import org.oscim.app.location.Compass;
import org.oscim.app.location.LocationDialog;
import org.oscim.app.location.LocationHandler;
import org.oscim.app.preferences.EditPreferences;
import org.oscim.core.GeoPoint;
import org.oscim.overlay.DistanceTouchOverlay;
import org.oscim.view.DebugSettings;
import org.oscim.view.MapView;
import org.osmdroid.location.POI;
import org.osmdroid.overlays.MapEventsReceiver;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

public class TileMap extends MapActivity implements MapEventsReceiver {
	static final String TAG = TileMap.class.getName();

	private static final int DIALOG_ENTER_COORDINATES = 0;
	private static final int DIALOG_LOCATION_PROVIDER_DISABLED = 2;

	//private static final int SELECT_RENDER_THEME_FILE = 1;
	protected static final int POIS_REQUEST = 2;

	public LocationHandler mLocation;

	private Menu mMenu = null;

	Compass mCompass;

	private final MapLayers mMapLayers = new MapLayers();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_tilemap);
		mMapView = (MapView) findViewById(R.id.mapView);
		mMapView.setClickable(true);
		mMapView.setFocusable(true);

		App.map = mMapView;
		App.activity = this;

		mMapLayers.setBaseMap(PreferenceManager.getDefaultSharedPreferences(this));

		mMapView.getOverlays().add(new DistanceTouchOverlay(mMapView, this));

		mCompass = new Compass(this, mMapView);
		mMapView.getOverlays().add(mCompass);

		mLocation = new LocationHandler(this, mCompass);

		App.poiSearch = new POISearch();
		App.routeSearch = new RouteSearch();

		registerForContextMenu(mMapView);

		handleIntent(getIntent(), true);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		handleIntent(intent, false);
	}


	private void handleIntent(Intent intent, boolean start){
		if (intent == null)
			return;

		Uri uri = intent.getData();
		if (uri != null) {
			String scheme = uri.getSchemeSpecificPart();
			Log.d(TAG, "got intent: " + (scheme == null ? "" : scheme));
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.options_menu, menu);
		mMenu = menu;
		toggleMenuCheck();
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case R.id.menu_info_about:
			startActivity(new Intent(this, InfoView.class));
			return true;

		case R.id.menu_position:
			return true;

		case R.id.menu_poi_nearby:
			Intent intent = new Intent(this, POIActivity.class);
			startActivityForResult(intent, TileMap.POIS_REQUEST);
			return true;

		case R.id.menu_compass_enable:
			if (!item.isChecked()) {
				mMapView.getEventLayer().enableRotation(false);
				mMapView.getEventLayer().enableTilt(false);
				mCompass.setEnabled(true);
			} else {
				mMapView.getEventLayer().enableRotation(true);
				mMapView.getEventLayer().enableTilt(true);
				mCompass.setEnabled(false);
			}
			toggleMenuCheck();
			return true;

		case R.id.menu_position_my_location_enable:
			if (!item.isChecked()) {
				mLocation.enableShowMyLocation(true);
			} else {
				mLocation.disableShowMyLocation();
			}
			toggleMenuCheck();
			return true;

		case R.id.menu_position_follow_location:
			if (!item.isChecked()) {
				mLocation.enableSnapToLocation();
			} else {
				mLocation.disableSnapToLocation();
			}
			toggleMenuCheck();
			return true;

		case R.id.menu_layer_mapquest:
		case R.id.menu_layer_naturalearth:
			int bgId = item.getItemId();
			// toggle if already enabled
			if (bgId == mMapLayers.getBackgroundId())
				bgId = -1;

			mMapLayers.setBackgroundMap(bgId);
			mMapView.redrawMap(true);

			toggleMenuCheck();
			return true;

		case R.id.menu_layer_grid:
			mMapLayers.enableGridOverlay(!mMapLayers.isGridEnabled());
			mMapView.redrawMap(true);

			toggleMenuCheck();
			return true;

		case R.id.menu_position_enter_coordinates:
			showDialog(DIALOG_ENTER_COORDINATES);
			return true;

			//case R.id.menu_position_map_center:
			//	MapPosition mapCenter = mBaseLayer.getMapFileCenter();
			//	if (mapCenter != null)
			//		mMapView.setCenter(mapCenter.getGeoPoint());
			//	return true;

		case R.id.menu_preferences:
			startActivity(new Intent(this, EditPreferences.class));
			overridePendingTransition(R.anim.slide_right, R.anim.slide_left2);
			return true;

		default:
			return false;
		}
	}

	private void toggleMenuCheck() {

		mMenu.findItem(R.id.menu_compass_enable)
				.setChecked(mCompass.isEnabled());
		mMenu.findItem(R.id.menu_position_my_location_enable)
				.setChecked(mLocation.isShowMyLocationEnabled());

		if (mLocation.isShowMyLocationEnabled()) {
			mMenu.findItem(R.id.menu_position_follow_location)
					.setVisible(true);
		}

		mMenu.findItem(R.id.menu_position_follow_location)
				.setChecked(mLocation.isSnapToLocationEnabled());

		int bgId = mMapLayers.getBackgroundId();
		mMenu.findItem(R.id.menu_layer_naturalearth)
				.setChecked(bgId == R.id.menu_layer_naturalearth);

		mMenu.findItem(R.id.menu_layer_mapquest)
				.setChecked(bgId == R.id.menu_layer_mapquest);

		mMenu.findItem(R.id.menu_layer_grid)
				.setChecked(mMapLayers.isGridEnabled());
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {

		if (!isPreHoneyComb()) {
			menu.clear();
			onCreateOptionsMenu(menu);
		}

		//if (mMapDatabase == TileSources.MAPSFORGE) {
		//	// menu.findItem(R.id.menu_mapfile).setVisible(true);
		//	menu.findItem(R.id.menu_position_map_center).setVisible(true);
		//} else {
		//	// menu.findItem(R.id.menu_mapfile).setVisible(false);
		//	menu.findItem(R.id.menu_position_map_center).setVisible(false);
		//}

		menu.findItem(R.id.menu_position_map_center).setVisible(false);

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		// forward the event to the MapView
		return mMapView.onTrackballEvent(event);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		switch (requestCode) {
		case POIS_REQUEST:
			Log.d(TAG, "result: POIS_REQUEST");
			if (resultCode == RESULT_OK) {
				int id = intent.getIntExtra("ID", 0);
				Log.d(TAG, "result: POIS_REQUEST: " + id);

				App.poiSearch.poiMarkers.showBubbleOnItem(id);
				POI poi = App.poiSearch.getPOIs().get(id);

				if (poi.bbox != null)
					mMapView.getMapViewPosition().animateTo(poi.bbox);
				else
					mMapView.getMapViewPosition().animateTo(poi.location);
			}
			break;
		//case SELECT_RENDER_THEME_FILE:
		//	if (resultCode == RESULT_OK && intent != null
		//			&& intent.getStringExtra(FilePicker.SELECTED_FILE) != null) {
		//		try {
		//			mMapView.setRenderTheme(intent
		//					.getStringExtra(FilePicker.SELECTED_FILE));
		//		} catch (FileNotFoundException e) {
		//			showToastOnUiThread(e.getLocalizedMessage());
		//		}
		//	}
		//	break;
		default:
			break;
		}

		//if (requestCode == SELECT_MAP_FILE) {
		//	if (resultCode == RESULT_OK) {
		//		if (intent != null) {
		//			if (intent.getStringExtra(FilePicker.SELECTED_FILE) != null) {
		//				map.setMapFile(intent
		//						.getStringExtra(FilePicker.SELECTED_FILE));
		//			}
		//		}
		//	} else if (resultCode == RESULT_CANCELED) {
		//		startActivity(new Intent(this, EditPreferences.class));
		//	}
		//} else if (requestCode == SELECT_RENDER_THEME_FILE && resultCode ==
		//		RESULT_OK
		//		&& intent != null
		//		&& intent.getStringExtra(FilePicker.SELECTED_FILE) != null) {
		//	try {
		//		map.setRenderTheme(intent
		//				.getStringExtra(FilePicker.SELECTED_FILE));
		//	} catch (FileNotFoundException e) {
		//		showToastOnUiThread(e.getLocalizedMessage());
		//	}
		//}
	}

	static boolean isPreHoneyComb() {
		return Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		if (id == DIALOG_ENTER_COORDINATES) {
			if (mLocationDialog == null)
				mLocationDialog = new LocationDialog();

			return mLocationDialog.createDialog(this);

		} else if (id == DIALOG_LOCATION_PROVIDER_DISABLED) {
			builder.setIcon(android.R.drawable.ic_menu_info_details);
			builder.setTitle(R.string.error);
			builder.setMessage(R.string.no_location_provider_available);
			builder.setPositiveButton(R.string.ok, null);
			return builder.create();
		} else {
			// no dialog will be created
			return null;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mLocation.disableShowMyLocation();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mCompass.setEnabled(false);
		// release the wake lock if necessary
		// if (mWakeLock.isHeld()) {
		// mWakeLock.release();
		// }
	}

	LocationDialog mLocationDialog;

	@SuppressWarnings("deprecation")
	@Override
	protected void onPrepareDialog(int id, final Dialog dialog) {
		if (id == DIALOG_ENTER_COORDINATES) {

			mLocationDialog.prepareDialog(mMapView, dialog);

		} else {
			super.onPrepareDialog(id, dialog);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		mMapLayers.setPreferences(preferences);

		if (preferences.getBoolean("fullscreen", false)) {
			Log.i("mapviewer", "FULLSCREEN");
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		} else {
			Log.i("mapviewer", "NO FULLSCREEN");
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		}

		if (preferences.getBoolean("fixOrientation", true)) {
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			// this all returns the orientation which is not currently active?!
			// getWindow().getWindowManager().getDefaultDisplay().getRotation());
			// getWindow().getWindowManager().getDefaultDisplay().getOrientation());
		}

		// try {
		// String textScaleDefault =
		// getString(R.string.preferences_text_scale_default);
		// map.setTextScale(Float.parseFloat(preferences.getString("textScale",
		// textScaleDefault)));
		// } catch (NumberFormatException e) {
		// map.setTextScale(1);
		// }
		// if (preferences.getBoolean("wakeLock", false) && !mWakeLock.isHeld())
		// {
		// mWakeLock.acquire();
		// }

		boolean drawTileFrames = preferences.getBoolean("drawTileFrames", false);
		boolean drawTileCoordinates = preferences.getBoolean("drawTileCoordinates", false);
		boolean disablePolygons = preferences.getBoolean("disablePolygons", false);
		boolean drawUnmatchedWays = preferences.getBoolean("drawUnmatchedWays", false);
		boolean debugLabels = preferences.getBoolean("debugLabels", false);

		DebugSettings cur = mMapView.getDebugSettings();
		if (cur.disablePolygons != disablePolygons
				|| cur.drawTileCoordinates != drawTileCoordinates
				|| cur.drawTileFrames != drawTileFrames
				|| cur.debugTheme != drawUnmatchedWays
				|| cur.debugLabels != debugLabels) {

			DebugSettings debugSettings = new DebugSettings(drawTileCoordinates,
					drawTileFrames, disablePolygons, drawUnmatchedWays, debugLabels);

			mMapView.setDebugSettings(debugSettings);
		}

		mMapView.redrawMap(false);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	/**
	 * Uses the UI thread to display the given text message as toast
	 * notification.
	 * @param text
	 *            the text message to display
	 */
	public void showToastOnUiThread(final String text) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast toast = Toast.makeText(TileMap.this, text, Toast.LENGTH_SHORT);
				toast.show();
			}
		});
	}

	private final static int MAP_MODE_MANUAL = 0;
	private final static int MAP_MODE_COMPASS = 1;
	private final static int MAP_MODE_SHOW_LOCATION = 2;
	private final static int MAP_MODE_SNAP_LOCATION = 3;

	private int mMapMode = MAP_MODE_MANUAL;

	public void toggleLocation(View V) {

		((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(50);

		mMapMode += 1;
		mMapMode %= 4;

		setInteractionMode(mMapMode);
	}

	private void setInteractionMode(int mapMode) {
		switch (mapMode) {
		case MAP_MODE_MANUAL:
			mMapView.getEventLayer().enableRotation(true);
			mMapView.getEventLayer().enableTilt(true);

			mLocation.disableShowMyLocation();

			mCompass.setEnabled(false);
			mCompass.controlOrientation(false);

			App.activity.showToastOnUiThread("Manual");

			break;
		case MAP_MODE_SHOW_LOCATION:
			mMapView.getEventLayer().enableRotation(true);
			mMapView.getEventLayer().enableTilt(true);

			mCompass.setEnabled(false);
			mCompass.controlOrientation(false);

			mLocation.enableShowMyLocation(true);
			App.activity.showToastOnUiThread("Show Location");
			break;
		case MAP_MODE_SNAP_LOCATION:
			mMapView.getEventLayer().enableRotation(true);
			mMapView.getEventLayer().enableTilt(true);

			mLocation.enableSnapToLocation();
			//mCompass.setEnabled(true);

			mCompass.controlOrientation(false);

			App.activity.showToastOnUiThread(App.activity
					.getString(R.string.snap_to_location_enabled));
			break;

		case MAP_MODE_COMPASS:
			mMapView.getEventLayer().enableRotation(false);
			mMapView.getEventLayer().enableTilt(false);

			mCompass.setEnabled(true);
			mCompass.controlOrientation(true);
			mLocation.disableShowMyLocation();

			App.activity.showToastOnUiThread("Compass");

			break;

		default:
			break;
		}

		App.map.redrawMap(true);
	}

	// ----------- Context Menu when clicking on the map
	private GeoPoint mLongPressGeoPoint;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.map_menu, menu);

		if (App.poiSearch.getPOIs().isEmpty())
			menu.removeItem(R.id.menu_poi_clear);

		if (App.routeSearch.isEmpty())
			menu.removeItem(R.id.menu_route_clear);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (App.poiSearch.onContextItemSelected(item, mLongPressGeoPoint))
			return true;

		if (App.routeSearch.onContextItemSelected(item, mLongPressGeoPoint))
			return true;

		return super.onContextItemSelected(item);
	}

	// ------------ MapEventsReceiver implementation
	@Override
	public boolean singleTapUpHelper(GeoPoint p) {
		App.poiSearch.singleTapUp();
		App.routeSearch.singleTapUp();
		return false;
	}

	@Override
	public boolean longPressHelper(GeoPoint p) {
		mLongPressGeoPoint = p;
		openContextMenu(mMapView);
		return true;
	}

	@Override
	public boolean longPressHelper(final GeoPoint p1, final GeoPoint p2) {
		App.routeSearch.showRoute(p1, p2);
		return true;
	}
}
