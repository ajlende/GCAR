package org.boofcv.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import android.content.pm.ActivityInfo;

import boofcv.android.BoofAndroidFiles;

public class DemoMain extends Activity {

	// contains information on all the cameras.  less error prone and easier to deal with
	public static List<CameraSpecs> specs = new ArrayList<CameraSpecs>();
	// specifies which camera to use an image size
	public static DemoPreference preference;
	// If another activity modifies the demo preferences this needs to be set to true so that it knows to reload
	// camera parameters.
	public static boolean changedPreferences = false;

	public DemoMain() {
		loadCameraSpecs();
	}

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        // When working with the camera, it's useful to stick to one orientation.
        setRequestedOrientation( ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE );

        // Next, we disable the application's title bar...
        requestWindowFeature( Window.FEATURE_NO_TITLE );
        // ...and the notification bar. That way, we can use the full screen.
        getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN );

        Intent intent = new Intent(this, CalibrationActivity.class);
        startActivity(intent);

	}

	@Override
	protected void onResume() {
		super.onResume();
		if( preference == null ) {
			preference = new DemoPreference();
			setDefaultPreferences();
		} else if( changedPreferences ) {
			loadIntrinsic();
		}
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	private void loadCameraSpecs() {
		int numberOfCameras = Camera.getNumberOfCameras();
		for (int i = 0; i < numberOfCameras; i++) {
			CameraSpecs c = new CameraSpecs();
			specs.add(c);

			Camera.getCameraInfo(i, c.info);
			Camera camera = Camera.open(i);
			Camera.Parameters params = camera.getParameters();

            /*for(int j = 0; j < params.getSupportedPreviewSizes().size(); j++) {
                if(params.getSupportedPreviewSizes().get(j).width == 486) c.sizePreview.add(params.getSupportedPreviewSizes().get(j));
            }

            for(int j = 0; j < params.getSupportedPictureSizes().size(); j++) {
                if(params.getSupportedPictureSizes().get(j).width == 486) c.sizePicture.add(params.getSupportedPictureSizes().get(j));
            }*/
			c.sizePreview.addAll(params.getSupportedPreviewSizes());
			c.sizePicture.addAll(params.getSupportedPictureSizes());
            //c.sizePreview.add(params.getSupportedPreviewSizes().get(1));
            //c.sizePicture.add(params.getSupportedPictureSizes().get(1));
			camera.release();
		}
	}

	private void setDefaultPreferences() {
		preference.showFps = false;

		// There are no cameras.  This is possible due to the hardware camera setting being set to false
		// which was a work around a bad design decision where front facing cameras wouldn't be accepted as hardware
		// which is an issue on tablets with only front facing cameras
		if( specs.size() == 0 ) {
			dialogNoCamera();
		}
		// select a front facing camera as the default
		for (int i = 0; i < specs.size(); i++) {
		    CameraSpecs c = specs.get(i);

			if( c.info.facing == Camera.CameraInfo.CAMERA_FACING_BACK ) {
				preference.cameraId = i;
				break;
			} else {
				// default to a front facing camera if a back facing one can't be found
				preference.cameraId = i;
			}
		}

		CameraSpecs camera = specs.get(preference.cameraId);
		//preference.preview = UtilVarious.closest(camera.sizePreview,320,240);
        preference.preview = UtilVarious.closest(camera.sizePreview,640,480);
        preference.picture = UtilVarious.closest(camera.sizePicture,640,480);

		// see if there are any intrinsic parameters to load
		loadIntrinsic();
	}

	private void dialogNoCamera() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Your device has no cameras!")
				.setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						System.exit(0);
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void loadIntrinsic() {
		preference.intrinsic = null;
		try {
			FileInputStream fos = openFileInput("cam"+preference.cameraId+".txt");
			Reader reader = new InputStreamReader(fos);
			preference.intrinsic = BoofAndroidFiles.readIntrinsic(reader);
		} catch (FileNotFoundException e) {

		} catch (IOException e) {
			Toast.makeText(this, "Failed to load intrinsic parameters", Toast.LENGTH_SHORT).show();
		}
	}

	public void  onContentChanged  () {
		System.out.println("onContentChanged");
		super.onContentChanged();
	}
}
