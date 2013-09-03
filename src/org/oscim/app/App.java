/*
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

import org.oscim.view.MapView;

import android.app.Application;
import android.content.res.Resources;

public class App extends Application {

	public static final String TAG = TileMap.class.getName();

	public static MapView map;
	public static Resources res;
	public static TileMap activity;

	public static POISearch poiSearch;
	public static RouteSearch routeSearch;


	// TODO
//	public final static int LAYER_EVENT = 0;
//	public final static int LAYER_BACKGROUND = 1;
//	public final static int LAYER_BASE = 2;
//	public final static int LAYER_BUILDING = 3;
//	public final static int LAYER_LABELS = 5;
//	public final static int LAYER_LOCATION = 4;

	@Override
	public void onCreate() {
		super.onCreate();
		res = getResources();
	}
}
