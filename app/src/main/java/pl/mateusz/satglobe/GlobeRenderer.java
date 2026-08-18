package pl.mateusz.satglobe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * OpenGL ES 2.0 renderer. Satellite markers use Android-reported azimuth and
 * elevation, projected onto a compressed visual shell around Earth.
 */
final class GlobeRenderer implements GLSurfaceView.Renderer {
    private static final int LATITUDE_BANDS = 48;
    private static final int LONGITUDE_BANDS = 96;
    private static final int SPHERE_STRIDE_FLOATS = 8;
    private static final int MARKER_STRIDE_FLOATS = 8;
    private static final int MAX_SATELLITES = 128;
    private static final double SATELLITE_SHELL_RADIUS = 1.70;

    private final Context context;
    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] model = new float[16];
    private final float[] modelView = new float[16];
    private final float[] mvp = new float[16];

    private FloatBuffer sphereVertices;
    private ShortBuffer sphereIndices;
    private int sphereIndexCount;
    private final FloatBuffer lineVertices = allocateFloatBuffer(
            MAX_SATELLITES * 2 * MARKER_STRIDE_FLOATS
    );
    private final FloatBuffer pointVertices = allocateFloatBuffer(
            (MAX_SATELLITES + 1) * MARKER_STRIDE_FLOATS
    );

    private int earthProgram;
    private int markerProgram;
    private int earthTexture;
    private volatile List<SatelliteInfo> satellites = Collections.emptyList();
    private volatile boolean hasReceiverLocation;
    private volatile double receiverLatitude;
    private volatile double receiverLongitude;
    private volatile String selectedSatelliteKey;
    private volatile float yawDegrees = -72f;
    private volatile float pitchDegrees = 42f;
    private volatile float zoom = 1f;
    private volatile boolean userAdjustedView;

    GlobeRenderer(Context context) {
        this.context = context;
        buildSphere();
    }

    void setSatellites(List<SatelliteInfo> newSatellites) {
        satellites = Collections.unmodifiableList(new ArrayList<>(newSatellites));
    }

    void setReceiverLocation(double latitude, double longitude) {
        boolean firstReceiverLocation = !hasReceiverLocation;
        receiverLatitude = latitude;
        receiverLongitude = longitude;
        hasReceiverLocation = true;
        if (firstReceiverLocation && !userAdjustedView) {
            centerOnReceiver();
        }
    }

    void setSelectedSatellite(String key) {
        selectedSatelliteKey = key;
    }

    void rotateBy(float yawDelta, float pitchDelta) {
        userAdjustedView = true;
        yawDegrees = wrapDegrees(yawDegrees + yawDelta);
        pitchDegrees = Math.max(-89f, Math.min(89f, pitchDegrees + pitchDelta));
    }

    void multiplyZoom(float factor) {
        userAdjustedView = true;
        zoom = Math.max(0.72f, Math.min(1.52f, zoom * factor));
    }

    void resetView() {
        userAdjustedView = false;
        if (hasReceiverLocation) {
            centerOnReceiver();
        } else {
            yawDegrees = -72f;
            pitchDegrees = 42f;
        }
        zoom = 1f;
    }

    private void centerOnReceiver() {
        yawDegrees = wrapDegrees((float) receiverLongitude - 90f);
        pitchDegrees = Math.max(
                -82f,
                Math.min(82f, (float) receiverLatitude)
        );
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(0.019f, 0.031f, 0.067f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glFrontFace(GLES20.GL_CW);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        earthProgram = buildProgram(EARTH_VERTEX_SHADER, EARTH_FRAGMENT_SHADER);
        markerProgram = buildProgram(MARKER_VERTEX_SHADER, MARKER_FRAGMENT_SHADER);
        earthTexture = loadEarthTexture();
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float aspect = height == 0 ? 1f : (float) width / (float) height;
        Matrix.perspectiveM(projection, 0, 42f, aspect, 0.1f, 20f);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        float cameraDistance = 3.65f / zoom;
        Matrix.setLookAtM(view, 0, 0f, 0f, cameraDistance, 0f, 0f, 0f, 0f, 1f, 0f);
        Matrix.setIdentityM(model, 0);
        Matrix.rotateM(model, 0, pitchDegrees, 1f, 0f, 0f);
        Matrix.rotateM(model, 0, yawDegrees, 0f, 1f, 0f);
        Matrix.multiplyMM(modelView, 0, view, 0, model, 0);
        Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0);

        drawEarth();
        drawSatelliteGeometry();
    }

    private void drawEarth() {
        GLES20.glUseProgram(earthProgram);

        int mvpHandle = GLES20.glGetUniformLocation(earthProgram, "uMvp");
        int modelHandle = GLES20.glGetUniformLocation(earthProgram, "uModel");
        int textureHandle = GLES20.glGetUniformLocation(earthProgram, "uTexture");
        int positionHandle = GLES20.glGetAttribLocation(earthProgram, "aPosition");
        int normalHandle = GLES20.glGetAttribLocation(earthProgram, "aNormal");
        int texCoordHandle = GLES20.glGetAttribLocation(earthProgram, "aTexCoord");

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0);
        GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, earthTexture);
        GLES20.glUniform1i(textureHandle, 0);

        int strideBytes = SPHERE_STRIDE_FLOATS * Float.BYTES;
        sphereVertices.position(0);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(
                positionHandle, 3, GLES20.GL_FLOAT, false, strideBytes, sphereVertices
        );
        sphereVertices.position(3);
        GLES20.glEnableVertexAttribArray(normalHandle);
        GLES20.glVertexAttribPointer(
                normalHandle, 3, GLES20.GL_FLOAT, false, strideBytes, sphereVertices
        );
        sphereVertices.position(6);
        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(
                texCoordHandle, 2, GLES20.GL_FLOAT, false, strideBytes, sphereVertices
        );

        sphereIndices.position(0);
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                sphereIndexCount,
                GLES20.GL_UNSIGNED_SHORT,
                sphereIndices
        );

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(normalHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
    }

    private void drawSatelliteGeometry() {
        if (!hasReceiverLocation || satellites.isEmpty()) {
            return;
        }

        SatelliteMath.Vec3 receiver = SatelliteMath.earthPoint(
                receiverLatitude,
                receiverLongitude,
                1.025
        );
        lineVertices.clear();
        pointVertices.clear();

        int satelliteCount = 0;
        String selectedKey = selectedSatelliteKey;
        for (SatelliteInfo satellite : satellites) {
            if (satelliteCount >= MAX_SATELLITES) {
                break;
            }
            SatelliteMath.Vec3 point = SatelliteMath.satellitePoint(
                    receiverLatitude,
                    receiverLongitude,
                    satellite.azimuthDegrees,
                    satellite.elevationDegrees,
                    SATELLITE_SHELL_RADIUS
            );
            boolean selected = satellite.key().equals(selectedKey);
            float[] color = selected
                    ? new float[]{1f, 1f, 1f, 1f}
                    : satellite.colorRgba(satellite.usedInFix ? 0.95f : 0.58f);

            putMarkerVertex(lineVertices, receiver, color[0], color[1], color[2], 0.08f, 1f);
            putMarkerVertex(
                    lineVertices,
                    point,
                    color[0],
                    color[1],
                    color[2],
                    satellite.usedInFix ? 0.78f : 0.34f,
                    1f
            );

            float signalSize = 8f + Math.max(0f, Math.min(8f, satellite.cn0DbHz / 7f));
            if (satellite.usedInFix) {
                signalSize += 3f;
            }
            if (selected) {
                signalSize += 5f;
            }
            putMarkerVertex(
                    pointVertices,
                    point,
                    color[0],
                    color[1],
                    color[2],
                    1f,
                    signalSize
            );
            satelliteCount++;
        }

        putMarkerVertex(pointVertices, receiver, 1f, 0.83f, 0.35f, 1f, 15f);
        lineVertices.flip();
        pointVertices.flip();

        GLES20.glUseProgram(markerProgram);
        int mvpHandle = GLES20.glGetUniformLocation(markerProgram, "uMvp");
        int positionHandle = GLES20.glGetAttribLocation(markerProgram, "aPosition");
        int colorHandle = GLES20.glGetAttribLocation(markerProgram, "aColor");
        int sizeHandle = GLES20.glGetAttribLocation(markerProgram, "aPointSize");
        int pointModeHandle = GLES20.glGetUniformLocation(markerProgram, "uPointMode");
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0);

        bindMarkerAttributes(lineVertices, positionHandle, colorHandle, sizeHandle);
        GLES20.glUniform1f(pointModeHandle, 0f);
        GLES20.glLineWidth(1f);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, satelliteCount * 2);

        bindMarkerAttributes(pointVertices, positionHandle, colorHandle, sizeHandle);
        GLES20.glUniform1f(pointModeHandle, 1f);
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, satelliteCount + 1);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(colorHandle);
        GLES20.glDisableVertexAttribArray(sizeHandle);
    }

    private void bindMarkerAttributes(
            FloatBuffer buffer,
            int positionHandle,
            int colorHandle,
            int sizeHandle
    ) {
        int strideBytes = MARKER_STRIDE_FLOATS * Float.BYTES;
        buffer.position(0);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(
                positionHandle, 3, GLES20.GL_FLOAT, false, strideBytes, buffer
        );
        buffer.position(3);
        GLES20.glEnableVertexAttribArray(colorHandle);
        GLES20.glVertexAttribPointer(
                colorHandle, 4, GLES20.GL_FLOAT, false, strideBytes, buffer
        );
        buffer.position(7);
        GLES20.glEnableVertexAttribArray(sizeHandle);
        GLES20.glVertexAttribPointer(
                sizeHandle, 1, GLES20.GL_FLOAT, false, strideBytes, buffer
        );
    }

    private static void putMarkerVertex(
            FloatBuffer buffer,
            SatelliteMath.Vec3 point,
            float red,
            float green,
            float blue,
            float alpha,
            float size
    ) {
        buffer.put((float) point.x);
        buffer.put((float) point.y);
        buffer.put((float) point.z);
        buffer.put(red);
        buffer.put(green);
        buffer.put(blue);
        buffer.put(alpha);
        buffer.put(size);
    }

    private void buildSphere() {
        int vertexCount = (LATITUDE_BANDS + 1) * (LONGITUDE_BANDS + 1);
        sphereVertices = allocateFloatBuffer(vertexCount * SPHERE_STRIDE_FLOATS);

        for (int latitudeIndex = 0; latitudeIndex <= LATITUDE_BANDS; latitudeIndex++) {
            float v = (float) latitudeIndex / LATITUDE_BANDS;
            double latitude = Math.PI / 2.0 - Math.PI * v;
            float y = (float) Math.sin(latitude);
            float horizontal = (float) Math.cos(latitude);

            for (int longitudeIndex = 0; longitudeIndex <= LONGITUDE_BANDS; longitudeIndex++) {
                float u = (float) longitudeIndex / LONGITUDE_BANDS;
                double longitude = -Math.PI + Math.PI * 2.0 * u;
                float x = horizontal * (float) Math.cos(longitude);
                float z = horizontal * (float) Math.sin(longitude);
                sphereVertices.put(x);
                sphereVertices.put(y);
                sphereVertices.put(z);
                sphereVertices.put(x);
                sphereVertices.put(y);
                sphereVertices.put(z);
                sphereVertices.put(u);
                sphereVertices.put(v);
            }
        }
        sphereVertices.flip();

        int indexCount = LATITUDE_BANDS * LONGITUDE_BANDS * 6;
        ByteBuffer indexBytes = ByteBuffer
                .allocateDirect(indexCount * Short.BYTES)
                .order(ByteOrder.nativeOrder());
        sphereIndices = indexBytes.asShortBuffer();
        for (int latitudeIndex = 0; latitudeIndex < LATITUDE_BANDS; latitudeIndex++) {
            for (int longitudeIndex = 0; longitudeIndex < LONGITUDE_BANDS; longitudeIndex++) {
                short first = (short) (
                        latitudeIndex * (LONGITUDE_BANDS + 1) + longitudeIndex
                );
                short second = (short) (first + LONGITUDE_BANDS + 1);
                sphereIndices.put(first);
                sphereIndices.put(second);
                sphereIndices.put((short) (first + 1));
                sphereIndices.put(second);
                sphereIndices.put((short) (second + 1));
                sphereIndices.put((short) (first + 1));
            }
        }
        sphereIndices.flip();
        sphereIndexCount = sphereIndices.remaining();
    }

    private int loadEarthTexture() {
        Bitmap bitmap = BitmapFactory.decodeResource(
                context.getResources(),
                R.drawable.earth_texture
        );
        if (bitmap == null) {
            throw new IllegalStateException("Missing Earth texture");
        }
        int[] textureIds = new int[1];
        GLES20.glGenTextures(1, textureIds, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0]);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
        );
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
        );
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_REPEAT
        );
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
        );
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();
        return textureIds[0];
    }

    private static int buildProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("OpenGL program link failed: " + log);
        }
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("OpenGL shader compile failed: " + log);
        }
        return shader;
    }

    private static FloatBuffer allocateFloatBuffer(int floatCount) {
        return ByteBuffer
                .allocateDirect(floatCount * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360f;
        return wrapped < -180f ? wrapped + 360f : (wrapped > 180f ? wrapped - 360f : wrapped);
    }

    private static final String EARTH_VERTEX_SHADER =
            "uniform mat4 uMvp;\n"
                    + "uniform mat4 uModel;\n"
                    + "attribute vec3 aPosition;\n"
                    + "attribute vec3 aNormal;\n"
                    + "attribute vec2 aTexCoord;\n"
                    + "varying vec3 vNormal;\n"
                    + "varying vec2 vTexCoord;\n"
                    + "void main() {\n"
                    + "  gl_Position = uMvp * vec4(aPosition, 1.0);\n"
                    + "  vNormal = normalize((uModel * vec4(aNormal, 0.0)).xyz);\n"
                    + "  vTexCoord = aTexCoord;\n"
                    + "}\n";

    private static final String EARTH_FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "uniform sampler2D uTexture;\n"
                    + "varying vec3 vNormal;\n"
                    + "varying vec2 vTexCoord;\n"
                    + "void main() {\n"
                    + "  vec3 lightDir = normalize(vec3(-0.35, 0.65, 0.9));\n"
                    + "  float diffuse = max(dot(normalize(vNormal), lightDir), 0.0);\n"
                    + "  vec3 base = texture2D(uTexture, vTexCoord).rgb;\n"
                    + "  float facing = max(vNormal.z, 0.0);\n"
                    + "  float rim = pow(1.0 - facing, 3.0);\n"
                    + "  vec3 lit = base * (0.33 + 0.82 * diffuse);\n"
                    + "  lit += vec3(0.08, 0.38, 0.62) * rim * 0.52;\n"
                    + "  gl_FragColor = vec4(lit, 1.0);\n"
                    + "}\n";

    private static final String MARKER_VERTEX_SHADER =
            "uniform mat4 uMvp;\n"
                    + "attribute vec3 aPosition;\n"
                    + "attribute vec4 aColor;\n"
                    + "attribute float aPointSize;\n"
                    + "varying vec4 vColor;\n"
                    + "void main() {\n"
                    + "  gl_Position = uMvp * vec4(aPosition, 1.0);\n"
                    + "  gl_PointSize = aPointSize;\n"
                    + "  vColor = aColor;\n"
                    + "}\n";

    private static final String MARKER_FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "uniform float uPointMode;\n"
                    + "varying vec4 vColor;\n"
                    + "void main() {\n"
                    + "  if (uPointMode < 0.5) {\n"
                    + "    gl_FragColor = vColor;\n"
                    + "    return;\n"
                    + "  }\n"
                    + "  vec2 centered = gl_PointCoord * 2.0 - 1.0;\n"
                    + "  float radiusSquared = dot(centered, centered);\n"
                    + "  if (radiusSquared > 1.0) discard;\n"
                    + "  float edge = 1.0 - smoothstep(0.50, 1.0, radiusSquared);\n"
                    + "  gl_FragColor = vec4(vColor.rgb, vColor.a * edge);\n"
                    + "}\n";
}
