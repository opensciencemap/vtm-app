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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.oscim.core.Tile;
import org.oscim.tiling.source.ITileCache;
import org.oscim.utils.IOUtils;

/*
 * The class CacheFile implements the concrete cache unit for the cache operation.
 * It contains the FileOutputStream, and can write the binary tile data via the buffer
 * to the file.
 */
public class CacheFile implements ITileCache.TileReader, ITileCache.TileWriter {
	//final static Logger log = LoggerFactory.getLogger(CacheFile.class);

	final File mFile;
	final TileCache mCacheManager;
	final Tile mTile;

	CacheFile(TileCache cm, Tile t, File f) {
		mCacheManager = cm;
		mFile = f;
		mTile = t;
	}

	private OutputStream mOutputStream;

	@Override
	public OutputStream getOutputStream() {
		if (mOutputStream == null) {
			try {
				mOutputStream = new FileOutputStream(mFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		return mOutputStream;
	}

	@Override
	public InputStream getInputStream() {
		try {
			return new FileInputStream(mFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public int getBytes() {
		return (int) mFile.length();
	}

	@Override
	public Tile getTile() {
		return mTile;
	}

	@Override
	public void complete(boolean success) {
		IOUtils.closeQuietly(mOutputStream);

		if (!success) {
			mFile.delete();
		}

		mCacheManager.storeTile(this, success);
	}

}
