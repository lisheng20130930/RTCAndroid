package com.qp.RTC;

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
        localRender.setMirror(true);
        localRender.init(_client._eglContext, null);
    }

    @Override
    public void onRemoteStream(MediaStream stream) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stream.videoTracks.get(0).addSink(remoteRender);
                remoteRender.init(_client._eglContext, null);
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
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_nvchat);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        String _callee = getIntent().getStringExtra(KEY_CALLEE);
        String _jsep = getIntent().getStringExtra(KEY_JSEP);

        remoteRender = findViewById(R.id.remoteView);
        localRender = findViewById(R.id.localView);
        hangup = (Button)findViewById(R.id.huangup);
        hangup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _client.hangup();
            }
        });

        //RTCClient.setAudioStreamType(this,true);
        EglBase.Context eglBaseContext = EglBase.create().getEglBaseContext();
        _client = new RTCClient(RTCActivity.this);
        _client.initializeMediaContext(getApplicationContext(), true, true, true, eglBaseContext);
        _client.start(_callee,_jsep);
    }

    private VideoCapturer createCameraCapturer() {
        Camera1Enumerator enumerator = new Camera1Enumerator(false);
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
    }
}
