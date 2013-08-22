package org.oscim.app;

import org.oscim.cache.CacheFileManager;
import org.oscim.layers.tile.vector.MapTileLayer;
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

import android.content.SharedPreferences;
import android.util.Log;

public class MapLayers {

	private static final String TAG = MapLayers.class.getName();

	private static final String CACHE_DIRECTORY = "/Android/data/org.oscim.app/cache/";

	private MapTileLayer mBaseLayer;
	private TileSources mMapDatabase;
	private ITileCache mCache;

	void setBaseMap(SharedPreferences preferences) {
		TileSources tileSourceNew;
		String dbname = preferences.getString("mapDatabase",
				TileSources.OPENSCIENCEMAP2.name());

		try {
			tileSourceNew = TileSources.valueOf(dbname);
		} catch (IllegalArgumentException e) {
			App.activity.showToastOnUiThread("invalid db: " + dbname);
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
				mCache = new CacheFileManager(App.activity);
				mCache.setStoragePath(CACHE_DIRECTORY + dbname);
				mCache.setCacheSize(512 * (1 << 10));
				tileSource.setCache(mCache);
			} else {
				mCache = null;
			}

			if (mBaseLayer == null) {
				mBaseLayer = App.map.setBaseMap(tileSource);
			} else
				mBaseLayer.setTileSource(tileSource);

			mMapDatabase = tileSourceNew;
		}
	}

	void setPreferences(SharedPreferences preferences){
		if (preferences.contains("mapDatabase")) {
			setBaseMap(preferences);
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

		// default cache size 20MB
		int cacheSize = preferences.getInt("cacheSize", 20);

		if (mCache != null)
			mCache.setCacheSize(cacheSize * (1 << 20));

	}
}
