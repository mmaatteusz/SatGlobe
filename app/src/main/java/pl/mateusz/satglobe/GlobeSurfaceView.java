package pl.mateusz.satglobe;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import java.util.List;

/**
 * Touch-enabled OpenGL surface for the Earth and satellite line-of-sight view.
 */
public final class GlobeSurfaceView extends GLSurfaceView {
    private GlobeRenderer globeRenderer;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private float previousX;
    private float previousY;

    public GlobeSurfaceView(Context context) {
        super(context);
        initialize(context);
    }

    public GlobeSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    private void initialize(Context context) {
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setPreserveEGLContextOnPause(true);

        globeRenderer = new GlobeRenderer(context.getApplicationContext());
        setRenderer(globeRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        scaleDetector = new ScaleGestureDetector(
                context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        globeRenderer.multiplyZoom(detector.getScaleFactor());
                        return true;
                    }
                }
        );
        gestureDetector = new GestureDetector(
                context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent event) {
                        globeRenderer.resetView();
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(MotionEvent event) {
                        performClick();
                        return true;
                    }
                }
        );
    }

    public void setSatellites(List<SatelliteInfo> satellites) {
        globeRenderer.setSatellites(satellites);
    }

    public void setReceiverLocation(double latitude, double longitude) {
        globeRenderer.setReceiverLocation(latitude, longitude);
    }

    public void setSelectedSatellite(String key) {
        globeRenderer.setSelectedSatellite(key);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
            float x = event.getX();
            float y = event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float density = getResources().getDisplayMetrics().density;
                globeRenderer.rotateBy(
                        (x - previousX) / density * 0.22f,
                        (y - previousY) / density * 0.22f
                );
            }
            previousX = x;
            previousY = y;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
