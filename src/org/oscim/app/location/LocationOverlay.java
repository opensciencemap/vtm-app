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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Timer;
import java.util.TimerTask;

import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.core.PointF;
import org.oscim.core.Tile;
import org.oscim.layers.overlay.GenericOverlay;
import org.oscim.layers.overlay.Overlay;
import org.oscim.renderer.GLRenderer;
import org.oscim.renderer.GLRenderer.Matrices;
import org.oscim.renderer.GLState;
import org.oscim.renderer.layers.BasicRenderLayer;
import org.oscim.renderer.layers.test.TestRenderLayer;
import org.oscim.utils.GlUtils;
import org.oscim.view.MapView;

import android.opengl.GLES20;

public class LocationOverlay extends Overlay {

	private float radius;
	private float mLatitude; // storing the information related to the circle pos
	private float mLongitude;

	public boolean StopAnimation;
	public int heightnum;

	public float getRadius() {
		return radius;
	}

	public int getColorvar() {
		return colorvar;
	}

	public void setRadius(float radius) {
		this.radius = radius;
	}

	static GenericOverlay orientationIndicator = null;

	public class CustomOverlay extends BasicRenderLayer {
		private final Timer timer;  // animation Timer
		private int mProgramObject;
		private int hVertexPosition;
		private int hMatrixPosition;
		private int hzoomlevel;
		float width;
		int hcolor;

		int hwave;
		FloatBuffer colorb;
		float[] colordata;
		float wave = .1f;
		int i = 0;

		private FloatBuffer mVertices;
		private final float[] mVerticesData;
		private boolean mInitialized;
		boolean tick; // animation variables
		long delay;
		boolean start;

		boolean countDown;

		int counter;
		float IndicationX, IndicationY;
		float multy = 1;

		float myRadius;

		final float circleMaxSize = 700;

		public CustomOverlay(final MapView mapView, float radius, int colorvar2) {
			super(mapView);
			//i=0;

			myRadius = radius;
			orientationIndicator = new GenericOverlay(mMapView, new TestRenderLayer(mMapView));

			delay = 100;
			timer = new Timer();

			//timer.cancel();
			countDown = false;            // Generating the motion of the circle
			timer.schedule(new TimerTask() {

				@Override
				public void run() {
					// TODO Auto-generated method stub

					if (wave < .28f && !countDown) {
						wave += (.02f * multy);
						multy += .001f;
						mapView.render();

					}
					else
						counter++;

					if (wave >= .28f && counter == 2 && !countDown) {
						countDown = true;
						counter = 0;
						mapView.render();
					}

					if (countDown) {

						counter = 0;
						wave -= (.02f * multy);
						multy -= .001f;
						if (wave <= 0) {
							countDown = false;
							wave = 0;
							multy = 1;
						}

						mapView.render();
					}

					tick = true;
				}

			}
					, 400, delay);
			mVerticesData = new float[] {
					-radius, -radius, -1, -1,
					-radius, radius, -1, 1,
					radius, -radius, 1, -1,
					radius, radius, 1, 1

			};

			width = mapView.getWidth();

			//scaleBy = 35/ radius;
			colordata = new float[] { colorvar, 0, 0, 0 };

		}

		// ---------- everything below runs in GLRender Thread ----------

		float scaleBy;
		PointF dd = new PointF();
		PointF dd2 = new PointF();
		MapPosition temp = new MapPosition();
		int widthnum = 1;
		boolean Left_Right;
		boolean Top_Down;
		int heightnum = 1;

