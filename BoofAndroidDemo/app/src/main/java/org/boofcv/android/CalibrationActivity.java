package org.boofcv.android;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.ddogleg.struct.FastQueue;

import java.util.ArrayList;
import java.util.List;

import boofcv.abst.calib.ConfigChessboard;
import boofcv.abst.calib.ConfigSquareGrid;
import boofcv.abst.calib.PlanarCalibrationDetector;
import boofcv.android.ConvertBitmap;
import boofcv.android.gui.VideoRenderProcessing;
import boofcv.factory.calib.FactoryPlanarCalibrationTarget;
import boofcv.struct.image.ImageFloat32;
import boofcv.struct.image.ImageType;
import georegression.geometry.UtilPoint2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;

import android.view.Display;
import android.util.DisplayMetrics;
/**
 * Activity for collecting images of calibration targets. The user must first specify the type of target it is
 * searching for and click the screen to add the image.
 **/

public class CalibrationActivity extends PointTrackerDisplayActivity
{
	public static final int TARGET_DIALOG = 10;

	public static int targetType = 0;
	public static int numRows = 5;
	public static int numCols = 7;

    Bitmap nickCage;

	Paint paintPoint = new Paint();
	Paint paintFailed = new Paint();

	// Storage for calibration info
	List<CalibrationImageInfo> shots;

	// user has requested that the next image be processed for the target
	boolean captureRequested = false;

	// user has requested that the most recent image be removed from data list
	boolean removeRequested = false;

	// displays the number of calibration images captured
	TextView textCount;

    // Initial found corners from Process
    Point2D_F64 C1,C2,C3,C4;

    // Final four corners of tracked image
    Point2D_F64 topLeft, topRight, botLeft, botRight;

    // Degrees of rotation
    float degree;

    Spinner spinnerTarget;
    EditText textRows;
    EditText textCols;

    // true if detect failed
	boolean showDetectDebug;

	// the user requests that the images be processed
	boolean processRequested = true;

	// pause the display so that it doesn't change until after this time
	// long timeResume;

	// handles gestures
	GestureDetector mDetector;

	public CalibrationActivity() {
		paintPoint.setColor(Color.RED);
		paintPoint.setStyle(Paint.Style.FILL);

		paintFailed.setColor(Color.CYAN);
		paintFailed.setStyle(Paint.Style.FILL);
		paintFailed.setStrokeWidth(12f);
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.calibration_view,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);

		//textCount = (TextView)controls.findViewById(R.id.text_total);



		shots = new ArrayList<CalibrationImageInfo>();

