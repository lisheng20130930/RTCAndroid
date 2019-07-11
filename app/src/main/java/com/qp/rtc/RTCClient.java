package com.qp.rtc;

import android.content.Context;
import android.opengl.EGLContext;
import android.util.Log;

import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.LinkedList;

public class RTCClient implements AVChatManager.RTCClientInterface {
    private PeerConnectionFactory factory = null;

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

    private RTCClientListener _listener = null;
    private String _callee = null;
    private PeerConnection _pc = null;


    public RTCClient(RTCClientListener listener){
        _listener = listener;
    }

    public void initializeMediaContext(Context context, boolean audio, boolean video, boolean videoHwAcceleration, EGLContext eglContext) {
        PeerConnectionFactory.initializeAndroidGlobals(context, audio, video, videoHwAcceleration, eglContext);
        factory = new PeerConnectionFactory();
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

    private MediaStream createLocalMediaStream(){
        AudioSource as = factory.createAudioSource(new MediaConstraints());
        AudioTrack audioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, as);

        VideoCapturer capturer = VideoCapturerAndroid.create(VideoCapturerAndroid.getNameOfFrontFacingDevice());
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth",Integer.toString(960)));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight",Integer.toString(540)));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("maxFrameRate",Integer.toString(15)));
        VideoSource vs = factory.createVideoSource(capturer, constraints);
        VideoTrack videoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, vs);

        MediaStream stream = factory.createLocalMediaStream(LOCAL_MEDIA_ID);
        stream.addTrack(audioTrack);
        stream.addTrack(videoTrack);
        if(_listener!=null) {
            _listener.onLocalStream(stream);
        }
        return stream;
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
        public void onAddStream(MediaStream stream) {
            _listener.onRemoteStream(stream);
        }

        @Override
        public void onRemoveStream(MediaStream stream) {
            _listener.onRemoveRemoteStream(stream);
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
        }

        @Override
        public void onRenegotiationNeeded() {

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
        pc.addStream(createLocalMediaStream());
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

    public void abort(){
        AVChatManager.getInstance().setRTCHandler(null);
        AVChatManager.getInstance().abort();
    }
}