		@Override
		public void update(MapPosition curPos, boolean changed, Matrices matrices) {
			if (!mInitialized) {
				if (!init())
					return;

				mInitialized = true;

				// tell GLRender to call 'compile' when data has changed
				newData = true;

				// fix current MapPosition

				temp = curPos;
				mMapPosition.copy(curPos);
				mMapPosition.setZoomLevel(12);

				mMapView.getOverlays().remove(orientationIndicator);
				mMapView.getOverlays().add(orientationIndicator);

				//	Log.v("a", "I");
				//	mMapPosition.setFromLatLon(lat, log, mMapPosition.zoomLevel);

			}

			if (curPos.zoomLevel <= 12 || !mMapView.getMapViewPosition().getViewBox().contains(
					temp.getGeoPoint())) {
				GeoPoint diff = new GeoPoint((mLatitude), (
						mLongitude));

				mMapView.getMapViewPosition().project(diff, dd2);
				if (Math.abs(dd2.x) > width / 2 * widthnum) {
					this.widthnum += 1;
					//scaleBy -=.01f;
				}
				if (Math.abs(dd2.x) < width / 2 * (widthnum - 1)) {
					this.widthnum -= 1;
					//scaleBy +=.01f;
				}

				if (Math.abs(dd2.y) > mMapView.getHeight() / 2 * heightnum) {
					this.heightnum += 1;
					//	scaleBy -=.01f;
				}
				if (Math.abs(dd2.y) < mMapView.getHeight() / 2 * (heightnum - 1)) {
					this.heightnum -= 1;

				}

				if (widthnum == 2)
					if (((dd2.x) / width * 2) > 1.1f) {
						Left_Right = true;
						IndicationX = width;
					}
					else {
						Left_Right = false;
						IndicationX = 0;
					}

				if (heightnum == 2)
					if (((dd2.y) * mMapView.getHeight() * 2) > 1.1f) {
						Top_Down = false;
						IndicationY = mMapView.getHeight();
					}
					else {
						Top_Down = true;
						IndicationY = 0;
					}

				if (widthnum == 1) {

					if ((dd2.x / width) > 1)
						IndicationX = 1.5f - (dd2.x / width);
					else
						IndicationX = .5f + (dd2.x / width);

					IndicationX *= width;

				}
				if (heightnum == 1) {

					if ((dd2.x / mMapView.getHeight()) > 1.1f) {
						IndicationY = .5f - (dd2.y / mMapView.getHeight());
						IndicationY -= 1;
						IndicationY = Math.abs(IndicationY);

					}
					else
						IndicationY = .5f + (dd2.y / mMapView.getHeight());

					IndicationY *= mMapView.getHeight();

				}

				GeoPoint Indication;
				GeoPoint P1 = new GeoPoint(mLatitude, mLongitude);
				if (!mMapView.getBoundingBox().contains(P1)) {

					start = false;

					if (widthnum > 1 && heightnum > 1) {

						Indication = mMapView.getMapViewPosition().fromScreenPixels(IndicationX,
								IndicationY);
						mMapPosition.setPosition(Indication.getLatitude(),
								Indication.getLongitude());
					}

					if (widthnum == 1) {
						if (!Top_Down)
							Indication = mMapView.getMapViewPosition().fromScreenPixels(
									IndicationX, mMapView.getHeight());
						else
							Indication = mMapView.getMapViewPosition().fromScreenPixels(
									IndicationX, 0);
						mMapPosition.setPosition(Indication.getLatitude(),
								Indication.getLongitude());
					}

					if (heightnum == 1) {

						if (Left_Right)
							Indication = mMapView.getMapViewPosition().fromScreenPixels(width,
									IndicationY);
						else
							Indication = mMapView.getMapViewPosition().fromScreenPixels(0,
									IndicationY);
						mMapPosition.setPosition(Indication.getLatitude(),
								Indication.getLongitude());
					}
				}
				else {
					mMapPosition.setPosition(P1.getLatitude(), P1.getLongitude());

					start = true;
				}
			}
		}

		boolean once;

		@Override
		public void compile() {
			// modify mVerticesData and put in FloatBuffer

			mVertices.clear();
			mVertices.put(mVerticesData);
			mVertices.flip();

			newData = false;

			// tell GLRender to call 'render'
			isReady = true;
		}

		@Override
		public void render(MapPosition pos, Matrices m) {

			// Use the program object
			GLState.useProgram(mProgramObject);

			GLState.blend(true);
			GLState.test(false, false);

			// unbind previously bound VBOs
			GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
			// Load the vertex data

			GLES20.glVertexAttribPointer(hVertexPosition, 4, GLES20.GL_FLOAT, false, 0, mVertices);

			GLState.enableVertexArrays(hVertexPosition, -1);

			GLES20.glEnableVertexAttribArray(0);
			/* apply view and projection matrices */
			// set mvp (tmp) matrix relative to mMapPosition
			// i.e. fixed on the map

			if (pos.zoomLevel <= 12
					|| !mMapView.getMapViewPosition().getViewBox().contains(temp.getGeoPoint()))
			{

				float scaleBy = (circleMaxSize / myRadius);

				//				 if ( scaleBy < .4f)
				//					 scaleBy= .5f;
				//Log.v("scaleby", String.valueOf(scaleBy));
				setMatrix2(pos, m, scaleBy);

			} else
				setMatrix(pos, m);
			//

			m.mvp.setAsUniform(hMatrixPosition);
			GLES20.glUniform1f(hcolor, colorvar);
			GLES20.glUniform1f(hwave, wave);

			if (radius >= 100 && pos.zoomLevel >= 15)
				GLES20.glUniform1f(hzoomlevel, 16);
			else
				GLES20.glUniform1f(hzoomlevel, pos.zoomLevel);

			if (!mMapView.getMapViewPosition().getViewBox().contains(temp.getGeoPoint()))
				GLES20.glUniform1f(hzoomlevel, 12);
			// Draw the triangle

			if (StopAnimation)
				GLES20.glUniform1f(hzoomlevel, 20);
			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

			i++;
			GlUtils.checkGlError("...");
		}

