package com.qp.RTC;

import android.content.Context;
import android.content.Intent;
import android.opengl.EGLContext;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;


import utils.Logger;

public class RTCActivity extends AppCompatActivity implements RTCClient.RTCClientListener{
    private static final String KEY_CALLEE = "CALLEE";
    private static final String KEY_JSEP = "JSEP";
    private RTCClient _client = null;
    private SurfaceViewRenderer localRender;
    private SurfaceViewRenderer  remoteRender;
    private Display _display = null;
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
    public void onLocalStream(final MediaStream stream) {
        stream.videoTracks.get(0).addSink(localRender);
        localRender.setZOrderOnTop(true);
        localRender.setMirror(true);
        localRender.init(_client._eglContext, null);
    }

    @Override
    public void onRemoteStream(MediaStream stream) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                _client.setAudioStreamType(RTCActivity.this,true);
                stream.videoTracks.get(0).addSink(remoteRender);
                remoteRender.init(_client._eglContext, null);
                RelativeLayout.LayoutParams parmas = (RelativeLayout.LayoutParams)localRender.getLayoutParams();
                DisplayMetrics metrics = new DisplayMetrics();
                _display.getMetrics(metrics);
                parmas.width = metrics.widthPixels/3;
                parmas.height = metrics.heightPixels/4;
                parmas.setMargins(0,30,5,0);
                localRender.setLayoutParams(parmas);
            }
        });
    }

    @Override
    public void onRemoveRemoteStream(MediaStream stream) {
        stream.videoTracks.get(0).removeSink(remoteRender);
    }

    @Override
    public void onHangup(){
        _client.abort();
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_nvchat);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        String callee = getIntent().getStringExtra(KEY_CALLEE);
        String jsep = getIntent().getStringExtra(KEY_JSEP);

        remoteRender = findViewById(R.id.remoteView);
        localRender = findViewById(R.id.localView);
        hangup = (Button)findViewById(R.id.huangup);
        hangup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _client.hangup();
            }
        });

        EglBase.Context eglBaseContext = EglBase.create().getEglBaseContext();
        _display = getWindowManager().getDefaultDisplay();
        _client = new RTCClient(RTCActivity.this);
        _client.initializeMediaContext(getApplicationContext(), true, true, true, eglBaseContext);
        _client.start(callee,jsep);
    }



    @Override
    protected void onDestroy(){
        super.onDestroy();
    }
}
