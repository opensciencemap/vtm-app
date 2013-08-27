package org.oscim.app.location;

import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.renderer.GLRenderer.Matrices;
import org.oscim.renderer.layers.BasicRenderLayer;
import org.oscim.renderer.sublayers.SymbolItem;
import org.oscim.renderer.sublayers.SymbolLayer;
import org.oscim.view.MapView;

import android.graphics.Bitmap;

public class DirectionRenderLayer extends BasicRenderLayer {

	private GeoPoint mLocation;
	private final SymbolLayer mLayer;

	public Bitmap locationBitmap;

	public DirectionRenderLayer(MapView mapView, GeoPoint p) {
		super(mapView);
		mLayer = new SymbolLayer();
		layers.textureLayers = mLayer;
	}

	public GeoPoint getLocation() {
		return mLocation;
	}

	public void setLocation(GeoPoint p) {
		this.mLocation = p;
	}

	@Override
	public void update(MapPosition position, boolean changed, Matrices matrices) {
		mMapPosition.copy(position);

		if (mLocation != null)
			mMapPosition.setPosition(mLocation);

		if (locationBitmap != null) {
			newData = true;
		}
	}

	@Override
	public void compile() {
		synchronized (locationBitmap) {
			mLayer.clear();

			SymbolItem it2 = new SymbolItem();
			it2.billboard = false;
			it2.x = 0;
			it2.y = 0;
			it2.bitmap = locationBitmap;
			mLayer.addSymbol(it2);
			mLayer.prepare();

			locationBitmap = null;

			// need to compile here as location bitmap
			// could be updated on main-thread while
			// uploading texture
			super.compile();
		}
	}
}
