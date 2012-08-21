package android.gui;

import java.util.*;
import java.io.*;
import java.nio.*;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.os.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

public class Renderer implements GLSurfaceView.Renderer {

    private NetMash netmash;
    private Mesh mesh;
    private int program;
    private int[] textureIDs;

    private float[] matrixPrj = new float[16];
    private float[] matrixRtx = new float[16];
    private float[] matrixRty = new float[16];
    private float[] matrixRtz = new float[16];
    private float[] matrixRxy = new float[16];
    private float[] matrixRot = new float[16];
    private float[] matrixScl = new float[16];
    private float[] matrixRos = new float[16];
    private float[] matrixTrn = new float[16];
    private float[] matrixMSR = new float[16];
    private float[] matrixVVV = new float[16];
    private float[] matrixMVV = new float[16];
    private float[] matrixMVP = new float[16];
    private float[] matrixNor = new float[16];

    private float[] lightPos = { 25.642736f, 0.0f, -18.505379f, 1.0f };
    private float[] lightCol = { 0.9f, 0.9f, 0.5f, 1.0f };
    private float[] ambient  = { 0.4f, 0.9f, 0.4f, 1.0f };
    private float[] diffuse  = { 0.4f, 0.4f, 0.9f, 1.0f };
    private float[] specular = { 0.9f, 0.4f, 0.4f, 1.0f };
    private float   shininess = 5.0f;

    private float eyeX;
    private float eyeY;
    private float eyeZ;
    private float seeX;
    private float seeY;
    private float seeZ;

    private float direction=0;

    public Renderer(NetMash netmash, LinkedHashMap hm) {
        this.netmash=netmash;
        this.mesh=new Mesh(hm);
        resetCoordsAndView();
    }

    public void newMesh(LinkedHashMap hm){
        this.mesh=new Mesh(hm);
    }

