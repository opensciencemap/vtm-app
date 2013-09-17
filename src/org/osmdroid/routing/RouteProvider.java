package org.osmdroid.routing;

import java.util.List;

import org.oscim.core.GeoPoint;
import org.oscim.layers.PathLayer;
import org.oscim.map.Map;
import org.osmdroid.routing.provider.GoogleRouteProvider;
import org.osmdroid.routing.provider.MapQuestRouteProvider;
import org.osmdroid.routing.provider.OSRMRouteProvider;

/**
 * Generic class to get a route between a start and a destination point, going
 * through a list of waypoints.
 * @see MapQuestRouteProvider
 * @see GoogleRouteProvider
 * @see OSRMRouteProvider
 * @author M.Kergall
 */
public abstract class RouteProvider {

	protected String mOptions;

	public abstract Route getRoute(List<GeoPoint> waypoints);

	public RouteProvider() {
		mOptions = "";
	}

	/**
	 * Add an option that will be used in the route request. Note that some
	 * options are set in the request in all cases.
	 * @param requestOption
	 *            see provider documentation. Just one example:
	 *            "routeType=bicycle" for MapQuest; "mode=bicycling" for Google.
	 */
	public void addRequestOption(String requestOption) {
		mOptions += "&" + requestOption;
	}

	protected String geoPointAsString(GeoPoint p) {
		StringBuffer result = new StringBuffer();
		double d = p.getLatitude();
		result.append(Double.toString(d));
		d = p.getLongitude();
		result.append("," + Double.toString(d));
		return result.toString();
	}

	/**
	 * Builds an overlay for the route shape with a default (and nice!) color.
	 * @param map
	 *            ..
	 * @param route
	 *            ..
	 * @param context
	 *            ..
	 * @return route shape overlay
	 */
	public static PathLayer buildRouteOverlay(Map map, Route route) {
		int lineColor = 0x800000FF;
		float lineWidth = 2.5f;

		PathLayer routeOverlay = new PathLayer(map, lineColor, lineWidth);
		if (route != null) {
			routeOverlay.setPoints(route.routeHigh);
		}
		return routeOverlay;
	}

}
