package org.oscim.app;

import org.oscim.android.cache.TileCache;
import org.oscim.layers.GenericLayer;
import org.oscim.layers.Layer;
import org.oscim.layers.tile.BitmapTileLayer;
import org.oscim.layers.tile.vector.BuildingLayer;
import org.oscim.layers.tile.vector.VectorTileLayer;
import org.oscim.layers.tile.vector.labeling.LabelLayer;
import org.oscim.renderer.GridRenderer;
import org.oscim.theme.InternalRenderTheme;
import org.oscim.tiling.source.ITileCache;
import org.oscim.tiling.source.TileSource;
import org.oscim.tiling.source.bitmap.DefaultSources.ImagicoLandcover;
import org.oscim.tiling.source.bitmap.DefaultSources.NaturalEarth;
import org.oscim.tiling.source.common.UrlTileSource;
import org.oscim.tiling.source.mapfile.MapFileTileSource;
import org.oscim.tiling.source.mapnik.MapnikVectorTileSource;
import org.oscim.tiling.source.oscimap4.OSciMap4TileSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.SharedPreferences;

public class MapLayers {

	final static Logger log = LoggerFactory.getLogger(MapLayers.class);

	private static final String CACHE_DIRECTORY = "/Android/data/org.oscim.app/cache/";

	abstract static class Config {
		final String name;

		public Config(String name) {
			this.name = name;
		}

		abstract TileSource init();
	}

	static Config[] configs = new Config[] {
	        new Config("OPENSCIENCEMAP4") {
		        TileSource init() {
			        return new OSciMap4TileSource();
		        }
	        },
	        new Config("MAPSFORGE") {
		        TileSource init() {
			        return new MapFileTileSource()
			            .setOption("file", "/storage/sdcard0/germany.map");
		        }
	        },
	        new Config("MAPNIK_VECTOR") {
		        TileSource init() {
			        return new MapnikVectorTileSource();
		        }
	        } };

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

		if (dbname.equals(mMapDatabase) && mBaseLayer != null)
			return;

		TileSource tileSource = null;
		for (Config c : configs)
			if (c.name.equals(dbname))
				tileSource = c.init();

		if (tileSource == null) {
			tileSource = configs[0].init();
			dbname = configs[0].name;
			preferences.edit().putString("mapDatabase", dbname).commit();
		}

		if (tileSource instanceof UrlTileSource) {
			mCache = new TileCache(App.activity, CACHE_DIRECTORY, dbname);
			mCache.setCacheSize(512 * (1 << 10));
			tileSource.setCache(mCache);
		} else {
			mCache = null;
		}

		if (mBaseLayer == null) {
			mBaseLayer = App.map.setBaseMap(tileSource);
			App.map.getLayers().add(2, new BuildingLayer(App.map, mBaseLayer.getTileRenderer()));
			App.map.getLayers().add(3, new LabelLayer(App.map, mBaseLayer.getTileRenderer()));
		} else
			mBaseLayer.setTileSource(tileSource);

		mMapDatabase = dbname;
	}

	void setPreferences(SharedPreferences preferences) {
		setBaseMap(preferences);

		InternalRenderTheme theme = InternalRenderTheme.DEFAULT;
		if (preferences.contains("theme")) {
			String name = preferences.getString("theme", "DEFAULT");
			try {
				theme = InternalRenderTheme.valueOf(name);
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
				//mBackgroundLayer = new BitmapTileLayer(App.map, new MapQuestAerial());
				mBackgroundLayer = new BitmapTileLayer(App.map, new ImagicoLandcover());
				break;

			case R.id.menu_layer_naturalearth:
				mBackgroundLayer = new BitmapTileLayer(App.map, new NaturalEarth());
				break;
			default:
				mBackgroundLayer = mBackroundPlaceholder;
				id = -1;
		}

		if (mBackgroundLayer instanceof BitmapTileLayer)
			App.map.setBackgroundMap((BitmapTileLayer) mBackgroundLayer);
		else
			App.map.getLayers().add(1, mBackroundPlaceholder);

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