    public void resetCoordsAndView(){
        eyeX=0;
        eyeY=1.5f;
        eyeZ=0;
        seeX=0;
        seeY=1.5f;
        seeZ=5.0f;
    }

    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        doBasicSetup();
        setupTextures();
        getProgram();
    }

    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float r = ((float)width)/height;
        float n = 1.0f;
        Matrix.frustumM(matrixPrj, 0, -r*n, r*n, -n, n, 0.5f, 100.0f);
    }

    public void onDrawFrame(GL10 gl){

        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(program);
        throwAnyGLException("glUseProgram");

        drawMeshAndSubs(mesh, 0,0,0);
    }

    private void drawMeshAndSubs(Mesh m, float tx, float ty, float tz){

        drawMesh(m, tx,ty,tz);

        for(Object o: m.subObjects){ try{
            LinkedHashMap subob=(LinkedHashMap)o;
            Object subobuid=subob.get("object");
            Object subobcrd=subob.get("coords");
            LinkedHashMap sm=(LinkedHashMap)netmash.user.glElements.get(subobuid);
            if(sm==null) continue;
            drawMeshAndSubs(new Mesh(sm), tx+Mesh.getFloatFromList(subobcrd,0,0), ty+Mesh.getFloatFromList(subobcrd,1,0), tz+Mesh.getFloatFromList(subobcrd,2,0));

        }catch(Throwable t){} }
    }

    private void drawMesh(Mesh m, float tx, float ty, float tz){try{

        Matrix.setIdentityM(matrixRtx, 0);
        Matrix.setIdentityM(matrixRty, 0);
        Matrix.setIdentityM(matrixRtz, 0);
        Matrix.setIdentityM(matrixScl, 0);
        Matrix.setIdentityM(matrixTrn, 0);

        Matrix.setRotateM(  matrixRtx, 0, m.rotationX, -1.0f, 0.0f, 0.0f);
        Matrix.setRotateM(  matrixRty, 0, m.rotationY,  0.0f, 1.0f, 0.0f);
        Matrix.setRotateM(  matrixRtz, 0, m.rotationZ,  0.0f, 0.0f, 1.0f);
        Matrix.scaleM(      matrixScl, 0, m.scaleX, m.scaleY, m.scaleZ);
        Matrix.translateM(  matrixTrn, 0, tx, ty, tz);

        Matrix.multiplyMM(  matrixRxy, 0, matrixRty, 0, matrixRtx, 0);
        Matrix.multiplyMM(  matrixRot, 0, matrixRtz, 0, matrixRxy, 0);
        Matrix.multiplyMM(  matrixRos, 0, matrixRot, 0, matrixScl, 0);
        Matrix.multiplyMM(  matrixMSR, 0, matrixTrn, 0, matrixRos, 0);

        Matrix.setLookAtM(  matrixVVV, 0, eyeX,eyeY,eyeZ, seeX,seeY,seeZ, 0f,1f,0f);

        Matrix.multiplyMM(  matrixMVV, 0, matrixVVV, 0, matrixMSR, 0);
        Matrix.multiplyMM(  matrixMVP, 0, matrixPrj, 0, matrixMVV, 0);
        Matrix.invertM(     matrixNor, 0, matrixMVP, 0);
        Matrix.transposeM(  matrixNor, 0, matrixNor, 0);

        // glGetUniformLocation, glGetAttribLocation - do these once on program creation
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "mvpm"), 1, false, matrixMVP, 0);
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "norm"), 1, false, matrixNor, 0);

        GLES20.glUniform4fv(      GLES20.glGetUniformLocation(program, "lightPos"),  1, lightPos, 0);
        GLES20.glUniform4fv(      GLES20.glGetUniformLocation(program, "lightCol"),  1, lightCol, 0);

        GLES20.glUniform4fv(      GLES20.glGetUniformLocation(program, "ambient"),   1, ambient, 0);
        GLES20.glUniform4fv(      GLES20.glGetUniformLocation(program, "diffuse"),   1, diffuse, 0);
        GLES20.glUniform4fv(      GLES20.glGetUniformLocation(program, "specular"),  1, specular, 0);
        GLES20.glUniform1f(       GLES20.glGetUniformLocation(program, "shininess"),    shininess);

        throwAnyGLException("uniforms");

        FloatBuffer vb = m.vb;
        ShortBuffer ib = m.ib;
        int indslength = m.il;

        // use JNI and glBufferData: http://stackoverflow.com/questions/5402567/whats-glbufferdata-for-in-opengl-es
        //                           http://code.google.com/p/gdc2011-android-opengl/wiki/TalkTranscript
        // or glBindBuffer:          http://www.learnopengles.com/android-lesson-seven-an-introduction-to-vertex-buffer-objects-vbos/
        //                           http://www.androidenea.com/2012/02/opengl-es-20-on-android.html
        int ph=GLES20.glGetAttribLocation(program, "pos");
        GLES20.glEnableVertexAttribArray(ph);
        vb.position(0);
        GLES20.glVertexAttribPointer(ph, 3, GLES20.GL_FLOAT, false, 32, vb);

        int nh=GLES20.glGetAttribLocation(program, "nor");
        GLES20.glEnableVertexAttribArray(nh);
        vb.position(3);
        GLES20.glVertexAttribPointer(nh, 3, GLES20.GL_FLOAT, false, 32, vb);

        int th=GLES20.glGetAttribLocation(program, "tex");
        GLES20.glEnableVertexAttribArray(th);
        vb.position(6);
        GLES20.glVertexAttribPointer(th, 2, GLES20.GL_FLOAT, false, 32, vb);

        throwAnyGLException("VBOs");

        for(int i=0; i < m.textures.size(); i++) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIDs[i]);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "texture"+i), i);
        }

        throwAnyGLException("textures");

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indslength, GLES20.GL_UNSIGNED_SHORT, ib);
        throwAnyGLException("glDrawElements");

        GLES20.glDisableVertexAttribArray(ph);
        GLES20.glDisableVertexAttribArray(nh);
        GLES20.glDisableVertexAttribArray(th);

        throwAnyGLException("Draw frame end");

    }catch(Throwable t){ Log.e("Draw frame:", t.getLocalizedMessage()); }}

    // -------------------------------------------------------------

    public void stroke(float dx, float dy){
        direction -= dx/50f;
        if(direction> 2*Math.PI) direction-=2*Math.PI;
        if(direction<-2*Math.PI) direction+=2*Math.PI;
        seeX=eyeX+7f*(float)Math.sin(direction);
        seeZ=eyeZ+7f*(float)Math.cos(direction);
        eyeX-=dy/7f*(float)Math.sin(direction);
        eyeZ-=dy/7f*(float)Math.cos(direction);
        this.netmash.user.onNewCoords(eyeX, eyeY, eyeZ);
    }

    // -------------------------------------------------------------

    public void doBasicSetup(){
        GLES20.glClearColor(0.6f, 0.8f, 0.9f, 1.0f);
        GLES20.glClearDepthf(1.0f);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glCullFace(GLES20.GL_BACK);
    }

    // use ETC compression
    // figure out how to rebind
    private void setupTextures(){
        int numtextures = mesh.textures.size();
        textureIDs = new int[numtextures];
        GLES20.glGenTextures(numtextures, textureIDs, 0);
        for(int i=0; i< numtextures; i++) {
            Bitmap bm=netmash.getBitmap(mesh.textures.get(i).toString());
            GLES20.glBindTexture(  GLES20.GL_TEXTURE_2D, textureIDs[i]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,     GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,     GLES20.GL_REPEAT);
            GLUtils.texImage2D(    GLES20.GL_TEXTURE_2D, 0, bm, 0);
        }
    }

    private void getProgram(){

        // detect using same shader and use cached gl handle
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, (String)netmash.user.glElements.get(mesh.vertexShader));
        if(vertexShader==0){ Log.e("getProgram", "Could not compile vertexShader"); return; }

        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, (String)netmash.user.glElements.get(mesh.fragmentShader));
        if(fragmentShader==0){ Log.e("getProgram", "Could not compile fragmentShader"); return; }

        program = GLES20.glCreateProgram();
        if(program==0){ Log.e("getProgram", "Could not create program"); return; }

        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if(linkStatus[0]==GLES20.GL_TRUE) return;
        Log.e("getProgram", "Could not link program:");
        Log.e("getProgram", GLES20.glGetProgramInfoLog(program));
        GLES20.glDeleteProgram(program);
        program=0;
    }

    private int compileShader(int shaderType, String source){

        int shader = GLES20.glCreateShader(shaderType);
        if(shader==0) return 0;

        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if(compiled[0]!=0) return shader;
        Log.e("compileShader", "Could not compile "+shaderType+" shader:");
        Log.e("compileShader", GLES20.glGetShaderInfoLog(shader));
        GLES20.glDeleteShader(shader);
        return 0;
    }

    // -------------------------------------------------------------

    private void throwAnyGLException(String fn) {
        int e; while((e=GLES20.glGetError())!=GLES20.GL_NO_ERROR){ throw new RuntimeException(fn+": glError "+e); }
    }
}

