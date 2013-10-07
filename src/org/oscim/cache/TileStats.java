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

import java.util.ArrayList;
import java.util.List;

import org.oscim.core.Tile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;

/*
 * This class implements the operations on the tilehits table,
 * when the new tiles was saved,or when the existed tile was visited,
 * or when the tile was deleted.
 */
@SuppressLint("DefaultLocale")
public class TileStats {

	//private static final String TAG = TileStats.class.getName();

	/*
	 * This class implement the sqldatabase for oscim map.
	 * It defines the schema of the tables to store the tile access
	 * information.
	 */
	class SQLiteHelper extends SQLiteOpenHelper {

		public static final String TABLE_NAME = "tilehit";
		public static final String COLUMN_ID = "_id";
		public static final String COLUMN_TileName = "_name";
		public static final String COLUMN_Hit = "hits";

		private static final String DATABASE_NAME = "tilehit.db";
		private static final int DATABASE_VERSION = 1;

		// Database creation sql statement
		private static final String DATABASE_CREATE =
		        "create table "
		                + TABLE_NAME + "("
		                + COLUMN_ID + " integer primary key, "
		                + COLUMN_TileName + " text not null, "
		                + COLUMN_Hit + " integer);";

		public SQLiteHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
			onCreate(db);

		}
	}

	private final SQLiteHelper dbHelper;
	public static String Lock = "dblock";

	//	private String[] allColumns = { SQLiteHelper.COLUMN_ID,
	//			SQLiteHelper.COLUMN_COMMENT };

	public TileStats(Context context) {
		dbHelper = new SQLiteHelper(context);
	}

	public SQLiteDatabase open() throws SQLException {
		return dbHelper.getWritableDatabase();
	}

	public void clearStats() {
		synchronized (Lock) {
			SQLiteDatabase db = open();
			dbHelper.onUpgrade(db, 0, 0);
			db.close();
		}
	}

	/**
	 * @param TileName
	 *            The new Tilename which will be saved in the database first
	 *            time.
	 */
	public void setTileHit(String TileName) {
		//ContentValues values = new ContentValues();
		synchronized (Lock) {
			SQLiteDatabase database = open();
			String insert =
			        "INSERT OR IGNORE INTO " + SQLiteHelper.TABLE_NAME + "(_name,hits)"
			                + " VALUES ('"
			                + TileName
			                + "', '0');";
			String update =
			        "UPDATE " + SQLiteHelper.TABLE_NAME
			                + " SET hits = hits + 1 WHERE _name = '"
			                + TileName + "'";
			database.execSQL(insert);
			database.execSQL(update);
			//Log.d("Cache", "setTileHit once");
			database.close();
		}
	}

	private static final String CACHE_FILE = "%d-%d-%d.tile";

	/**
	 * @param tiles
	 *            The new Tilenames which will be saved in the database first
	 *            time.
	 */
	public void setTileHit(Tile[] tiles) {

		new AsyncTask<Tile, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Tile... commits) {
				synchronized (Lock) {
					SQLiteDatabase db = open();
					for (final Tile tile : commits) {
						//System.out.println("commit " + tile);

						final String tileName = String.format(CACHE_FILE,
						                                      Integer.valueOf(tile.zoomLevel),
						                                      Integer.valueOf(tile.tileX),
						                                      Integer.valueOf(tile.tileY));
						String insert =
						        "INSERT OR IGNORE INTO " + SQLiteHelper.TABLE_NAME + "(_name,hits)"
						                + " VALUES ('"
						                + tileName
						                + "', '0');";
						String update =
						        "UPDATE " + SQLiteHelper.TABLE_NAME
						                + " SET hits = hits + 1 WHERE _name = '"
						                + tileName + "'";
						db.execSQL(insert);
						db.execSQL(update);
					}

					db.close();
				}
				return Boolean.TRUE;
			}
		}.execute(tiles);
	}

	/**
	 * @param TileName
	 *            The Tile name for which the hits time was required.
	 * @return
	 *         The times the tile was visited.
	 */
	public int getHitsByTile(String TileName) {
		synchronized (Lock) {

			SQLiteDatabase database = open();

			Cursor cursor = database.query(SQLiteHelper.TABLE_NAME, new String[] { "hits" },
			                               "_name=?",
			                               new String[] { TileName }, null, null, null);
			cursor.moveToFirst();
			int hit = cursor.getInt(0);
			cursor.close();
			database.close();
			return hit;
		}
	}

	/**
	 * @param hit
	 *            The visit time.
	 * @return
	 *         All the Tile name whose access time less than the given hit time.
	 */
	public List<String> getAllTileFileUnderHits(int hit) {
		synchronized (Lock) {
			SQLiteDatabase database = open();

			List<String> TileFiles = new ArrayList<String>();
			Cursor cursor = database.query(SQLiteHelper.TABLE_NAME,
			                               new String[] { "_name" },
			                               "hits<=?",
			                               new String[] { String.valueOf(hit) },
			                               null,
			                               null,
			                               null);
			cursor.moveToFirst();
			while (!cursor.isAfterLast()) {
				String File = cursor.getString(0);
				TileFiles.add(File);
				cursor.moveToNext();
			}
			cursor.close();
			database.close();
			return TileFiles;
		}
	}

	/**
	 * @param hit
	 *            The visit time.
	 * @return
	 *         All the Tile name whose access time more than the given hit time.
	 */
	public List<String> getAllTileFileAboveHits(int hit) {
		synchronized (Lock) {
			SQLiteDatabase database = open();

			List<String> TileFiles = new ArrayList<String>();
			Cursor cursor = database.query(SQLiteHelper.TABLE_NAME,
			                               new String[] { "_name" },
			                               "hits>?",
			                               new String[] { String.valueOf(hit) },
			                               null,
			                               null,
			                               null);
			cursor.moveToFirst();
			while (!cursor.isAfterLast()) {
				String File = cursor.getString(0);
				TileFiles.add(File);
				cursor.moveToNext();
			}
			cursor.close();
			database.close();
			return TileFiles;
		}
	}

	/**
	 * @return
	 *         The average hit time for the Tiles in the table.
	 */
	public int getMiddleHits() {
		synchronized (Lock) {
			SQLiteDatabase database = open();
			Cursor c = database.rawQuery(
			                             "select max(hits) from " + SQLiteHelper.TABLE_NAME,
			                             null);
			c.moveToFirst();
			int middle = c.getInt(0) / 2;
			c.close();
			database.close();
			return middle;
		}
	}

	/**
	 * @param hit
	 *            Given hit times, and delete all the Tiles which was accessed
	 *            less then the hit time.
	 */
	public void deleteTileFileUnderhits(int hit) {
		List<String> names = getAllTileFileUnderHits(hit);
		for (String name : names) {
			deleteTileFile(name);
		}
	}

	/**
	 * @param name
	 *            The name for the Tile, which will be deleted from the table.
	 */
	public void deleteTileFile(String name) {
		synchronized (Lock) {
			SQLiteDatabase database = open();

			database.delete(SQLiteHelper.TABLE_NAME, SQLiteHelper.COLUMN_ID
			        + " = '" + name + "'", null);
			database.close();
		}
	}
}
