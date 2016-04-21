package com.davidmascharka.lips;

import java.io.FileNotFoundException;
import java.io.InputStream;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.mascharka.indoorlocalization.R;

/**
 *  Copyright 2015 David Mascharka
 * 
 * This file is part of LIPS (Learning-based Indoor Positioning System).
 *
 *  LIPS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  LIPS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with LIPS.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * @author David Mascharka (david.mascharka@drake.edu)
 */
public class GridView extends View {

	private ScaleGestureDetector scaleDetector;
	private float scaleFactor = 1.0f;
	
	private final static String KEY = "com.mascharka.indoorlocalization";
	
	private int gridWidth;
	private int gridHeight;
	
	private Paint gridPaint;
	private Paint pointPaint;
	
	// Coordinates for drawing point at user's location
	private float pointX;
	private float pointY;
	
	// Focal point coordinates for zooming
	private float focalX;
	private float focalY;
	
	// Members for panning
	private float startX;
	private float startY;
	private float translateX;
	private float translateY;
	private float lastTranslateX;
	private float lastTranslateY;
	private Rect clipBounds;
	
	// Members for displaying a map of the area
	private boolean displayMap;
	private Drawable map;
	private Uri mapUri;
	private Rect imageBounds;
	
	// Whether this view should take input
	private boolean catchInput;
	
	public GridView(Context context) {
		this(context, null, 0);
	}
	
