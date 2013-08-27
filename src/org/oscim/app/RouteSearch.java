/*
 * Copyright 2012 osmdroid: M.Kergall
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

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.oscim.core.GeoPoint;
import org.oscim.layers.overlay.ItemizedOverlay;
import org.oscim.layers.overlay.OverlayItem;
import org.oscim.layers.overlay.OverlayItem.HotspotPlace;
import org.oscim.layers.overlay.PathOverlay;
import org.oscim.view.MapView;
import org.osmdroid.location.GeocoderNominatim;
import org.osmdroid.overlays.DefaultInfoWindow;
import org.osmdroid.overlays.ExtendedOverlayItem;
import org.osmdroid.overlays.ItemizedOverlayWithBubble;
import org.osmdroid.routing.Route;
import org.osmdroid.routing.RouteNode;
import org.osmdroid.routing.RouteProvider;
import org.osmdroid.routing.provider.MapQuestRouteProvider;

import android.graphics.drawable.Drawable;
import android.location.Address;
import android.os.AsyncTask;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class RouteSearch {
	protected Route mRoute;
	protected final PathOverlay mRouteOverlay;
	protected final ItemizedOverlayWithBubble<ExtendedOverlayItem> mRouteMarkers;
	protected final ItemizedOverlayWithBubble<ExtendedOverlayItem> mItineraryMarkers;

	protected GeoPoint mStartPoint, mDestinationPoint;
	public GeoPoint getmStartPoint() {
		return mStartPoint;
	}

	public void setmStartPoint(GeoPoint mStartPoint) {
		this.mStartPoint = mStartPoint;

	}

	boolean longPress2Point(GeoPoint p1, GeoPoint p2) {
		removeAllOverlay();
		mStartPoint = p1;
		markerStart = putMarkerItem(markerStart, mStartPoint, START_INDEX,
				R.string.departure, R.drawable.marker_departure, -1);
		mDestinationPoint = p2;
		//new GeoPoint((GeoPoint) tempClickedGeoPoint);
		markerDestination = putMarkerItem(markerDestination, mDestinationPoint, DEST_INDEX,
				R.string.destination,
				R.drawable.marker_destination, -1);
		getRouteAsync();
		return true;
	}



	public GeoPoint getmDestinationPoint() {
		return mDestinationPoint;
	}

	public void setmDestinationPoint(GeoPoint mDestinationPoint) {
		this.mDestinationPoint = mDestinationPoint;
	}

	protected final ArrayList<GeoPoint> mViaPoints;

	protected static int START_INDEX = -2, DEST_INDEX = -1;
	protected ExtendedOverlayItem markerStart, markerDestination;

	RouteSearch() {
		mViaPoints = new ArrayList<GeoPoint>();

		// Itinerary markers:
		ArrayList<ExtendedOverlayItem> waypointsItems = new ArrayList<ExtendedOverlayItem>();
		mItineraryMarkers = new ItemizedOverlayWithBubble<ExtendedOverlayItem>(App.map,
				App.activity, waypointsItems, new ViaPointInfoWindow(R.layout.itinerary_bubble,
						App.map));

		updateIternaryMarkers();

		//Route and Directions
		ArrayList<ExtendedOverlayItem> routeItems = new ArrayList<ExtendedOverlayItem>();
		mRouteMarkers = new ItemizedOverlayWithBubble<ExtendedOverlayItem>(App.activity, routeItems,
				App.map);

		mRouteOverlay = new PathOverlay(App.map, 0xAA0000FF, 3);

		App.map.getOverlays().add(mRouteOverlay);
		App.map.getOverlays().add(mRouteMarkers);
		App.map.getOverlays().add(mItineraryMarkers);
	}

	/**
	 * Reverse Geocoding
	 * @param p
	 *            ...
	 * @return ...
	 */
	public String getAddress(GeoPoint p) {
		GeocoderNominatim geocoder = new GeocoderNominatim(App.activity);
		String theAddress;
		try {
			double dLatitude = p.getLatitude();
			double dLongitude = p.getLongitude();
			List<Address> addresses = geocoder.getFromLocation(dLatitude, dLongitude, 1);
			StringBuilder sb = new StringBuilder();
			if (addresses.size() > 0) {
				Address address = addresses.get(0);
				int n = address.getMaxAddressLineIndex();
				for (int i = 0; i <= n; i++) {
					if (i != 0)
						sb.append(", ");
					sb.append(address.getAddressLine(i));
				}
				theAddress = new String(sb.toString());
			} else {
				theAddress = null;
			}
		} catch (IOException e) {
			theAddress = null;
		}
		if (theAddress != null) {
			return theAddress;
		}
		return "";
	}

	// Async task to reverse-geocode the marker position in a separate thread:
	class GeocodingTask extends AsyncTask<Object, Void, String> {
		ExtendedOverlayItem marker;

		@Override
		protected String doInBackground(Object... params) {
			marker = (ExtendedOverlayItem) params[0];
			return getAddress(marker.getPoint());
		}

		@Override
		protected void onPostExecute(String result) {
			marker.setDescription(result);
			//itineraryMarkers.showBubbleOnItem(???, map); //open bubble on the item
		}
	}

	/* add (or replace) an item in markerOverlays. p position. */
	public ExtendedOverlayItem putMarkerItem(ExtendedOverlayItem item, GeoPoint p, int index,
			int titleResId, int markerResId, int iconResId) {

		if (item != null)
			mItineraryMarkers.removeItem(item);

		Drawable marker = ItemizedOverlay.makeMarker(App.res, markerResId,
				HotspotPlace.BOTTOM_CENTER);

		String title = App.res.getString(titleResId);

		ExtendedOverlayItem overlayItem = new ExtendedOverlayItem(title, "", p);

		overlayItem.setMarker(marker);

		if (iconResId != -1)
			overlayItem.setImage(App.res.getDrawable(iconResId));

		overlayItem.setRelatedObject(Integer.valueOf(index));

		mItineraryMarkers.addItem(overlayItem);

		App.map.redrawMap(true);

		//Start geocoding task to update the description of the marker with its address:
		new GeocodingTask().execute(overlayItem);
		return overlayItem;
	}

	public void addViaPoint(GeoPoint p) {
		mViaPoints.add(p);
		putMarkerItem(null, p, mViaPoints.size() - 1,
				R.string.viapoint, R.drawable.marker_via, -1);
	}

	public void removePoint(int index) {
		if (index == START_INDEX)
			mStartPoint = null;
		else if (index == DEST_INDEX)
			mDestinationPoint = null;
		else
			mViaPoints.remove(index);

		getRouteAsync();
		updateIternaryMarkers();
	}

	public void updateIternaryMarkers() {
		mItineraryMarkers.removeAllItems();

		//Start marker:
		if (mStartPoint != null) {
			markerStart = putMarkerItem(null, mStartPoint, START_INDEX,
					R.string.departure, R.drawable.marker_departure, -1);
		}
		//Via-points markers if any:
		for (int index = 0; index < mViaPoints.size(); index++) {
			putMarkerItem(null, mViaPoints.get(index), index,
					R.string.viapoint, R.drawable.marker_via, -1);
		}
		//Destination marker if any:
		if (mDestinationPoint != null) {
			markerDestination = putMarkerItem(null, mDestinationPoint, DEST_INDEX,
					R.string.destination,
					R.drawable.marker_destination, -1);
		}
	}

	//------------ Route and Directions

	private void putRouteNodes(Route route) {
		mRouteMarkers.removeAllItems();

		Drawable marker = ItemizedOverlay.makeMarker(App.res, R.drawable.marker_node, null);

		int n = route.nodes.size();
		//TypedArray iconIds = App.res.obtainTypedArray(R.array.direction_icons);
		for (int i = 0; i < n; i++) {
			RouteNode node = route.nodes.get(i);
			String instructions = (node.instructions == null ? "" : node.instructions);
			ExtendedOverlayItem nodeMarker = new ExtendedOverlayItem(
					"Step " + (i + 1), instructions, node.location);

			nodeMarker.setSubDescription(route.getLengthDurationText(node.length, node.duration));
			nodeMarker.setMarkerHotspot(OverlayItem.HotspotPlace.CENTER);
			nodeMarker.setMarker(marker);
			//int iconId = iconIds.getResourceId(node.mManeuverType, R.drawable.ic_empty);
			//if (iconId != R.drawable.ic_empty) {
			//	Drawable icon = App.res.getDrawable(iconId);
			//	nodeMarker.setImage(icon);
			//}
			mRouteMarkers.addItem(nodeMarker);
		}
	}

	void updateRouteMarkers(Route route) {
		mRouteMarkers.removeAllItems();
		mRouteOverlay.clearPath();

		if (route == null)
			return;

		if (route.status == Route.STATUS_DEFAULT)
			Toast.makeText(App.map.getContext(), "We have a problem to get the route",
					Toast.LENGTH_SHORT).show();

		mRouteOverlay.setPoints(route.routeHigh);

		//RouteProvider.buildRouteOverlay(mRouteOverlay, route);

		putRouteNodes(route);

		App.map.redrawMap(true);
	}



	void removeAllOverlay() {
		mRouteMarkers.removeAllItems(true);
		mItineraryMarkers.removeAllItems(true);

		mRouteOverlay.clearPath();
		mStartPoint = null;
		mDestinationPoint = null;
		mViaPoints.clear();

		App.map.redrawMap(true);
	}

	/**
	 * Async task to get the route in a separate thread.
	 */
	class UpdateRouteTask extends AsyncTask<WayPoints, Void, Route> {
		@Override
		protected Route doInBackground(WayPoints... wp) {
			WayPoints waypoints = wp[0];
			//RouteManager routeManager = new GoogleRouteManager();
			//RouteManager routeManager = new OSRMRouteManager();
			RouteProvider routeManager = new MapQuestRouteProvider();
			Locale locale = Locale.getDefault();
			routeManager.addRequestOption("locale=" + locale.getLanguage() + "_"
					+ locale.getCountry());
			routeManager.addRequestOption("routeType=pedestrian");
			return routeManager.getRoute(waypoints);
		}

		@Override
		protected void onPostExecute(Route result) {
			mRoute = result;
			updateRouteMarkers(result);



			DecimalFormat twoDForm = new DecimalFormat("#.#");
			DecimalFormat oneDForm = new DecimalFormat("#");
			int hour = ((int) result.duration / 3600);
			int minute = ((int) result.duration % 3600) / 60;
			String time = "";
			if (hour == 0 && minute == 0) {
				time = "?";
			}
			else if (hour == 0 && minute != 0) {
				time = minute + "m";
			} else {
				time = hour + "h " + minute + "m";
			}
			//Log.d(TAG,"Hour: "+hour+" Min: "+minute+" Duration: "+result.duration);
			//			tileMap.mapInfo.setVisibility(View.VISIBLE);//			tileMap.mapInfo.setTextSize((float) 20.0);
			double dis = ((double) (mStartPoint.distanceTo(mDestinationPoint))) / 1000;
			String distance;
			String shortpath;
			if (dis < 100) {
				distance = twoDForm.format(dis);
			} else {
				distance = oneDForm.format(dis);
			}
			if (result.length == 0) {
				shortpath = "?";
			}
			else if (result.length < 100) {
				shortpath = twoDForm.format(result.length);
			} else {
				shortpath = oneDForm.format(result.length);
			}
			//			tileMap.mapInfo.setText(" Direct distance: "+distance+" km" +
			//					"\n Shortest path: " + shortpath
			//					+ " km \n By car: "
			//					+time);
			App.activity.setRouteBar(distance + " km ", shortpath + " km ", time);


		}
	}

	class WayPoints extends ArrayList<GeoPoint> {
		public WayPoints(int i) {
			super(i);
		}

		private static final long serialVersionUID = 1L;
	}

	public void getRouteAsync() {
		mRoute = null;
		if (mStartPoint == null || mDestinationPoint == null) {
			updateRouteMarkers(mRoute);
			return;
		}
		WayPoints waypoints = new WayPoints(2);
		waypoints.add(mStartPoint);
		//add intermediate via points:
		for (GeoPoint p : mViaPoints) {
			waypoints.add(p);
		}
		waypoints.add(mDestinationPoint);
		new UpdateRouteTask().execute(waypoints);
	}

	GeoPoint tempClickedGeoPoint; //any other way to pass the position to the menu ???

	boolean longPress(GeoPoint p) {
		tempClickedGeoPoint = p;
		return true;
	}

	void singleTapUp() {
		mRouteMarkers.hideBubble();
		mItineraryMarkers.hideBubble();
	}

	boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_departure:
			mStartPoint = tempClickedGeoPoint;
			markerStart = putMarkerItem(markerStart, mStartPoint, START_INDEX,
					R.string.departure, R.drawable.marker_departure, -1);
			getRouteAsync();
			return true;

		case R.id.menu_destination:
			mDestinationPoint = tempClickedGeoPoint;
			markerDestination = putMarkerItem(markerDestination, mDestinationPoint, DEST_INDEX,
					R.string.destination,
					R.drawable.marker_destination, -1);
			getRouteAsync();
			return true;

		case R.id.menu_viapoint:
			GeoPoint viaPoint = tempClickedGeoPoint;
			addViaPoint(viaPoint);
			getRouteAsync();
			return true;

		case R.id.menu_clear_route:
			removeAllOverlay();
			return true;

		default:
		}
		return false;
	}

	public boolean isEmpty(){
		 return (mItineraryMarkers.size() == 0);
	}

	class ViaPointInfoWindow extends DefaultInfoWindow {

		int mSelectedPoint;

		public ViaPointInfoWindow(int layoutResId, MapView mapView) {
			super(layoutResId, mapView);

			Button btnDelete = (Button) (mView.findViewById(R.id.bubble_delete));
			btnDelete.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					removePoint(mSelectedPoint);
					close();
				}
			});
		}

		@Override
		public void onOpen(ExtendedOverlayItem item) {
			mSelectedPoint = ((Integer) item.getRelatedObject()).intValue();
			super.onOpen(item);
		}

	}
}
