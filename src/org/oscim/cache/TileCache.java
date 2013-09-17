/*
 * Copyright 2013 OpenScienceMap
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
package org.oscim.cache;

import java.io.File;
import java.util.ArrayList;

import org.oscim.core.Tile;
import org.oscim.tiling.source.ITileCache;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

/**
 * FIXME: REWRITE
 */
@SuppressLint("DefaultLocale")
public class TileCache implements ITileCache {
	private final static String TAG = TileCache.class.getName();
	private final static boolean DEBUG = false;

	// size of cache in bytes
	private long mCacheLimit;

	//private String CACHE_DIRECTORY;
	private static final String CACHE_FILE = "%d-%d-%d.tile";

	final TileStats mTileStats;
	private File mCacheDir;

	private volatile long mCacheSize = 0;
	private Context mContext;

	private ArrayList<Tile> mCommitHitList;

	public TileCache(Context context, String cacheDirectory, String dbName) {
		mContext = context;
		mTileStats = new TileStats(context);
		mTileStats.open();

		setStoragePath(cacheDirectory + dbName);

		// FIXME get size from Database or read once in asyncTask!
		// also use asyncTask for limiting cache:
		// for now check only once on initialization:

		// todo commit on app pause/destroy
		mCommitHitList = new ArrayList<Tile>(100);
	}

	private static File createDirectory(String pathName) {
		File file = new File(pathName);
		if (!file.exists() && !file.mkdirs()) {
			throw new IllegalArgumentException("could not create directory: " + file);
		} else if (!file.isDirectory()) {
			throw new IllegalArgumentException("not a directory: " + file);
		} else if (!file.canRead()) {
			throw new IllegalArgumentException("cannot read directory: " + file);
		} else if (!file.canWrite()) {
			throw new IllegalArgumentException("cannot write directory: " + file);
		}
		return file;
	}

	@Override
	public synchronized CacheFile writeTile(Tile tile) {
		File f = new File(mCacheDir, String.format(CACHE_FILE,
				Integer.valueOf(tile.zoomLevel),
				Integer.valueOf(tile.tileX),
				Integer.valueOf(tile.tileY)));

		addTileHit(tile);
		cacheCheck(tile);

		return new CacheFile(this, tile, f);
	}

	private void addTileHit(Tile t) {
		mCommitHitList.add(t);

		if (mCommitHitList.size() > 100) {
			Tile[] tiles = new Tile[mCommitHitList.size()];
			tiles = mCommitHitList.toArray(tiles);
			mCommitHitList.clear();

			mTileStats.setTileHit(tiles);
		}
	}

	@Override
	public synchronized ITileCache.TileReader getTile(Tile tile) {

		File f = new File(mCacheDir, String.format(CACHE_FILE,
				Integer.valueOf(tile.zoomLevel),
				Integer.valueOf(tile.tileX),
				Integer.valueOf(tile.tileY)));
		if (f.exists() && f.length() > 0) {
			return new CacheFile(this, tile, f);
		}

		return null;
	}

	/**
	 * @param tile
	 *            The currently accessed tile.
	 */
	private void cacheCheck(Tile tile) {
		if (mCacheSize > mCacheLimit) {
			if (mCleanupJob == null) {
				// TODO dump mCommitHitList before cleanup!

				long limit = mCacheLimit - Math.min(mCacheLimit / 4, 4 * 1024 * 1024);
				mCleanupJob = new CleanupTask(mCacheSize, limit, mCacheDir);
				mCleanupJob.execute(tile);
			}
		}
	}

	/**
	 * @return
	 *         The current size of the cache directionary.
	 */
	private long getCacheDirSize() {
		if (mCacheDir != null) {
			long size = 0;
			File[] files = mCacheDir.listFiles();

			for (File file : files) {
				if (file.isFile()) {
					size += file.length();
				}
			}
			mCacheSize = size;
			if (DEBUG)
				Log.d(TAG, "cache size is now " + mCacheSize);

			return size;
		}
		return -1;
	}

	@Override
	public void setCacheSize(long size) {
		this.mCacheLimit = size;

		if (size > 0)
			return;

		mTileStats.clearStats();

		if (mCacheDir.exists()) {
			for (File f : mCacheDir.listFiles())
				f.delete();

			mCacheSize = 0;
		}
	}

