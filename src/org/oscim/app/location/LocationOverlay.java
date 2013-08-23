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
	private final int SHOW_ACCURACY_ZOOM = 16;

	private final PointD mLocation = new PointD();
	private double mRadius;

	private final Compass mCompass;

	public LocationOverlay(MapView mapView, Compass compass) {
		super(mapView);
		mLayer = new LocationIndicator(mapView);
		mCompass = compass;
	}

	public void setPosition(double latitude, double longitude, double accuracy) {
		mLocation.x = MercatorProjection.longitudeToX(longitude);
		mLocation.y = MercatorProjection.latitudeToY(latitude);
		mRadius = accuracy / MercatorProjection.calculateGroundResolution(latitude, 1);
		((LocationIndicator) mLayer).animate(true);
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);

		if (!enabled)
			((LocationIndicator) mLayer).animate(false);
		mCompass.setEnabled(true);
	}

	@Override
	public void onUpdate(MapPosition mapPosition, boolean changed, boolean clear) {
		mCompass.setEnabled(mapPosition.zoomLevel >= SHOW_ACCURACY_ZOOM);
	}

	public class LocationIndicator extends RenderLayer {
		private int mShaderProgram;
		private int hVertexPosition;
		private int hMatrixPosition;
		private int hScale;
		private int hPhase;
		private int hDirection;

		private final float CIRCLE_SIZE = 60;

		private final static long ANIM_RATE = 50;
		private final static long INTERVAL = 2000;

		private final PointD mIndicatorPosition = new PointD();

		private final PointD mScreenPoint = new PointD();
		private final Box mBBox = new Box();

		private boolean mInitialized;

		private boolean mLocationIsVisible;

		private boolean mRunAnim;
		private long mAnimStart;

		public LocationIndicator(final MapView mapView) {
			super(mapView);
		}

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
				init();
				mInitialized = true;
			}

			if (!isEnabled()){
				isReady = false;
				return;
			}

			if (!changed && isReady)
				return;

			isReady = true;

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

			if (x > width - 5)
				x = width;
			else if (x < 5)
				x = 0;
			else
				visible++;

			if (y > height - 5)
				y = height;
			else if (y < 5)
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

			animate(true);
			boolean viewShed = false;
			if (!mLocationIsVisible || pos.zoomLevel < SHOW_ACCURACY_ZOOM) {
				//animate(true);
			} else {
				radius = (float) (mRadius * pos.scale);
				viewShed = true;
				//animate(false);
			}
			GLES20.glUniform1f(hScale, radius);

			double x = mIndicatorPosition.x - pos.x;
			double y = mIndicatorPosition.y - pos.y;
			double tileScale = Tile.SIZE * pos.scale;

			m.mvp.setTransScale((float) (x * tileScale), (float) (y * tileScale), 1);
			m.mvp.multiplyMM(m.viewproj, m.mvp);
			m.mvp.setAsUniform(hMatrixPosition);

			if (!viewShed) {
				float phase = Math.abs(animPhase() - 0.5f) * 2;
				//phase = Interpolation.fade.apply(phase);
				phase = Interpolation.swing.apply(phase);

				GLES20.glUniform1f(hPhase, 0.8f + phase * 0.2f);
				GLES20.glUniform2f(hDirection, 0, 0);

			} else {
				GLES20.glUniform1f(hPhase, 1);
			}

			if (viewShed && mLocationIsVisible) {
				float rotation = mCompass.getRotation() - 90;
				GLES20.glUniform2f(hDirection,
						(float) Math.cos(Math.toRadians(rotation)),
						(float) Math.sin(Math.toRadians(rotation)));
			}

			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		}

		private boolean init() {
			int shader = GlUtils.createProgram(vShaderStr, fShaderStr);
			if (shader == 0)
				return false;

			mShaderProgram = shader;
			hVertexPosition = GLES20.glGetAttribLocation(shader, "a_pos");
			hMatrixPosition = GLES20.glGetUniformLocation(shader, "u_mvp");
			hPhase = GLES20.glGetUniformLocation(shader, "u_phase");
			hScale = GLES20.glGetUniformLocation(shader, "u_scale");
			hDirection = GLES20.glGetUniformLocation(shader, "u_dir");

			return true;
		}

		private final static String vShaderStr = ""
				+ "precision mediump float;"
				+ "uniform mat4 u_mvp;"
				+ "uniform float u_phase;"
				+ "uniform float u_scale;"
				+ "attribute vec2 a_pos;"
				+ "varying vec2 v_tex;"
				+ "void main() {"
				+ "  gl_Position = u_mvp * vec4(a_pos * u_scale * u_phase, 0.0, 1.0);"
				+ "  v_tex = a_pos;"
				+ "}";

		private final static String fShaderStr = ""
				+ "precision mediump float;"
				+ "varying vec2 v_tex;"
				+ "uniform float u_scale;"
				+ "uniform float u_phase;"
				+ "uniform vec2 u_dir;"
				+ "void main() {"
				+ "  float len = 1.0 - length(v_tex);"
				///  blur outline by 2px
				+ "  float a = smoothstep(0.0, 2.0 / u_scale, len);"
				+ "  float b = smoothstep(4.0 / u_scale, 5.0 / u_scale, len);"
				///  center point
				+ "  float c = 1.0 - smoothstep(14.0 / u_scale, 16.0 / u_scale, 1.0 - len);"
				+ "  vec2 dir = normalize(v_tex);"
				+ "  float d = 1.0 - dot(dir, u_dir); "
				///  0.5 width of viewshed + antialiasing
				+ "  d = step(0.5, d);"
				+ "  a = clamp(d, 0.4, 0.7) * clamp(a - (b + c) * 0.5 , 0.0, 1.0) + c * 0.5;"
				//+ "  a = a - clamp(d, 0.6, 0.8) * b;"
				+ "  gl_FragColor = vec4 (0.2, 0.2, 0.8, 1.0) * clamp(a, 0.0, 1.0);"
				+ "}";

	}
}