	public GridView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}
	
	public GridView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
		
		gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		gridPaint.setStyle(Paint.Style.STROKE);
		gridPaint.setStrokeWidth(0);
		gridPaint.setColor(Color.BLACK);
		
		pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		pointPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		pointPaint.setStrokeWidth(20);
		pointPaint.setColor(Color.BLUE);
		
		clipBounds = new Rect();

		map = null;
		mapUri = null;
		
		imageBounds = new Rect();
		
		catchInput = true;
	}
	
	public void setCatchInput(boolean catchInput) {
		this.catchInput = catchInput;
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
	      super.onDraw(canvas);
	      
	      canvas.getClipBounds(imageBounds);
	      
	      canvas.save();
	      
	      // Zoom around the focal point
	      canvas.scale(scaleFactor, scaleFactor, focalX, focalY);
	      // Translate the canvas
	      // Constrain scrolling to stay in the grid
	      canvas.getClipBounds(clipBounds);
	      
	      if (clipBounds.left - (translateX / scaleFactor) < 0) {
	    	  translateX = clipBounds.left * scaleFactor;
	    	  lastTranslateX = clipBounds.left * scaleFactor;
	      }
	      if (clipBounds.top - (translateY / scaleFactor) < 0) {
	    	  translateY = clipBounds.top * scaleFactor;
	    	  lastTranslateY = clipBounds.top * scaleFactor;
	      }
	      if (clipBounds.right - (translateX / scaleFactor) > getWidth()) {
	    	  translateX = (clipBounds.right - getWidth()) * scaleFactor;
	    	  lastTranslateX = (clipBounds.right - getWidth()) * scaleFactor;
	      }
	      if (clipBounds.bottom - (translateY / scaleFactor) > getHeight()) {
	    	  translateY = (clipBounds.bottom - getHeight()) * scaleFactor;
	    	  lastTranslateY = (clipBounds.bottom - getHeight()) * scaleFactor;
	      }
	      
	      canvas.translate(translateX / scaleFactor, translateY / scaleFactor);
	      canvas.getClipBounds(clipBounds);
	      
	      // Background color
	      canvas.drawColor(Color.WHITE);
	      
	      // If the user opts to display a map, draw it if they have selected one
	      if (displayMap) {
	    	  if (map != null) {
	    		  map.setBounds(imageBounds);
	    		  map.draw(canvas);
	    	  }
	      }
	      
	      // Float casts below ensure correct drawing -> eliminate rounding errors due to
	      // integer division
	      
	      // Draw grid lines in x dimension (vertical lines)
	      for (int x = 0; x <= gridWidth; x++) {
	         canvas.drawLine(x * ((float) getWidth() / gridWidth), 0,
	                x * ((float) getWidth() / gridWidth), getHeight(), gridPaint);
	      }
	
	      // Draw grid lines in y dimension (horizontal lines)
	      for (int y = 0; y <= gridHeight; y++) {
	         canvas.drawLine(0, y * ((float) getHeight() / gridHeight), getWidth(),
	        		 y * ((float) getHeight() / gridHeight), gridPaint);
	      }
	      
	      // Draw the user's touch point
	      canvas.drawCircle(pointX, pointY, 1, pointPaint);
	      
	      canvas.restore();
	   }
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
				MeasureSpec.getSize(heightMeasureSpec));
	}
	
	public int getGridWidth() {
		return gridWidth;
	}
	
	public int getGridHeight() {
		return gridHeight;
	}
	
	public void setGridSize(int width, int height) {
		gridWidth = width;
		gridHeight = height;
		
		// Need to redraw canvas if the grid size has changed
		invalidate();
	}
	
	public void setDisplayMap(boolean display) {
		displayMap = display;
		
		// Need to redraw canvas if the status of drawMap has changed
		invalidate();
	}

	public void setMapUri(Uri uri) {
		// Don't do anything if the uri passed in is null
		if (uri == null) {
			return;
		}
		
		// Otherwise, try to load the map and display it
		try {
			mapUri = uri;
			InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
			map = Drawable.createFromStream(inputStream, uri.toString());
			invalidate();
		} catch (FileNotFoundException e) {
			Toast.makeText(getContext(), "File not found", Toast.LENGTH_SHORT).show();
		}
	}
	
	@Override
	protected Parcelable onSaveInstanceState() {
		Bundle bundle = new Bundle();
		
		Parcelable parentState = super.onSaveInstanceState();
		
		bundle.putParcelable(KEY + ".parentState", parentState);
		bundle.putInt(KEY + ".width", gridWidth);
		bundle.putInt(KEY + ".height", gridHeight);
		bundle.putBoolean(KEY + ".displayMap", displayMap);
		bundle.putParcelable(KEY + ".mapUri", mapUri);
		
		return bundle;
	}
	
	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		if (state == null) {
			super.onRestoreInstanceState(state);
		} else {
			Bundle bundle = (Bundle) state;
			gridWidth = bundle.getInt(KEY + ".width");
			gridHeight = bundle.getInt(KEY + ".length");
			displayMap = bundle.getBoolean(KEY + ".displayMap");
			mapUri = bundle.getParcelable(KEY + ".mapUri");
			
			setMapUri(mapUri);
			setGridSize(gridWidth, gridHeight);
			
			super.onRestoreInstanceState(bundle.getParcelable(KEY + ".parentState"));
		}
	}
	
	private float getVirtualWidth() {
		return gridWidth / scaleFactor;
	}
	
	private float getVirtualHeight() {
		return gridHeight / scaleFactor;
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (!catchInput) {
			return false;
		}
		
		// Pass all touch events to the scale detector to inspect
		scaleDetector.onTouchEvent(ev);
		
		// Get the x and y textviews
		TextView xText = (TextView) ((Activity) getContext()).findViewById(R.id.text_xposition);
		TextView yText = (TextView) ((Activity) getContext()).findViewById(R.id.text_yposition);
		
		// Compute touch coordinates relative to the grid size
		float xTouch = (ev.getX() / getWidth()) * getVirtualWidth();
		float yTouch = (getHeight() - ev.getY()) / getHeight() * getVirtualHeight();
		
		// Compute x and y offsets for when the user has panned around the canvas
		float xOffset = (float) clipBounds.left / getWidth() * gridWidth;
		float yOffset = ((float) getHeight() - clipBounds.bottom) / getHeight() * gridHeight;
		
		// Compute x and y coordinates of the touch relative to the canvas
		pointX = ev.getX() / scaleFactor + clipBounds.left;
		pointY = ev.getY() / scaleFactor + clipBounds.top;
		
		// Set x and y coordinate textviews
		xText.setText("X: " + (xTouch + xOffset));
		yText.setText("Y: " + (yTouch + yOffset));

		switch (ev.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:	// First pointer down
				startX = ev.getX() - lastTranslateX;
				startY = ev.getY() - lastTranslateY;
				break;
			case MotionEvent.ACTION_MOVE:	// Pointer moves
				if (!scaleDetector.isInProgress()) {	// If not scaling
					translateX = ev.getX() - startX;
					translateY = ev.getY() - startY;
					invalidate();
				} else {
					startX = ev.getX() - lastTranslateX;
					startY = ev.getY() - lastTranslateY;
					lastTranslateX = translateX;
					lastTranslateY = translateY;
				}
				break;
			case MotionEvent.ACTION_UP:	// Last pointer up
				lastTranslateX = translateX;
				lastTranslateY = translateY;
				break;
		}
		
		// Redraw canvas
		invalidate();
		
		return true;
	}
	
	public void setUserPointCoords(float x, float y) {
		pointX = x * getWidth() / gridWidth / scaleFactor + clipBounds.left;
		pointY = getHeight() - y * getHeight() / gridHeight / scaleFactor + clipBounds.top;
		invalidate();
	}

	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			scaleFactor *= detector.getScaleFactor();
			
			focalX = detector.getFocusX();
			focalY = detector.getFocusY();
			
			// Constrain scale
			// Max scale 20x zoom, min scale 1x zoom
			scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 20.0f));
			
			pointPaint.setStrokeWidth(20 / scaleFactor);

			return true;
		}
	}
}