package org.oscim.app;

import org.oscim.cache.CacheFileManager;
import org.oscim.layers.Layer;
import org.oscim.layers.labeling.LabelLayer;
import org.oscim.layers.overlay.BuildingOverlay;
import org.oscim.layers.overlay.GenericOverlay;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.layers.tile.bitmap.MapQuestAerial;
import org.oscim.layers.tile.bitmap.NaturalEarth;
import org.oscim.layers.tile.vector.MapTileLayer;
import org.oscim.renderer.layers.GridRenderLayer;
import org.oscim.theme.InternalRenderTheme;
import org.oscim.theme.ThemeLoader;
import org.oscim.tilesource.ITileCache;
import org.oscim.tilesource.TileSource;
import org.oscim.tilesource.TileSources;
import org.oscim.tilesource.common.UrlTileSource;
import org.oscim.tilesource.mapfile.MapFileTileSource;
import org.oscim.tilesource.mapnik.MapnikVectorTileSource;
import org.oscim.tilesource.oscimap.OSciMap1TileSource;
import org.oscim.tilesource.oscimap2.OSciMap2TileSource;
import org.oscim.tilesource.oscimap4.OSciMap4TileSource;

import android.content.SharedPreferences;
import android.util.Log;

public class MapLayers {

	private static final String TAG = MapLayers.class.getName();

	private static final String CACHE_DIRECTORY = "/Android/data/org.oscim.app/cache/";

	private MapTileLayer mBaseLayer;
	private TileSources mMapDatabase;
	private ITileCache mCache;

	private GenericOverlay mGridOverlay;
	private boolean mGridEnabled;

	// FIXME -> implement LayerGroup
	private int mBackgroundId = -2;
	private Layer mBackroundPlaceholder;
	private Layer mBackgroundLayer;

	public MapLayers() {
		mBackroundPlaceholder = new Layer(null) {
		};
		setBackgroundMap(-1);
	}

	void setBaseMap(SharedPreferences preferences) {
		TileSources tileSourceNew;
		String dbname = preferences.getString("mapDatabase",
				TileSources.OPENSCIENCEMAP4.name());

		try {
			tileSourceNew = TileSources.valueOf(dbname);
		} catch (IllegalArgumentException e) {
			tileSourceNew = TileSources.OPENSCIENCEMAP4;
		}

		if (tileSourceNew == mMapDatabase)
			return;

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
			mCache = new CacheFileManager(App.activity);
			mCache.setStoragePath(CACHE_DIRECTORY + dbname);
			mCache.setCacheSize(512 * (1 << 10));
			//tileSource.setCache(mCache);
		} else {
			mCache = null;
		}

		if (mBaseLayer == null) {
			mBaseLayer = App.map.setBaseMap(tileSource);
			App.map.getLayers().add(2,
					new BuildingOverlay(App.map, mBaseLayer.getTileLayer()));
			App.map.getLayers().add(3, new LabelLayer(App.map, mBaseLayer.getTileLayer()));
		} else
			mBaseLayer.setTileSource(tileSource);

		mMapDatabase = tileSourceNew;
	}

	void setPreferences(SharedPreferences preferences) {
		if (preferences.contains("mapDatabase"))
			setBaseMap(preferences);

		InternalRenderTheme theme = InternalRenderTheme.DEFAULT;
		if (preferences.contains("theme")) {
			String name = preferences.getString("theme", "DEFAULT");
			try {
				theme = InternalRenderTheme.valueOf(name);
				mBaseLayer.setRenderTheme(ThemeLoader.load(theme));
			} catch (IllegalArgumentException e) {
				theme = InternalRenderTheme.DEFAULT;
			}
		}

		App.map.setTheme(theme);

		// default cache size 20MB
		int cacheSize = preferences.getInt("cacheSize", 20);

		if (mCache != null)
			mCache.setCacheSize(cacheSize * (1 << 20));

	}

	void enableGridOverlay(boolean enable) {
		if (mGridEnabled == enable)
			return;

		if (enable) {
			if (mGridOverlay == null)
				mGridOverlay = new GenericOverlay(App.map, new GridRenderLayer());

			App.map.getLayers().add(mGridOverlay);
		} else {
			App.map.getLayers().remove(mGridOverlay);
		}

		mGridEnabled = enable;
		App.map.updateMap(true);
	}

	boolean isGridEnabled() {
		return mGridEnabled;
	}

	void setBackgroundMap(int id) {
		if (id == mBackgroundId)
			return;

		App.map.getLayers().remove(mBackgroundLayer);
		mBackgroundLayer = null;

		switch (id) {
		case R.id.menu_layer_mapquest:
			mBackgroundLayer = new BitmapTileLayer(App.map, MapQuestAerial.INSTANCE);
			break;

		case R.id.menu_layer_naturalearth:
			mBackgroundLayer = new BitmapTileLayer(App.map, NaturalEarth.INSTANCE);
			break;
		default:
			mBackgroundLayer = mBackroundPlaceholder;
			id = -1;
		}

		if (mBackgroundLayer instanceof BitmapTileLayer)
			App.map.setBackgroundMap((BitmapTileLayer) mBackgroundLayer);
		else
			App.map.getLayers().add(0, mBackroundPlaceholder);

		mBackgroundId = id;
	}

	int getBackgroundId() {
		return mBackgroundId;
	}
}
