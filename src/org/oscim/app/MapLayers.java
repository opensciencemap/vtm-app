package org.oscim.app;

import org.oscim.cache.TileCache;
import org.oscim.layers.GenericLayer;
import org.oscim.layers.Layer;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.layers.tile.bitmap.MapQuestAerial;
import org.oscim.layers.tile.bitmap.NaturalEarth;
import org.oscim.layers.tile.vector.BuildingLayer;
import org.oscim.layers.tile.vector.VectorTileLayer;
import org.oscim.layers.tile.vector.labeling.LabelLayer;
import org.oscim.renderer.GridRenderer;
import org.oscim.theme.InternalRenderTheme;
import org.oscim.theme.ThemeLoader;
import org.oscim.tiling.source.ITileCache;
import org.oscim.tiling.source.TileSource;
import org.oscim.tiling.source.common.UrlTileSource;
import org.oscim.tiling.source.mapfile.MapFileTileSource;
import org.oscim.tiling.source.mapnik.MapnikVectorTileSource;
import org.oscim.tiling.source.oscimap.OSciMap1TileSource;
import org.oscim.tiling.source.oscimap2.OSciMap2TileSource;
import org.oscim.tiling.source.oscimap4.OSciMap4TileSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.SharedPreferences;

public class MapLayers {

	final static Logger log = LoggerFactory.getLogger(MapLayers.class);

	private static final String CACHE_DIRECTORY = "/Android/data/org.oscim.app/cache/";

	private VectorTileLayer mBaseLayer;
	private String mMapDatabase;
	private ITileCache mCache;

	private GenericLayer mGridOverlay;
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
		String dbname = preferences.getString("mapDatabase", "OPENSCIENCEMAP4");

		if (dbname == mMapDatabase)
			return;

		TileSource tileSource = null;

		if ("OPENSCIENCEMAP1".equals(dbname)) {
			tileSource = new OSciMap1TileSource();
			tileSource.setOption("url", "http://opensciencemap.org/osmstache/test");
		} else if ("OPENSCIENCEMAP2".equals(dbname)) {
			tileSource = new OSciMap2TileSource();
			tileSource.setOption("url", "http://opensciencemap.org/osci/map-live");
		} else if ("OPENSCIENCEMAP4".equals(dbname)) {
			tileSource = new OSciMap4TileSource();
			tileSource.setOption("url", "http://opensciencemap.org/tiles/vtm");
		} else if ("MAPSFORGE".equals(dbname)) {
			tileSource = new MapFileTileSource();
			tileSource.setOption("file", "/storage/sdcard0/germany.map");
		} else if ("MAPNIK_VECTOR".equals(dbname)) {
			tileSource = new MapnikVectorTileSource();
			tileSource.setOption("url", "http://d1s11ojcu7opje.cloudfront.net/dev/764e0b8d");
		} else {
			log.debug("no matching tilesource for: " + dbname);
			return;
		}

		if (tileSource instanceof UrlTileSource) {
			mCache = new TileCache(App.activity, CACHE_DIRECTORY, dbname);
			mCache.setCacheSize(512 * (1 << 10));
			//tileSource.setCache(mCache);
		} else {
			mCache = null;
		}

		if (mBaseLayer == null) {
			mBaseLayer = App.map.setBaseMap(tileSource);
			App.map.getLayers().add(2,
			                        new BuildingLayer(App.map, mBaseLayer.getTileLayer()));
			App.map.getLayers().add(3, new LabelLayer(App.map, mBaseLayer.getTileLayer()));
		} else
			mBaseLayer.setTileSource(tileSource);

		mMapDatabase = dbname;
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
				mGridOverlay = new GenericLayer(App.map, new GridRenderer());

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

	public void deleteCache() {
		if (mCache != null)
			mCache.setCacheSize(0);
	}
}
