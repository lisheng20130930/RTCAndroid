package com.qp.RTC;

import android.content.Context;
import android.media.AudioManager;

import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.LinkedList;

import utils.Logger;

public class RTCClient implements AVChatManager.RTCClientInterface {
    public EglBase.Context _eglContext = null;
    public String _callee = null;
    private PeerConnectionFactory factory = null;
    private MediaStream localstream = null;
    private boolean bIsFrontCamera = true;
    private RTCClientListener _listener = null;
    private PeerConnection _pc = null;
    private Context _context = null;
    private VideoCapturer _capturer = null;

    public interface RTCClientListener{
        void onLocalStream(MediaStream stream);
        void onRemoteStream(MediaStream stream);
        void onRemoveRemoteStream(MediaStream stream);
        void onHangup();
    }

    @Override
    public void onHangup() {
        _listener.onHangup();
    }

    @Override
    public void onAccepted(String cid, JSONObject jsep) {
        if(jsep != null) {
            SessionDescription sdp = null;
            try {
                String sdpString = jsep.getString("sdp");
                SessionDescription.Type type = SessionDescription.Type.fromCanonicalForm(jsep.getString("type"));
                sdp = new SessionDescription(type, sdpString);
            }catch (Exception e){
                e.printStackTrace();
            }
            _pc.setRemoteDescription(new PeerSdpObserver(), sdp);
        }
        String name = cid+((null==_callee)?"-callee":"-caller");
        AVChatManager.getInstance().record(true,name);
    }


    public RTCClient(RTCClientListener listener){
        _listener = listener;
    }

    public void initializeMediaContext(Context context, boolean audio, boolean video, boolean videoHwAcceleration, EglBase.Context eglContext) {
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);
        _context = context;
        _eglContext = eglContext;
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory();
    }

    public void start(String callee,String jsep){
        AVChatManager.getInstance().setRTCHandler(this);
        if(callee==null){
            incomingcall(jsep);
        }else{
            outgoingcall(callee);
        }
    }

    private final String VIDEO_TRACK_ID = "1929283";
    private final String AUDIO_TRACK_ID = "1928882";
    private final String LOCAL_MEDIA_ID = "1198181";


    private VideoCapturer createCameraCapturer(boolean bIsFront) {
        Camera1Enumerator enumerator = new Camera1Enumerator(false);
        final String[] deviceNames = enumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (bIsFront==enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private VideoTrack getCameraVideoTrack(boolean bIsFront){
        _capturer = createCameraCapturer(bIsFront);
        VideoSource vs = factory.createVideoSource(_capturer);
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", _eglContext);
        _capturer.startCapture(480, 640, 15);
        return factory.createVideoTrack(VIDEO_TRACK_ID, vs);
    }

    private MediaStream createLocalMediaStream(){
        AudioSource as = factory.createAudioSource(new MediaConstraints());
        AudioTrack audioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, as);
        VideoTrack videoTrack = getCameraVideoTrack(bIsFrontCamera);
        MediaStream stream = factory.createLocalMediaStream(LOCAL_MEDIA_ID);
        stream.addTrack(audioTrack);
        stream.addTrack(videoTrack);
        if(_listener!=null) {
            _listener.onLocalStream(stream);
        }
        return stream;
    }

    private void swapLocalVideoTrack(){
        if (_capturer != null) {
            if (_capturer instanceof CameraVideoCapturer) {
                ((CameraVideoCapturer)_capturer).switchCamera(null);
            }
        }
    }

    class PeerConnectionObserver implements PeerConnection.Observer {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState state) {
            switch (state) {
                case NEW:
                    break;
                case GATHERING:
                    break;
                case COMPLETE:
                    sendTrickleCandidate(null);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onIceCandidate(IceCandidate candidate) {
            sendTrickleCandidate(candidate);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream stream) {
            if(null!=_listener) {
                _listener.onRemoteStream(stream);
            }
        }

        @Override
        public void onRemoveStream(MediaStream stream) {
            if(null!=_listener) {
                _listener.onRemoveRemoteStream(stream);
            }
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }
    }

    class PeerSdpObserver implements SdpObserver{
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
        }
        @Override
        public void onSetSuccess() {
        }
        @Override
        public void onCreateFailure(String s) {
        }
        @Override
        public void onSetFailure(String s) {
        }
    }

    private PeerConnection createPeerConnection(){
        LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
        MediaConstraints pc_cons = new MediaConstraints();
        pc_cons.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        return factory.createPeerConnection(iceServers, pc_cons, new PeerConnectionObserver());
    }

    private PeerConnection createMediaPeerConnection(){
        PeerConnection pc = createPeerConnection();
        localstream = createLocalMediaStream();
        pc.addStream(localstream);
        return pc;
    }

    private MediaConstraints defaultConstraints(){
        MediaConstraints pc_cons = new MediaConstraints();
        pc_cons.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        pc_cons.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pc_cons.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        return pc_cons;
    }

    private void sendTrickleCandidate(IceCandidate candidate) {
        if(null!=candidate){
            AVChatManager.getInstance().trickleCandidate(candidate);
        }else{
            AVChatManager.getInstance().trickleCandidateComplete();
        }
    }

    private void outgoingcall(String callee){
        _callee = callee; // remember the callee HERE
        _pc = createMediaPeerConnection();
        _pc.createOffer(new PeerSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                final SessionDescription _sdp = sdp;
                _pc.setLocalDescription(new PeerSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        AVChatManager.getInstance().call2(_callee,_sdp);
                    }
                }, sdp);
            }
        }, defaultConstraints());
    }

    private void incomingcall(String jsepStr){
        SessionDescription sdp1 = null;
        try {
            JSONObject jsep = new JSONObject(jsepStr);
            String sdpString = jsep.getString("sdp");
            SessionDescription.Type type = SessionDescription.Type.fromCanonicalForm(jsep.getString("type"));
            sdp1 = new SessionDescription(type, sdpString);
        }catch (Exception e){
            e.printStackTrace();
        }
        _pc = createMediaPeerConnection();
        _pc.setRemoteDescription(new PeerSdpObserver(), sdp1);
        _pc.createAnswer(new PeerSdpObserver(){
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                _pc.setLocalDescription(new PeerSdpObserver(), sdp);
                AVChatManager.getInstance().accept(sdp);
            }
        }, defaultConstraints());
    }

    public void hangup(){
        AVChatManager.getInstance().hangup(_callee!=null);
    }

    public static void setAudioStreamType(Context ctx, boolean speaker) {
        try {
            AudioManager audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (speaker) {
                audioManager.setSpeakerphoneOn(true);
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM,
                        audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM), AudioManager.FX_KEY_CLICK);
            } else {
                audioManager.setSpeakerphoneOn(false);
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), AudioManager.FX_KEY_CLICK);
            }
        }catch (Exception e){
            Logger.log(e.getMessage());
        }
    }

    public void switchCamera(){
        swapLocalVideoTrack();
    }

    public void abort(){
        if(null != _pc) {
            _pc.close(); // CLOSE PEER
        }
        localstream = null;
        _listener = null;
        factory = null;
        _pc = null;
        AVChatManager.getInstance().setRTCHandler(null);
        AVChatManager.getInstance().abort();
    }
}
