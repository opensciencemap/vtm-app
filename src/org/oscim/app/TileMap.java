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
import org.oscim.app.compass.Compass;
import org.oscim.app.location.LocationDialog;
import org.oscim.app.location.LocationHandler;
import org.oscim.app.preferences.EditPreferences;
import org.oscim.cache.CacheFileManager;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.layers.overlay.GenericOverlay;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.layers.tile.bitmap.MapQuestAerial;
import org.oscim.layers.tile.bitmap.NaturalEarth;
import org.oscim.layers.tile.vector.MapTileLayer;
import org.oscim.overlay.DistanceTouchOverlay;
import org.oscim.renderer.layers.GridRenderLayer;
import org.oscim.theme.InternalRenderTheme;
import org.oscim.tilesource.ITileCache;
import org.oscim.tilesource.TileSource;
import org.oscim.tilesource.TileSources;
import org.oscim.tilesource.common.UrlTileSource;
import org.oscim.tilesource.mapfile.MapFileTileSource;
import org.oscim.tilesource.mapnik.MapnikVectorTileSource;
import org.oscim.tilesource.oscimap.OSciMap1TileSource;
import org.oscim.tilesource.oscimap2.OSciMap2TileSource;
import org.oscim.tilesource.oscimap4.OSciMap4TileSource;
import org.oscim.view.DebugSettings;
import org.oscim.view.MapView;
import org.osmdroid.location.POI;
import org.osmdroid.overlays.MapEventsReceiver;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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

	private static final String CACHE_DIRECTORY = "/Android/data/org.oscim.app/cache/";

	// Intents
	//private static final int SELECT_RENDER_THEME_FILE = 1;
	protected static final int POIS_REQUEST = 2;

	public LocationHandler mLocation;

	private TileSources mMapDatabase;

	private Menu mMenu = null;

	Compass mCompass;
	private MapTileLayer mBaseLayer;

	boolean naturalOn;
	boolean mapQuestOn;

	private ITileCache mCache;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

		setContentView(R.layout.activity_tilemap);
		mMapView = (MapView) findViewById(R.id.mapView);

		setMapDatabase(preferences);

		App.map = mMapView;
		App.activity = this;

		//App.map.setBackgroundMap(new BitmapTileLayer(App.map, NaturalEarth.INSTANCE));

		mMapView.setClickable(true);
		mMapView.setFocusable(true);

		mLocation = new LocationHandler(this);

		mMapView.getOverlays().add(new DistanceTouchOverlay(mMapView, this));

		mCompass = new Compass(this, mMapView);
		mMapView.getOverlays().add(mCompass);

		App.poiSearch = new POISearch();
		App.routeSearch = new RouteSearch();

		registerForContextMenu(mMapView);

		final Intent intent = getIntent();
		if (intent != null) {
			Uri uri = intent.getData();
			if (uri != null) {
				String scheme = uri.getSchemeSpecificPart();
				Log.d(TAG, "got intent " + (scheme == null ? "" : scheme));
			}
		}
	}

	private void setMapDatabase(SharedPreferences preferences) {
		TileSources tileSourceNew;
		String dbname = preferences.getString("mapDatabase",
				TileSources.OPENSCIENCEMAP2.name());

		try {
			tileSourceNew = TileSources.valueOf(dbname);
		} catch (IllegalArgumentException e) {
			showToastOnUiThread("invalid db: " + dbname);
			tileSourceNew = TileSources.OPENSCIENCEMAP2;
		}

		if (tileSourceNew != mMapDatabase) {
			Log.d(TAG, "set tile source " + tileSourceNew);

			TileSource tileSource = null;

			switch (tileSourceNew) {
			case OPENSCIENCEMAP1:
				tileSource = new OSciMap1TileSource();
				tileSource.setOption("url", "http://city.informatik.uni-bremen.de/osmstache/test");
				break;
			case OPENSCIENCEMAP2:
				tileSource = new OSciMap2TileSource();
				tileSource.setOption("url", "http://city.informatik.uni-bremen.de/osci/map-live");
				break;
			case OPENSCIENCEMAP4:
				tileSource = new OSciMap4TileSource();
				tileSource.setOption("url", "http://city.informatik.uni-bremen.de/tiles/vtm");
				break;
			case MAPSFORGE:
				tileSource = new MapFileTileSource();
				tileSource.setOption("file", "/storage/sdcard0/germany.map");
				break;
			case MAPNIK_VECTOR:
				tileSource = new MapnikVectorTileSource();
				tileSource.setOption("url", "http://d1s11ojcu7opje.cloudfront.net/dev/764e0b8d");
				break;

			default:
				break;
			}

			if (tileSource instanceof UrlTileSource) {
				mCache = new CacheFileManager(this);
				mCache.setStoragePath(CACHE_DIRECTORY + dbname);
				mCache.setCacheSize(512 * (1 << 10));
				tileSource.setCache(mCache);
			} else{
				mCache = null;
			}

			if (mBaseLayer == null) {
				mBaseLayer = mMapView.setBaseMap(tileSource);
			} else
				mBaseLayer.setTileSource(tileSource);

			mMapDatabase = tileSourceNew;
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
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

		//mMenu.findItem(R.id.menu_position_my_location_enable).setChecked(mLocation.ismSnapToLocation());
		switch (item.getItemId()) {

		case R.id.menu_info_about:
			startActivity(new Intent(this, InfoView.class));
			return true;

		case R.id.menu_position:
			return true;

		case R.id.menu_rotation_enable:
			if (!item.isChecked()) {
				item.setChecked(true);

				mCompass.stop();
				mMapView.enableRotation(true);
				//compass.stop();
			} else {
				item.setChecked(false);
				mMapView.enableRotation(false);
				mMapView.enableRotation(true);
			}
			toggleMenuCheck();
			return true;

		case R.id.menu_poi_nearby:
			Intent intent = new Intent(this, POIActivity.class);
			startActivityForResult(intent, TileMap.POIS_REQUEST);
			return true;

		case R.id.menu_compass_enable:
			if (!item.isChecked()) {
				item.setChecked(true);
				mMapView.enableRotation(false);
				mCompass.start();

				//mMapView.setRotation(0);
				//ompass.start();
			} else {
				item.setChecked(false);
				mCompass.stop();

				//compass.stop();

			}
			toggleMenuCheck();
			return true;

		case R.id.menu_position_my_location_enable:
			if (!item.isChecked()) {
				item.setChecked(true);
				//mMapView.enableRotation(false);
				//mMapView.enableCompass(true);
				mLocation.enableShowMyLocation(true);

				//mMapView.setRotation(0);
				//ompass.start();
			} else {
				item.setChecked(false);
				mLocation.disableShowMyLocation();

				//mMapView.enableCompass(false);

				//compass.stop();

			}
			toggleMenuCheck();
			return true;

		case R.id.item1:
			if (!item.isChecked()) {
				item.setChecked(true);
				//mMapView.enableRotation(false);
				//mMapView.enableCompass(true);
				mLocation.enableSnapToLocation(true);

				//mMapView.setRotation(0);
				//ompass.start();
			} else {
				item.setChecked(false);
				mLocation.disableSnapToLocation(true);
				//mMapView.enableCompass(false);

				//compass.stop();

			}
			toggleMenuCheck();
			return true;

		case R.id.item2:
			if (!item.isChecked()) {
				item.setChecked(true);
				//mMapView.enableRotation(false);
				//mMapView.enableCompass(true);
				//mLocation.enableSnapToLocation(true);
				mLocation.ts.StopAnimation = false;
				//mMapView.setRotation(0);
				//ompass.start();
			} else {
				item.setChecked(false);
				mLocation.ts.StopAnimation = true;
				//mLocation.disableSnapToLocation(true);
				//mMapView.enableCompass(false);

				//compass.stop();

			}
			toggleMenuCheck();
			return true;

		case R.id.item4:
			if (!item.isChecked()) {
				item.setChecked(true);
				//mMapView.enableRotation(false);
				//mMapView.enableCompass(true);
				//mLocation.enableSnapToLocation(true);
				//	mLocation.ts.StopAnimation=false;
				//mMapView.setRotation(0);
				//ompass.start();

				naturalOn = true;
				mapQuestOn = false;
				App.map.getLayerManager().remove(lastAdd);
				lastAdd = new BitmapTileLayer(App.map, NaturalEarth.INSTANCE);

				App.map.setBackgroundMap(lastAdd);
				App.map.render();
			} else {
				item.setChecked(false);
				//mLocation.disableSnapToLocation(true);
				//mMapView.enableCompass(false);
				App.map.getLayerManager().remove(lastAdd);
				App.map.render();
				//compass.stop();

			}
			toggleMenuCheck();
			return true;

		case R.id.item5:
			if (!item.isChecked()) {
				item.setChecked(true);
				//mMapView.enableRotation(false);
				//mMapView.enableCompass(true);
				//mLocation.enableSnapToLocation(true);
				//	mLocation.ts.StopAnimation=false;
				//mMapView.setRotation(0);
				//ompass.start();
				mapQuestOn = true;
				naturalOn = false;
				App.map.getLayerManager().remove(lastAdd);

				lastAdd = new BitmapTileLayer(App.map, MapQuestAerial.INSTANCE);
				App.map.setBackgroundMap(lastAdd);
				App.map.render();
			} else {
				item.setChecked(false);
				//mLocation.disableSnapToLocation(true);
				//mMapView.enableCompass(false);
				App.map.getLayerManager().remove(lastAdd);
				//compass.stop();
				App.map.render();

			}
			toggleMenuCheck();
			return true;

		case R.id.item6:
			if (!item.isChecked()) {
				item.setChecked(true);;
				GridShown = true;
				App.map.getOverlays().remove(mGridOverlay);
				mGridOverlay = new GenericOverlay(mMapView, new GridRenderLayer(mMapView));

				App.map.getOverlays().add(mGridOverlay);

				App.map.render();
			} else {
				item.setChecked(false);

				App.map.getOverlays().remove(mGridOverlay);

				GridShown = false;
				App.map.render();

			}
			toggleMenuCheck();
			return true;

		case R.id.menu_position_enter_coordinates:
			showDialog(DIALOG_ENTER_COORDINATES);
			return true;

		case R.id.menu_position_map_center:
			MapPosition mapCenter = mBaseLayer.getMapFileCenter();
			if (mapCenter != null)
				mMapView.setCenter(mapCenter.getGeoPoint());
			return true;

		case R.id.menu_preferences:
			startActivity(new Intent(this, EditPreferences.class));
			overridePendingTransition(R.anim.slide_right, R.anim.slide_left2);
			return true;

		default:
			return false;
		}
	}

	GenericOverlay mGridOverlay;
	BitmapTileLayer lastAdd;

	private void toggleMenuCheck() {
		mMenu.findItem(R.id.menu_rotation_enable).setChecked(mMapView.getRotationEnabled());
		mMenu.findItem(R.id.menu_compass_enable).setChecked(mCompass.isEnabled());
		mMenu.findItem(R.id.menu_position_my_location_enable).setChecked(
				mLocation.isShowMyLocationEnabled());
		if (mLocation.isShowMyLocationEnabled()) {

			mMenu.findItem(R.id.item1).setVisible(true);

			mMenu.findItem(R.id.item2).setVisible(true);

		}

		mMenu.findItem(R.id.item1).setChecked(mLocation.isSnapToLocationEnabled());
		if (mLocation.ts != null)
			mMenu.findItem(R.id.item2).setChecked(!mLocation.ts.StopAnimation);

		mMenu.findItem(R.id.item4).setChecked(naturalOn);
		mMenu.findItem(R.id.item5).setChecked(mapQuestOn);
		mMenu.findItem(R.id.item6).setChecked(GridShown);

		App.map.render();
	}

	boolean GridShown;

	//private static void toggleMenuItem(Menu menu, int id, int id2, boolean enable) {
	//	menu.findItem(id).setVisible(enable);
	//	menu.findItem(id).setEnabled(enable);
	//	menu.findItem(id2).setVisible(!enable);
	//	menu.findItem(id2).setEnabled(!enable);
	//}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {

		if (!isPreHoneyComb()) {
			menu.clear();
			onCreateOptionsMenu(menu);
		}

		if (mMapDatabase == TileSources.MAPSFORGE) {
			// menu.findItem(R.id.menu_mapfile).setVisible(true);
			menu.findItem(R.id.menu_position_map_center).setVisible(true);
		} else {
			// menu.findItem(R.id.menu_mapfile).setVisible(false);
			menu.findItem(R.id.menu_position_map_center).setVisible(false);
		}

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

		// if (requestCode == SELECT_MAP_FILE) {
		// if (resultCode == RESULT_OK) {
		//
		// location.disableSnapToLocation(true);
		//
		// if (intent != null) {
		// if (intent.getStringExtra(FilePicker.SELECTED_FILE) != null) {
		// map.setMapFile(intent
		// .getStringExtra(FilePicker.SELECTED_FILE));
		// }
		// }
		// } else if (resultCode == RESULT_CANCELED) {
		// startActivity(new Intent(this, EditPreferences.class));
		// }
		// } else
		// if (requestCode == SELECT_RENDER_THEME_FILE && resultCode ==
		// RESULT_OK
		// && intent != null
		// && intent.getStringExtra(FilePicker.SELECTED_FILE) != null) {
		// try {
		// map.setRenderTheme(intent
		// .getStringExtra(FilePicker.SELECTED_FILE));
		// } catch (FileNotFoundException e) {
		// showToastOnUiThread(e.getLocalizedMessage());
		// }
		// }
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

		if (preferences.contains("mapDatabase")) {
			setMapDatabase(preferences);
		}
		if (preferences.contains("theme")) {
			String name = preferences.getString("theme",
					"OSMARENDER");
			InternalRenderTheme theme = null;

			try {
				theme = InternalRenderTheme.valueOf(name);
			} catch (IllegalArgumentException e) {
			}
			if (theme == null)
				mBaseLayer.setRenderTheme(InternalRenderTheme.DEFAULT);
			else
				mBaseLayer.setRenderTheme(theme);
		} else {
			mBaseLayer.setRenderTheme(InternalRenderTheme.DEFAULT);
		}

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

		// default cache size 20MB
		int cacheSize = preferences.getInt("cacheSize", 20);

		if (mCache != null)
			mCache.setCacheSize(cacheSize * (1 << 20));

		// try {
		// String textScaleDefault =
		// getString(R.string.preferences_text_scale_default);
		// map.setTextScale(Float.parseFloat(preferences.getString("textScale",
		// textScaleDefault)));
		// } catch (NumberFormatException e) {
		// map.setTextScale(1);
		// }

		// MapScaleBar mapScaleBar = mapView.getMapScaleBar();
		// mapScaleBar.setShowMapScaleBar(preferences.getBoolean("showScaleBar",
		// false));
		// String scaleBarUnitDefault =
		// getString(R.string.preferences_scale_bar_unit_default);
		// String scaleBarUnit = preferences.getString("scaleBarUnit",
		// scaleBarUnitDefault);
		// mapScaleBar.setImperialUnits(scaleBarUnit.equals("imperial"));

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
				Toast toast = Toast.makeText(TileMap.this, text, Toast.LENGTH_LONG);
				toast.show();
			}
		});
	}

	// ----------- Context Menu when clicking on the map
	private GeoPoint mLongPressGeoPoint;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		// Log.d(TAG, "create context menu");

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.map_menu, menu);

		if (App.poiSearch.getPOIs().isEmpty())
			menu.removeItem(R.id.menu_poi_clear);

		if (App.routeSearch.isEmpty())
			menu.removeItem(R.id.menu_route_clear);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		// Log.d(TAG, "context menu item selected " + item.getItemId());

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

	public Compass getCompass() {
		return mCompass;
	}

}
