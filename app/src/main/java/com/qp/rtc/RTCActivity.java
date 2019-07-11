package com.qp.rtc;

import android.content.Context;
import android.content.Intent;
import android.opengl.EGLContext;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import org.webrtc.MediaStream;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import utils.Logger;

public class RTCActivity extends AppCompatActivity implements RTCClient.RTCClientListener{
    private static final String KEY_CALLEE = "CALLEE";
    private static final String KEY_JSEP = "JSEP";
    private RTCClient _client = null;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private GLSurfaceView vsv = null;
    private Button hangup = null;

    public static void outgoingcall(Context ctx, String callee){
        Intent intent = new Intent(ctx, RTCActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(KEY_CALLEE, callee);
        ctx.startActivity(intent);
    }

    public static void incomingCall(Context ctx, String jsep) {
        Intent intent = new Intent(ctx, RTCActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(KEY_JSEP, jsep);
        ctx.startActivity(intent);
    }

    @Override
    public void onLocalStream(MediaStream stream) {
        stream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
        VideoRendererGui.update(localRender, 0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, true);
    }

    @Override
    public void onRemoteStream(MediaStream stream) {
        stream.videoTracks.get(0).setEnabled(true);
        stream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
        VideoRendererGui.update(remoteRender, 0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);
        VideoRendererGui.update(localRender, 72, 72, 25, 25, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, true);
    }

    @Override
    public void onRemoveRemoteStream(MediaStream stream) {
        return;
    }

    @Override
    public void onHangup(){
        _client.abort();
        finish();
    }

    private class MyInit implements Runnable {
        public String _callee = null;
        public String _jsep = null;

        public MyInit(String callee,String jsep){
            _callee = callee;
            _jsep = jsep;
            _client = new RTCClient(RTCActivity.this);
        }

        public void run() {
            try {
                EGLContext con = VideoRendererGui.getEGLContext();
                _client.initializeMediaContext(RTCActivity.this, true, true, true, con);
                _client.start(_callee,_jsep);
            } catch (Exception ex) {
                Logger.log("computician.janusclient==>"+ex.getMessage());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_nvchat);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        final String callee = getIntent().getStringExtra(KEY_CALLEE);
        String jsep = getIntent().getStringExtra(KEY_JSEP);

        vsv = (GLSurfaceView)findViewById(R.id.glview);
        vsv.setPreserveEGLContextOnPause(true);
        vsv.setKeepScreenOn(true);
        VideoRendererGui.setView(vsv, new MyInit(callee,jsep));

        remoteRender = VideoRendererGui.create(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FIT, false);
        localRender  = VideoRendererGui.create(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FIT, false);

        hangup = (Button)findViewById(R.id.huangup);
        hangup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _client.hangup();
            }
        });
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
    }
}