	private void setStoragePath(String path) {
		String state = Environment.getExternalStorageState();

		String externalStorageDirectory = Environment.getExternalStorageDirectory()
				.getAbsolutePath();

		String cacheDirectoryPath = externalStorageDirectory + path;

		if (Environment.MEDIA_MOUNTED.equals(state)) {
			// We can read and write the media
			mCacheDir = createDirectory(cacheDirectoryPath);
			Log.d(TAG, "SDCARD");
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			// We can only read the media
			mCacheDir = mContext.getCacheDir();
			Log.d(TAG, "SDCARD is read only!");
		} else {
			// Something else is wrong. It may be one of many other states, but all we need
			//  to know is we can neither read nor write
			mCacheDir = mContext.getCacheDir();
			Log.d(TAG, "Memory");
		}
		if (DEBUG)
			Log.d(TAG, "cache dir: " + mCacheDir);

		new AsyncTask<Void, Void, Boolean>() {

			@Override
			protected Boolean doInBackground(Void... params) {
				getCacheDirSize();
				return null;
			}
		}.execute();
	}

	class CleanupTask extends AsyncTask<Tile, Void, Boolean> {
		File dir;
		long limit;
		long currentSize;
		long beginSize;

		public CleanupTask(long size, long limit, File cacheDir) {
			dir = cacheDir;
			this.limit = limit;
			beginSize = currentSize = size;
		}

		@Override
		protected Boolean doInBackground(Tile... params) {
			limitCache(params[0]);
			return null;
		}

		/**
		 * @param tile
		 *            The tile, which was accessed when the cache directionary
		 *            size is bigger than the defined size.
		 *            In this case the caches should be recalculated and
		 *            orgnized so that some tile-caches can be deleted.
		 *            The rules for determine if a cache will be deleted or not
		 *            is:
		 *            1. spatial distances from the current tile.
		 *            2. hits time.
		 *            3. exsited age.
		 */
		private void limitCache(Tile tile) {
			/* 1.distance
			 * 2.haeufigkeit
			 * 3.time */
			ArrayList<String> safeTile = new ArrayList<String>();
			int z = tile.zoomLevel;
			int x = tile.tileX;
			int y = tile.tileY;

			/* the tiles surrouding the current tile should not be deleted. */
			for (int zz = z; zz > 4; zz--) {
				for (int xx = x - 3; xx < x + 4; xx++) {
					for (int yy = y - 3; yy < y + 4; yy++) {
						if (xx < 0) {
							xx = (int) (Math.pow(2, zz) - 1 + xx);
						}
						if (yy > 0) {
							String safeTileFile = String.format(CACHE_FILE, Integer.valueOf(zz),
									Integer.valueOf(xx), Integer.valueOf(yy));
							safeTile.add(safeTileFile);
						}
					}
				}
				x = x / 2;
				x = y / 2;
			}
			/* get the middle haeufigkeit */
			//Log.d("Cache", "middle is: " + datasource.getMiddleHits());
			ArrayList<String> always = (ArrayList<String>) mTileStats
					.getAllTileFileAboveHits(mTileStats.getMiddleHits());

			//long limit = MAX_SIZE - 1024 * 1024;

			safeTile.addAll(always);
			/* time */
			if (dir != null) {
				File[] files = dir.listFiles();
				for (int i = 0; i < files.length && currentSize > limit; i++) {
					File file = files[i];
					if (!file.isFile()) {
						files[i] = null;
						continue;
					}

					long age = System.currentTimeMillis() - file.lastModified();

					//Log.d("Cache", file.getName());
					if (!safeTile.contains(file.getName()) && age > 2000000) {
						currentSize -= file.length();
						file.delete();
						files[i] = null;
					}
				}
				for (int i = 0; i < files.length && currentSize > limit; i++) {
					File file = files[i];
					if (file == null)
						continue;

					if (!safeTile.contains(file.getName())) {
						currentSize -= file.length();
						file.delete();
						files[i] = null;
					}
				}
				for (int i = 0; i < files.length && currentSize > limit; i++) {
					File file = files[i];
					if (file == null)
						continue;

					currentSize -= file.length();
					file.delete();
				}
			}
			mCacheSize -= (beginSize - currentSize);
			mCleanupJob = null;
			if (DEBUG)
				Log.d(TAG, "freed: " + (beginSize - currentSize) + " now: " + mCacheSize);
		}

	}

	private CleanupTask mCleanupJob;

	public synchronized void storeTile(CacheFile cacheFile, boolean success) {
		if (success) {
			int size = (int) cacheFile.mFile.length();
			mCacheSize += size;
			if (DEBUG)
				Log.d(TAG, cacheFile.getTile() + " written: " + size + " / usage: "
						+ (mCacheSize / 1024) + "kb");
		} else {
			Log.d(TAG, cacheFile.getTile() + " cache failed");

		}
	}

}
