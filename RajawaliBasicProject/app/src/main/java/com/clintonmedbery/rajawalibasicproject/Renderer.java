package com.clintonmedbery.rajawalibasicproject;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.renderscript.BaseObj;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.renderer.RajawaliRenderer;

import java.util.List;

/**
 * Created by clintonmedbery on 4/6/15.
 */
public class Renderer extends RajawaliRenderer {

    public Context context;

    private DirectionalLight directionalLight;
    private Object3D plane;

    public Renderer(Context context) {
        super(context);
        this.context = context;
        setFrameRate(60);
    }



    public void initScene(){

        directionalLight = new DirectionalLight(1f, .2f, -1.0f);
        directionalLight.setColor(1.0f, 1.0f, 1.0f);
        directionalLight.setPower(2);
        getCurrentScene().addLight(directionalLight);

        Material material = new Material();
        material.enableLighting(false);
        material.setDiffuseMethod(new DiffuseMethod.Lambert());
        material.setColor(new float[]{0.0f, 0.55f, 0.0f, 1.0f});

        plane = new Plane(2f,2f,1,1);

        plane.setMaterial(material);
        plane.rotate(Vector3.Axis.Y, 30.0);
        plane.rotate(Vector3.Axis.X, 10.0);

        SurfaceTexture t = new SurfaceTexture();
        Surface s = new Surface();

        getCurrentScene().addChild(plane);
        getCurrentCamera().setZ(4.2f);

    }


    @Override
     public void onRender(final long elapsedTime, final double deltaTime) {
        super.onRender(elapsedTime, deltaTime);
    }


    public void onTouchEvent(MotionEvent event){


    }

    public void onOffsetsChanged(float x, float y, float z, float w, int i, int j){

    }
}

