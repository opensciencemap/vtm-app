/*
 * Copyright 2013 Ahmad Saleem
 * Copyright 2013 Hannes Janetzek.org
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

package org.oscim.app.location;

import org.oscim.android.AndroidGraphics;
import org.oscim.app.R;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.core.PointF;
import org.oscim.layers.overlay.Overlay;
import org.oscim.renderer.GLRenderer.Matrices;
import org.oscim.renderer.layers.BasicRenderLayer;
import org.oscim.renderer.sublayers.SymbolItem;
import org.oscim.renderer.sublayers.SymbolLayer;
import org.oscim.view.MapView;

import android.content.res.Resources;
import android.graphics.Bitmap;

public class LocationMarker extends Overlay {

	private double mLatitude;
	private double mLongitude;

	public void setPosition(GeoPoint g) {
		mLatitude = g.getLatitude();
		mLongitude = g.getLongitude();
	}

	class DrawableLayer extends BasicRenderLayer {

		private Bitmap mMarkerDefault;
		private Bitmap mMarkerPerson;
		private SymbolLayer mSymbolLayer;
		private SymbolItem mSymbol;

		public boolean detailMarker;

		public DrawableLayer(MapView mapView) {
			super(mapView);
			Resources res = mapView.getContext().getResources();

			mMarkerDefault = AndroidGraphics.drawableToBitmap(res
					.getDrawable(R.drawable.marker_default));
			mMarkerPerson = AndroidGraphics.drawableToBitmap(res
					.getDrawable(R.drawable.person));

			SymbolItem it = SymbolItem.pool.get();
			it.billboard = false;
			it.x = 0;
			it.y = 0;
			it.bitmap = mMarkerDefault;
			mSymbol = it;

			layers.textureLayers = mSymbolLayer = new SymbolLayer();

			mSymbolLayer.addSymbol(it);
		}

		@Override
		public void update(MapPosition curPos, boolean changed, Matrices matrices) {

			if (curPos.zoomLevel > 13 && !detailMarker) {
				detailMarker = true;
				mSymbol.bitmap = mMarkerPerson;
				mSymbol.billboard = true;
				mSymbol.offset = new PointF(0.5f, 0.95f);
				newData = true;
			} else if (curPos.zoomLevel <= 13 && detailMarker) {
				detailMarker = false;
				mSymbol.billboard = false;
				mSymbol.bitmap = mMarkerDefault;
				mSymbol.offset = null;
				newData = true;
			}

			if (newData) {
				mMapPosition.copy(curPos);
				mMapPosition.setPosition(mLatitude, mLongitude);
			}
		}
	}

	public LocationMarker(MapView mapView) {
		super(mapView);

		mLayer = new DrawableLayer(mapView);
	}
}