		private void setMatrix2(MapPosition curPos, Matrices m, float width) {
			MapPosition oPos = mMapPosition;

			// flip around date-line
			double x = oPos.x - curPos.x;
			double y = oPos.y - curPos.y;

			// scale to current tile world coordinates
			double tileScale = Tile.SIZE * curPos.scale;

			m.mvp.setTransScale((float) (x * tileScale), (float) (y * tileScale),
					(1 / GLRenderer.COORD_SCALE) * width);

			m.mvp.multiplyMM(m.viewproj, m.mvp);
		}

		int animation = 0;

		private boolean init() {
			// Load the vertex/fragment shaders
			int programObject = GlUtils.createProgram(vShaderStr, fShaderStr);

			if (programObject == 0)
				return false;

			// Handle for vertex position in shader
			hVertexPosition = GLES20.glGetAttribLocation(programObject, "a_pos");

			hMatrixPosition = GLES20.glGetUniformLocation(programObject, "u_mvp");

			hcolor = GLES20.glGetUniformLocation(programObject, "radius");
			hwave = GLES20.glGetUniformLocation(programObject, "wave");
			hzoomlevel = GLES20.glGetUniformLocation(programObject, "zoomLevel");

			// Store the program object
			mProgramObject = programObject;

			mVertices = ByteBuffer.allocateDirect(mVerticesData.length * 4)
					.order(ByteOrder.nativeOrder()).asFloatBuffer();

			return true;
		}

		private final static String vShaderStr = ""
				+ "precision mediump float;"

				+ "uniform mat4 u_mvp;"

				+ "uniform float radius;"
				+ " uniform float  wave ; "
				+ "uniform float zoomLevel;"

				+ "attribute vec4 a_pos;"
				+ "varying vec2 tex;"
				+ " varying vec4 v_color;   "
				+ "void main()"
				+ "{"
				+ "if (zoomLevel <= 0.0)  "

				+ "gl_Position = u_mvp * vec4(a_pos.xy, 0.0, .31- wave);"

				+ "else " +
				" gl_Position = u_mvp * vec4(a_pos.xy, 0.0, 1);"
				+ "   tex = a_pos.zw;"
				+ " if( zoomLevel <=15.0) {"
				+ "        if (radius > .5 ) "
				+ "        v_color = vec4 ( .5-wave,1.0,0 , .31 - wave );     "
				+ "                  else "
				+ "      v_color = vec4 ( wave,0.23,1,1);      "
				+ "}"

				+ "else { "

				+ "        if (radius > .5 ) "

				+ "        v_color = vec4 ( .5,1.0,0 ,1.0);     "
				+ "                  else "
				+ "      v_color = vec4 ( .2,0,1.0,1.0);      "

				+ "}"
				+ "}";

		private final static String fShaderStr = ""
				+ "precision mediump float;"
				+ "varying vec2 tex;"
				+ "varying vec4 v_color;"

				+ "void main()"
				+ "{"

				+ "   float a =.5- smoothstep(v_color.x , 1.0, length(tex));"

				+ " float  b =smoothstep(v_color.x  , 1.0*.3, length(tex) ) ;"
				+ "a= a- (a*b);"

				+ "  gl_FragColor = vec4(v_color.x * a , v_color.y * a,v_color.z *a,a * v_color.w);"
				+ "}";

	}

	public int colorvar;

	public LocationOverlay(MapView mapView, float radius, int colorvar) {
		super(mapView);

		mLayer = new CustomOverlay(mapView, radius, colorvar);

	}

	public float getLat() {
		return mLatitude;
	}

	public void setLat(float lat) {
		this.mLatitude = lat;
	}

	public float getLon() {
		return mLongitude;
	}

	public void setLon(float lon) {
		this.mLongitude = lon;
	}
}
