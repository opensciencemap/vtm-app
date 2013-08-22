/*
 * Copyright 2013 Ahmad Saleem
 * Copyright 2013 Hannes Janetzek
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

import org.oscim.core.Box;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.core.MercatorProjection;
import org.oscim.core.PointD;
import org.oscim.core.Tile;
import org.oscim.layers.overlay.Overlay;
import org.oscim.renderer.GLRenderer;
import org.oscim.renderer.GLRenderer.Matrices;
import org.oscim.renderer.GLState;
import org.oscim.renderer.RenderLayer;
import org.oscim.utils.FastMath;
import org.oscim.utils.GlUtils;
import org.oscim.utils.Interpolation;
import org.oscim.view.MapView;
import org.oscim.view.MapViewPosition;

import android.opengl.GLES20;
import android.os.SystemClock;

public class LocationOverlay extends Overlay {

	private final PointD mLocation = new PointD();
	private double mRadius;

	public class LocationIndicator extends RenderLayer {
		private int mShaderProgram;
		private int hVertexPosition;
		private int hMatrixPosition;
		private int hScale;
		private int hPhase;

		private boolean mInitialized;

		private final float CIRCLE_SIZE = 100;
		private final int SHOW_ACCURACY_ZOOM = 16;

		private final static long ANIM_RATE = 50;
		private final static long INTERVAL = 2000;

		private final PointD mIndicatorPosition = new PointD();

		private final PointD mScreenPoint = new PointD();
		private final Box mBBox = new Box();

		private boolean mLocationIsVisible;

		public LocationIndicator(final MapView mapView) {
			super(mapView);
		}

		private boolean mRunAnim;
		private long mAnimStart;

		private void animate(boolean enable) {
			if (mRunAnim == enable)
				return;

			mRunAnim = enable;
			if (!enable)
				return;

			final Runnable action = new Runnable() {
				private long lastRun;

				@Override
				public void run() {
					if (!mRunAnim)
						return;

					long diff = SystemClock.elapsedRealtime() - lastRun;
					mMapView.postDelayed(this, Math.min(ANIM_RATE, diff));
					mMapView.render();
				}
			};

			mAnimStart = SystemClock.elapsedRealtime();
			mMapView.postDelayed(action, ANIM_RATE);
		}

		private float animPhase() {
			return (float) ((GLRenderer.frametime - mAnimStart) % INTERVAL) / INTERVAL;
		}

		@Override
		public void update(MapPosition curPos, boolean changed, Matrices matrices) {

			if (!mInitialized) {
				if (!init())
					return;

				mInitialized = true;
				//newData = true;
				isReady = true;
			}

			if (!changed)
				return;

			int width = mMapView.getWidth();
			int height = mMapView.getHeight();

			MapViewPosition mapViewPosition = mMapView.getMapViewPosition();

			// clamp location to a position that can be
			// savely translated to screen coordinates
			mapViewPosition.getViewBox(mBBox);

			double x = mLocation.x;
			double y = mLocation.y;

			if (!mBBox.contains(mLocation)) {
				x = FastMath.clamp(x, mBBox.minX, mBBox.maxX);
				y = FastMath.clamp(y, mBBox.minY, mBBox.maxY);
			}

			// get position of Location in pixel relative to
			// screen center
			mapViewPosition.project(x, y, mScreenPoint);

			x = mScreenPoint.x + width / 2;
			y = mScreenPoint.y + height / 2;

			// clip position to screen boundaries
			int visible = 0;

			if (x > width)
				x = width;
			else if (x < 0)
				x = 0;
			else
				visible++;

			if (y > height)
				y = height;
			else if (y < 0)
				y = 0;
			else
				visible++;

			mLocationIsVisible = (visible == 2);

			// set location indicator position
			mapViewPosition.fromScreenPixels(x, y, mIndicatorPosition);
		}

		@Override
		public void compile() {
		}

		@Override
		public void render(MapPosition pos, Matrices m) {

			GLState.useProgram(mShaderProgram);
			GLState.blend(true);
			GLState.test(false, false);

			GLState.enableVertexArrays(hVertexPosition, -1);
			GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,
					GLRenderer.getQuadVertexVBO());
			GLES20.glVertexAttribPointer(hVertexPosition, 2,
					GLES20.GL_FLOAT, false, 0, 0);

			float radius = CIRCLE_SIZE;

			if (!mLocationIsVisible || pos.zoomLevel < SHOW_ACCURACY_ZOOM) {
				animate(true);
			} else {
				radius = (float) (mRadius * pos.scale);
				animate(false);
			}
			GLES20.glUniform1f(hScale, radius);

			double x = mIndicatorPosition.x - pos.x;
			double y = mIndicatorPosition.y - pos.y;
			double tileScale = Tile.SIZE * pos.scale;

			m.mvp.setTransScale((float) (x * tileScale), (float) (y * tileScale), 1);
			m.mvp.multiplyMM(m.viewproj, m.mvp);
			m.mvp.setAsUniform(hMatrixPosition);

			if (mRunAnim) {
				float phase = Interpolation.swing.apply(animPhase());
				GLES20.glUniform1f(hPhase, 0.5f + Math.abs(phase - 0.5f));
			} else {
				GLES20.glUniform1f(hPhase, 1);
			}

			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		}

		private boolean init() {
			int shader = GlUtils.createProgram(vShaderStr, fShaderStr);
			if (shader == 0)
				return false;

			hVertexPosition = GLES20.glGetAttribLocation(shader, "a_pos");
			hMatrixPosition = GLES20.glGetUniformLocation(shader, "u_mvp");

			hPhase = GLES20.glGetUniformLocation(shader, "phase");
			hScale = GLES20.glGetUniformLocation(shader, "scale");

			mShaderProgram = shader;
			return true;
		}

		private final static String vShaderStr = ""
				+ "precision mediump float;"
				+ "uniform mat4 u_mvp;"
				+ "uniform float phase;"
				+ "uniform float scale;"
				+ "attribute vec2 a_pos;"
				+ "varying vec2 v_tex;"
				+ "void main() {"
				+ "  gl_Position = u_mvp * vec4(a_pos * scale * phase, 0.0, 1.0);"
				+ "  v_tex = a_pos;"
				+ "}";

		private final static String fShaderStr = ""
				+ "precision mediump float;"
				+ "varying vec2 v_tex;"
				+ "uniform float scale;"
				+ "void main() {"
				+ "  float len = 1.0 - length(v_tex);"
				///  blur outline by 10px
				+ "  float a = smoothstep(0.0, 10.0 / scale, len);"
				+ "  float b = smoothstep(0.0, 1.0, len);"
				+ "  a = a - b;"
				+ "  gl_FragColor = vec4 (0.2, 0.2, 0.6, 0.6) * a;"
				+ "}";

	}

	public LocationOverlay(MapView mapView) {
		super(mapView);
		mLayer = new LocationIndicator(mapView);
	}

	public void setPosition(GeoPoint location, double radius) {
		MercatorProjection.project(location, mLocation);
		mRadius = radius / MercatorProjection.calculateGroundResolution(location.getLatitude(), 1);
	}
}
