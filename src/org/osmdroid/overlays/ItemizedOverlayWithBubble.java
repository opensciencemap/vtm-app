package org.osmdroid.overlays;

import java.util.List;

import org.oscim.app.App;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.core.Point;
import org.oscim.event.MotionEvent;
import org.oscim.layers.marker.ItemizedIconLayer;
import org.oscim.layers.marker.MarkerItem;
import org.oscim.layers.marker.MarkerSymbol;
import org.oscim.map.Map;
import org.osmdroid.utils.BonusPackHelper;

import android.content.Context;
import android.util.Log;

/**
 * An itemized overlay with an InfoWindow or "bubble" which opens when the user
 * taps on an overlay item, and displays item attributes. <br>
 * Items must be ExtendedMarkerItem. <br>
 * 
 * @see ExtendedMarkerItem
 * @see InfoWindow
 * @author M.Kergall
 * @param <Item>
 *            ...
 */
public class ItemizedOverlayWithBubble<Item extends MarkerItem> extends ItemizedIconLayer<Item>
        implements ItemizedIconLayer.OnItemGestureListener<Item>, Map.UpdateListener
{

	protected List<Item> mItemsList;

	// only one for all items of this overlay => one at a time
	protected InfoWindow mBubble;

	// the item currently showing the bubble. Null if none.
	protected MarkerItem mItemWithBubble;

	static int layoutResId = 0;

	public ItemizedOverlayWithBubble(Map map, Context context, MarkerSymbol marker,
	        List<Item> aList, InfoWindow bubble) {
		super(map, aList, marker, null);

		mItemsList = aList;
		if (bubble != null) {
			mBubble = bubble;
		} else {
			// build default bubble:
			String packageName = context.getPackageName();
			if (layoutResId == 0) {
				layoutResId = context.getResources().getIdentifier(
				                                                   "layout/bonuspack_bubble", null,
				                                                   packageName);
				if (layoutResId == 0)
					Log.e(BonusPackHelper.LOG_TAG,
					      "ItemizedOverlayWithBubble: layout/bonuspack_bubble not found in "
					              + packageName);
			}
			mBubble = new DefaultInfoWindow(layoutResId, App.view);
		}

		mItemWithBubble = null;
		mOnItemGestureListener = this;
	}

	public ItemizedOverlayWithBubble(Map map, Context context, MarkerSymbol marker,
	        List<Item> aList) {
		this(map, context, marker, aList, null);
	}

	@Override
	public boolean onItemLongPress(int index, MarkerItem item) {
		if (mBubble.isOpen())
			hideBubble();
		else
			showBubble(index);
		return false;
	}

	@Override
	public boolean onItemSingleTapUp(int index, MarkerItem item) {
		showBubble(index);

		return true;
	}

	private final Point mTmpPoint = new Point();

	@Override
	protected boolean activateSelectedItems(MotionEvent event, ActiveItem task) {
		boolean hit = super.activateSelectedItems(event, task);

		if (!hit)
			hideBubble();

		return hit;
	}

	@Override
	public void onMapUpdate(MapPosition mapPosition, boolean changed, boolean clear) {
		if (mBubble.isOpen()) {
			GeoPoint gp = mItemWithBubble.getPoint();

			Point p = mTmpPoint;
			mMap.getViewport().toScreenPoint(gp, p);

			mBubble.position((int) p.x, (int) p.y);
		}
	}

	void showBubble(int index) {
		showBubbleOnItem(index);
	}

	/**
	 * Opens the bubble on the item. For each ItemizedOverlay, only one bubble
	 * is opened at a time. If you want more bubbles opened simultaneously, use
	 * many ItemizedOverlays.
	 * 
	 * @param index
	 *            of the overlay item to show
	 * @param map
	 *            ...
	 */
	@SuppressWarnings("unchecked")
	public void showBubbleOnItem(int index) {
		ExtendedMarkerItem item = (ExtendedMarkerItem) (getItem(index));
		mItemWithBubble = item;
		if (item != null) {
			item.showBubble(mBubble, (Map) mMap);

			mMap.getAnimator().animateTo(item.mGeoPoint);

			mMap.updateMap(true);
			setFocus((Item) item);
		}
	}

	/** Close the bubble (if it's opened). */
	public void hideBubble() {
		mBubble.close();
		mItemWithBubble = null;
	}

	//	@Override
	//	public boolean onSingleTapUp(final MotionEvent event) {
	//		boolean handled = super.onSingleTapUp(event);
	//		if (!handled)
	//			hideBubble();
	//		return handled;
	//	}
	//
	//	@Override
	//	protected boolean onSingleTapUpHelper(final int index, final Item item) {
	//		showBubbleOnItem(index);
	//		return true;
	//	}

	/** @return the item currenty showing the bubble, or null if none. */
	public MarkerItem getBubbledItem() {
		if (mBubble.isOpen())
			return mItemWithBubble;

		return null;
	}

	/** @return the index of the item currenty showing the bubble, or -1 if none. */
	public int getBubbledItemId() {
		MarkerItem item = getBubbledItem();
		if (item == null)
			return -1;

		return mItemsList.indexOf(item);
	}

	@Override
	public boolean removeItem(final Item item) {
		boolean result = super.removeItem(item);
		if (mItemWithBubble == item) {
			hideBubble();
		}
		return result;
	}

	@Override
	public void removeAllItems() {
		super.removeAllItems();
		hideBubble();
	}
}
