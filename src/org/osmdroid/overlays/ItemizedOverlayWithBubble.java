package org.osmdroid.overlays;

import java.util.List;

import org.oscim.app.App;
import org.oscim.backend.input.MotionEvent;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.core.PointD;
import org.oscim.layers.overlay.ItemizedIconOverlay;
import org.oscim.layers.overlay.OverlayItem;
import org.oscim.layers.overlay.OverlayMarker;
import org.oscim.view.Map;
import org.osmdroid.utils.BonusPackHelper;

import android.content.Context;
import android.util.Log;

/**
 * An itemized overlay with an InfoWindow or "bubble" which opens when the user
 * taps on an overlay item, and displays item attributes. <br>
 * Items must be ExtendedOverlayItem. <br>
 * @see ExtendedOverlayItem
 * @see InfoWindow
 * @author M.Kergall
 * @param <Item>
 *            ...
 */
public class ItemizedOverlayWithBubble<Item extends OverlayItem> extends ItemizedIconOverlay<Item>
		implements ItemizedIconOverlay.OnItemGestureListener<Item>
{

	protected List<Item> mItemsList;

	// only one for all items of this overlay => one at a time
	protected InfoWindow mBubble;

	// the item currently showing the bubble. Null if none.
	protected OverlayItem mItemWithBubble;

	static int layoutResId = 0;

	@Override
	public boolean onItemLongPress(final int index, final OverlayItem item) {
		if (mBubble.isOpen())
			hideBubble();
		else
			showBubble(index);
		return false;
	}

	@Override
	public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
		showBubble(index);
		return false;
	}

	private final PointD mTmpPoint = new PointD();

	@Override
	public void onUpdate(MapPosition mapPosition, boolean changed, boolean clear) {
		if (mBubble.isOpen()) {
			GeoPoint gp = mItemWithBubble.getPoint();

			PointD p = mTmpPoint;
			mMap.getViewport().project(gp, p);

			mBubble.position((int) p.x, (int) p.y);
		}
	}

	public ItemizedOverlayWithBubble(Map map, Context context, OverlayMarker marker,
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
			// FIXME
			mBubble = new DefaultInfoWindow(layoutResId, App.view);
		}
		mItemWithBubble = null;

		mOnItemGestureListener = this;
	}

	public ItemizedOverlayWithBubble(Map map, Context context, OverlayMarker marker,
			List<Item> aList) {
		this(map, context, marker, aList, null);
	}

	void showBubble(int index) {
		showBubbleOnItem(index);
	}

	/**
	 * Opens the bubble on the item. For each ItemizedOverlay, only one bubble
	 * is opened at a time. If you want more bubbles opened simultaneously, use
	 * many ItemizedOverlays.
	 * @param index
	 *            of the overlay item to show
	 * @param map
	 *            ...
	 */
	@SuppressWarnings("unchecked")
	public void showBubbleOnItem(final int index) {
		ExtendedOverlayItem eItem = (ExtendedOverlayItem) (getItem(index));
		mItemWithBubble = eItem;
		if (eItem != null) {
			eItem.showBubble(mBubble, (Map) mMap);

			mMap.getViewport().animateTo(eItem.mGeoPoint);

			mMap.updateMap(true);
			setFocus((Item) eItem);
		}
	}

	/** Close the bubble (if it's opened). */
	public void hideBubble() {
		mBubble.close();
		mItemWithBubble = null;
	}

	@Override
	public boolean onSingleTapUp(final MotionEvent event) {
		boolean handled = super.onSingleTapUp(event);
		if (!handled)
			hideBubble();
		return handled;
	}

	@Override
	protected boolean onSingleTapUpHelper(final int index, final Item item) {
		showBubbleOnItem(index);
		return true;
	}

	/** @return the item currenty showing the bubble, or null if none. */
	public OverlayItem getBubbledItem() {
		if (mBubble.isOpen())
			return mItemWithBubble;

		return null;
	}

	/** @return the index of the item currenty showing the bubble, or -1 if none. */
	public int getBubbledItemId() {
		OverlayItem item = getBubbledItem();
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