		FrameLayout iv = getViewPreview();
		mDetector = new GestureDetector(this, new MyGestureDetector(iv));
		iv.setOnTouchListener(new View.OnTouchListener(){
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				mDetector.onTouchEvent(event);
				return true;
			}});

        nickCage = BitmapFactory.decodeResource(getResources(), R.drawable.thecage);
	}

	@Override
	protected void onResume() {
		super.onResume();  // Always call the superclass method first
		startVideoProcessing();

		if( DemoMain.preference.intrinsic != null ) {
			Toast.makeText(this, "Camera already calibrated", Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Configures the detector, configures target description for calibration and starts the detector thread.
	 */
	private void startVideoProcessing() {
		PlanarCalibrationDetector detector;

		if( targetType == 0 ) {
			ConfigChessboard config = new ConfigChessboard(numCols,numRows);
			detector = FactoryPlanarCalibrationTarget.detectorChessboard(config);
			CalibrationComputeActivity.target = FactoryPlanarCalibrationTarget.gridChess(numCols, numRows, 30);
		} else {
			ConfigSquareGrid config = new ConfigSquareGrid(numCols,numRows);
			detector = FactoryPlanarCalibrationTarget.detectorSquareGrid(config);
			CalibrationComputeActivity.target = FactoryPlanarCalibrationTarget.gridSquare(numCols, numRows, 30,30);
		}
		setProcessing(new DetectTarget(detector));
	}

	public void pressedOK( View view ) {
		processRequested = true;
	}

	public void pressedRemove( View view ) {
		removeRequested = true;
	}

	public void pressedHelp( View view ) {
		Intent intent = new Intent(this, CalibrationHelpActivity.class);
		startActivity(intent);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
			case TARGET_DIALOG:
				ManageDialog dialog = new ManageDialog();
				dialog.create(this);
		}
		return super.onCreateDialog(id);
	}

	/**
	 * Checks to see if there are enough images and launches the activity for computing intrinsic parameters.
	 * Only call from a thread where 'shots' is not going to be modified
	 */
	private void handleProcessRequest() {
		if( shots.size() < 5 ) {
			Toast.makeText(this, "Calibrating", Toast.LENGTH_SHORT).show();
		}

        else if(shots.size() == 5) {
            getWindow().getDecorView().findViewById(android.R.id.content).performClick();
        }

        else {
			CalibrationComputeActivity.images = shots;
			Intent intent = new Intent(this, CalibrationComputeActivity.class);
			startActivity(intent);
		}
	}

	protected class MyGestureDetector extends GestureDetector.SimpleOnGestureListener
	{
		View v;

		public MyGestureDetector(View v) {
			this.v = v;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			captureRequested = true;
			return true;
		}
	}

	private class ManageDialog {


		public void create( Context context ) {
			LayoutInflater inflater = getLayoutInflater();
			LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.calibration_configure,null);
			// Create out AlterDialog
			AlertDialog.Builder builder = new AlertDialog.Builder(context);
			builder.setView(controls);
			builder.setCancelable(true);
			builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialogInterface, int i) {
					CalibrationActivity.numCols = Integer.parseInt(textCols.getText().toString());
					CalibrationActivity.numRows = Integer.parseInt(textRows.getText().toString());
					CalibrationActivity.targetType = spinnerTarget.getSelectedItemPosition();
					startVideoProcessing();
				}
			});

			spinnerTarget = (Spinner) controls.findViewById(R.id.spinner_type);
			textRows = (EditText) controls.findViewById(R.id.text_rows);
			textCols = (EditText) controls.findViewById(R.id.text_cols);

			textRows.setText(""+CalibrationActivity.numRows);
			textCols.setText(""+CalibrationActivity.numCols);

			setupTargetSpinner();

			AlertDialog dialog = builder.create();
			dialog.show();
		}

		private void setupTargetSpinner() {
			ArrayAdapter<CharSequence> adapter =
					new ArrayAdapter<CharSequence>(CalibrationActivity.this, android.R.layout.simple_spinner_item);
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			adapter.add("Chessboard");
			adapter.add("Square Grid");

			spinnerTarget.setAdapter(adapter);
		}

	}

	private class DetectTarget extends VideoRenderProcessing<ImageFloat32> {

		PlanarCalibrationDetector detector;

		FastQueue<Point2D_F64> pointsGui = new FastQueue<Point2D_F64>(Point2D_F64.class,true);

		List<List<Point2D_I32>> debugQuads = new ArrayList<List<Point2D_I32>>();

		Bitmap bitmap;
		byte[] storage;

		protected DetectTarget( PlanarCalibrationDetector detector ) {
			super(ImageType.single(ImageFloat32.class));
			this.detector = detector;
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);
			bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
			storage = ConvertBitmap.declareStorage(bitmap, storage);
		}

		@Override
		protected void process(ImageFloat32 gray) {
			// User requested that the most recently processed image be removed
			if( removeRequested || shots.size() > 100) {
				removeRequested = false;
				if( shots.size() > 0 )  {
					shots.remove( shots.size()-1 );
					updateShotCountInUiThread();
				}
			}

			/*if( timeResume > System.currentTimeMillis() )
				return;*/

			synchronized ( lockGui ) {
				ConvertBitmap.grayToBitmap(gray,bitmap,storage);
			}

			boolean detected = false;
			showDetectDebug = false;
			if( captureRequested ) {
				captureRequested = true;
				detected = collectMeasurement(gray);
			}

			// safely copy data into data structures used by GUI thread
			synchronized ( lockGui ) {
                ConvertBitmap.grayToBitmap(gray, bitmap, storage);
                pointsGui.reset();
                debugQuads.clear();
                if (detected) {
                    List<Point2D_F64> found = detector.getPoints();
                    for (Point2D_F64 p : found)
                        pointsGui.grow().set(p);
                }
            }

            if(pointsGui.size() > 0) {
                // Points first run through detect as 'corners'
                C1 = pointsGui.get(0);
                C2 = pointsGui.get(0);
                C3 = pointsGui.get(0);
                C4 = pointsGui.get(0);

                for (int i = 0; i < 24; i++) {
                    // If equal points, continue
                    //Point2D_F64 point = pointsGui.get(i);

                    if (compare(pointsGui.get(i), C1) || compare(pointsGui.get(i), C2) || compare(pointsGui.get(i), C3) || compare(pointsGui.get(i), C4))
                        continue;

                    if ((pointsGui.get(i).getX() <= C1.getX()) && (pointsGui.get(i).getY() < C1.getY())) {
                        C1 = pointsGui.get(i);
                        continue;
                    }

                    if ((pointsGui.get(i).getX() < C3.getX()) && (pointsGui.get(i).getY() > C3.getY())) {
                        C3 = pointsGui.get(i);
                        continue;
                    }

                    if ((pointsGui.get(i).getX() > C2.getX()) && (pointsGui.get(i).getY() < C2.getY())) {
                        C2 = pointsGui.get(i);
                        continue;
                    }

                    if ((pointsGui.get(i).getX() > C4.getX()) && (pointsGui.get(i).getY() > C4.getY())) {
                        C4 = pointsGui.get(i);
                        continue;
                    }
                }

                ArrayList<Point2D_F64> list = new ArrayList<Point2D_F64>();
                list.add(C1);
                list.add(C2);
                list.add(C3);
                list.add(C4);

                Point2D_F64 top1 = list.get(0);
                Point2D_F64 top2 = list.get(0);
                Point2D_F64 bot1 = list.get(0);
                Point2D_F64 bot2 = list.get(0);

                // Find top points
                for (int i = 0; i < 4; i++) {
                    if (list.get(i).getY() < top2.getY()) {
                        if (list.get(i).getY() < top1.getY()) {
                            top2 = top1;
                            top1 = list.get(i);
                        } else top2 = list.get(i);
                    }
                }

                // Decide which top point is right and which is left
                if (top1.getX() < top2.getX()) {
                    topLeft = top1;
                    topRight = top2;
                } else if (top1.getX() > top2.getX()) {
                    topLeft = top2;
                    topRight = top1;
                }

                // If equal, add 1 to top2.x, let it be the right
                else {
                    top2.x++;
                    topRight = top2;
                    topLeft = top1;
                }


                // Find bottom points
                for (int i = 0; i < 4; i++) {
                    if (list.get(i).getY() > bot2.getY()) {
                        if (list.get(i).getY() > bot1.getY()) {
                            bot2 = bot1;
                            bot1 = list.get(i);
                        } else bot2 = list.get(i);
                    }
                }

                // Decide which bottom point is right and which is left
                if (bot1.getX() < bot2.getX()) {
                    botLeft = bot1;
                    botRight = bot2;
                } else if (bot1.getX() > bot2.getX()) {
                    botLeft = bot2;
                    botRight = bot1;
                }

                // If equal, add 1 to bot2.x, let it be the right
                else {
                    bot2.x++;
                    botRight = bot2;
                    botLeft = bot1;
                }

                // Find most Right, Top, Left, and Bottom Axes
                float mostRight = (float) Math.max(topRight.getX(), botRight.getX());
                float mostTop = (float) Math.min(topRight.getY(), topLeft.getY());
                float mostLeft = (float) Math.min(topLeft.getX(), botLeft.getX());
                float mostBot = (float) Math.max(botLeft.getY(), botRight.getY());

                // Re-analyse points, keeping all points that meet/go beyond this
                ArrayList<Point2D_F64> rightAxis = new ArrayList<Point2D_F64>();
                ArrayList<Point2D_F64> topAxis = new ArrayList<Point2D_F64>();
                ArrayList<Point2D_F64> leftAxis = new ArrayList<Point2D_F64>();
                ArrayList<Point2D_F64> botAxis = new ArrayList<Point2D_F64>();

                for (int i = 0; i < 24; i++) {
                    if (pointsGui.get(i).getX() >= mostRight) rightAxis.add(pointsGui.get(i));
                    if (pointsGui.get(i).getX() <= mostLeft) leftAxis.add(pointsGui.get(i));
                    if (pointsGui.get(i).getY() >= mostBot) botAxis.add(pointsGui.get(i));
                    if (pointsGui.get(i).getY() <= mostTop) topAxis.add(pointsGui.get(i));
                }

                // Go through Right axis, get 'Top Right'
                for (int i = 0; i < rightAxis.size(); i++) {
                    if (rightAxis.get(i).getX() >= topRight.getX()) topLeft = rightAxis.get(i);
                }

                // Go through Bottom Axis, get 'Bottom Right'
                for (int i = 0; i < botAxis.size(); i++) {
                    if (botAxis.get(i).getY() >= botRight.getY()) botRight = botAxis.get(i);
                }

                // Go through Left Axis, get 'Bottom Left'
                for (int i = 0; i < leftAxis.size(); i++) {
                    if (leftAxis.get(i).getX() <= botLeft.getX()) botLeft = leftAxis.get(i);
                }

                // Go through Top Axis, get 'Top Right'
                for (int i = 0; i < topAxis.size(); i++) {
                    if (topAxis.get(i).getX() <= topRight.getX()) topRight = topAxis.get(i);
                }

                double dx = topLeft.getX() - botLeft.getX();
                double dy = topLeft.getY() - botLeft.getY();

                degree = (float) (-180/3.14 *  Math.atan(dx/dy));

            }

		}

		/**
		 * Detect calibration targets in the image and save the results.  Pause the display so the
		 * user can see the results]
		 */
		private boolean collectMeasurement(ImageFloat32 gray) {


			boolean success = detector.process(gray);

			// pause the display to provide feed back to the user
			// timeResume = System.currentTimeMillis()+1500;

			if( success ) {
				shots.add( new CalibrationImageInfo(gray,detector.getPoints()));
				updateShotCountInUiThread();
				return true;
			}  else {
				showDetectDebug = true;
				return false;
			}
		}

		/**
		 * Call when the number of shots needs to be updated from outside an UI thread
		 */
		private void updateShotCountInUiThread() {
			final int size = shots.size();
			runOnUiThread(new Runnable() {
				public void run() {
					//textCount.setText(""+size);
				}
			});
		}

		private boolean detectTarget(ImageFloat32 gray) {
			if( detector.process(gray) ) {
				return true;
			} else {
				showDetectDebug = true;
				return false;
			}
		}

		@Override
		protected void render(Canvas canvas, double imageToOutput) {
			// launch processing from here since you know data structures aren't being changed
			if( processRequested) {
				processRequested = false;
				handleProcessRequest();
			} else {
                Display display = getWindowManager().getDefaultDisplay();
                DisplayMetrics outMetrics = new DisplayMetrics ();
                display.getMetrics(outMetrics);
                float density  = getResources().getDisplayMetrics().density;
                float dpWidth  = outMetrics.widthPixels / density;

                Bitmap half = Bitmap.createScaledBitmap(bitmap, (int) dpWidth / 2, bitmap.getHeight(), true);
                canvas.drawBitmap(half,0,0,null);
                canvas.drawBitmap(half,half.getWidth(),0,null);

				// draw detected calibration points
				for( int i = 0; i < pointsGui.size(); i++ ) {
					Point2D_F64 p = pointsGui.get(i);
				}

                if (pointsGui.size() > 0) {

                    //1st Eye
                    paintPoint.setColor(Color.RED);
                    paintPoint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle((float)topLeft.getX()/2,(float)topLeft.getY(),5,paintPoint);

                    paintPoint.setColor(Color.BLUE);
                    paintPoint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle((float)topRight.getX()/2,(float)topRight.getY(),5,paintPoint);

                    paintPoint.setColor(Color.GREEN);
                    paintPoint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle((float)botLeft.getX()/2,(float)botLeft.getY(),5,paintPoint);

                    paintPoint.setColor(Color.YELLOW);
                    paintPoint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle((float)botRight.getX()/2,(float)botRight.getY(),5,paintPoint);

                    //2nd Eye
                    paintPoint.setColor(Color.RED);
                    paintPoint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle((float)topLeft.x/2 + half.getWidth(),(float)topLeft.y,5,paintPoint);

                    paintPoint.setColor(Color.BLUE);
                    paintPoint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle((float)topRight.x/2 + half.getWidth(),(float)topRight.y,5,paintPoint);

                    paintPoint.setColor(Color.GREEN);
                    paintPoint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle((float)botLeft.x/2 + half.getWidth(),(float)botLeft.y,5,paintPoint);

                    paintPoint.setColor(Color.YELLOW);
                    paintPoint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle((float)botRight.x/2 + half.getWidth(),(float)botRight.y,5,paintPoint);


                    double width = UtilPoint2D_F64.distance(topLeft.getX(), topLeft.getY(), topRight.getX(), topRight.getY());
                    double height = UtilPoint2D_F64.distance(topLeft.getX(), topLeft.getY(), botRight.getX(), botRight.getY());

                        try {

                            width = Math.abs(width);
                            height = Math.abs(height);

                            //Bitmap scaledBitmap = Bitmap.createScaledBitmap(nickCage, (int) width, (int) height, false);
                            Bitmap scaledBitmap = Bitmap.createScaledBitmap(nickCage, 90, 150, false);
                            Matrix matrix = new Matrix();
                            //matrix.postRotate(degree-45);
                            Log.d("Degree", "Degree: " + degree);
                            Bitmap rotatedBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);



                            // Draw Cage
                            Point2D_F64 point;

                            if(botLeft.getY() > topLeft.getY()) point = topRight;
                            else point = botLeft;

                            canvas.drawBitmap(rotatedBitmap, (float) point.getX()/2, (float) point.getY(), null);
                            canvas.drawBitmap(rotatedBitmap, (float) point.getX()/2 + half.getWidth(), (float) point.getY(), null);

                            /*

                            // Left Eye Lines
                            canvas.drawLine((float) topRight.getX()/2, (float)  topRight.getY(), (float)  topLeft.getX()/2,  (float) topLeft.getY(), paintPoint);
                            canvas.drawLine((float) topRight.getX()/2, (float)  topRight.getY(), (float)  botRight.getX()/2,  (float) botRight.getY(), paintPoint);
                            canvas.drawLine((float) botRight.getX()/2, (float)  botRight.getY(), (float)  botLeft.getX()/2,  (float) botLeft.getY(), paintPoint);
                            canvas.drawLine((float) botLeft.getX()/2, (float)  botLeft.getY(), (float)  topLeft.getX()/2,  (float) topLeft.getY(), paintPoint);
                            */

                            //canvas.drawRect(mostLeft, mostTop, mostRight, mostBot, paintPoint);
                            //canvas.drawBitmap(rotatedBitmap, null, rectangle, null);
                        }

                        // Debugging
                        catch (Exception e) {
//                            Log.e("Boofcv", "Width: " + width + " & Height: " + height);
//                            Log.e("Boofcv", "Top Left: (" + topLeft.x + ", " + topLeft.y);
//                            Log.e("Boofcv", "Top Right: (" + topRight.x + ", " + topRight.y);
//                            Log.e("Boofcv", "Bottom Left: (" + botLeft.x + ", " + botLeft.y);
//                            Log.e("Boofcv", "Bottom Right: (" + botRight.x + ", " + botRight.y + "\n\n");
                        }

                }
			}
		}

        private boolean compare(Point2D_F64 p1, Point2D_F64 p2) {
            if(p1.getX() != p2.getX()) return false;
            else if(p1.getY() != p2.getY()) return false;

            else return true;
        }
	}
}