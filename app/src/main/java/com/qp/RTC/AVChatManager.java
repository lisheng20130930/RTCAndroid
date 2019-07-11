package com.qp.RTC;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.math.BigInteger;

import utils.Logger;

public class AVChatManager implements WebSocketChannel.Delegate{
    private static class InstanceHolder {
        public final static AVChatManager instance = new AVChatManager();
    }

    public interface AVLoginListener {
        void onLoginResult(boolean success);
    }

    public interface IncomingCallObserver{
        void onIncomingCall(String caller, String jsep);
    }

    public interface RTCClientInterface{
        void onHangup();
        void onAccepted(String cid, JSONObject jsep);
    }

    private RTCClientInterface _handler = null;
    private WebSocketChannel _websocket = null;
    private BigInteger _handleId = null;
    private IncomingCallObserver _observer = null;
    private AVLoginListener _listener = null;

    private AVChatManager(){
    }

    public static AVChatManager getInstance(){
        return InstanceHolder.instance;
    }

    public void setRTCHandler(RTCClientInterface handler){
        _handler = handler;
    }

    public void setIncomingObserver(IncomingCallObserver observer){
        _observer = observer;
    }

    private void onResultEvent(BigInteger handleId, JSONObject result,JSONObject jsep) throws JSONException {
        String event = result.getString("event");
        if (event.equals("registered")) {
            _handleId = handleId;
            Logger.log("VideoCAll===>registered success!!!!!!!!");
            if (_listener != null) {
                _listener.onLoginResult(true);
                _listener = null;
            }
        } else if (event.equals("incomingcall")) {
            Logger.log("VideoCAll===>incomingcall !!!!!!!!");
            String username = result.getString("username");
            if (_observer != null) {
                _observer.onIncomingCall(username, jsep.toString());
                _observer = null;
            }
        } else if (event.equals("accepted")){
            Logger.log("VideoCAll===>accepted !!!!!!!!");
            if(result.has("username")){
                String username = result.getString("username");
                Logger.log("VideoCAll===>"+ username + "accepted the call");
            }else{
                Logger.log("VideoCAll===>Call started!");
            }
            String cid = result.getString("cid");
            if(_handler!=null){
                _handler.onAccepted(cid,jsep);
            }
        } else if(event.equals("hangup")){
            Logger.log("VideoCAll===>hangup !!!!!!!!");
            if(_handler!=null){
                _handler.onHangup();
            }
        }
    }

    @Override
    public void onMessage(BigInteger handleId, JSONObject msg, JSONObject jsep) {
        try {
            if (msg.has("result")){
                JSONObject result = msg.getJSONObject("result");
                if (result.has("event")) {
                    onResultEvent(handleId,result,jsep);
                }
            }else{
                Logger.log("VideoCAll===>MESSAGE==>"+msg.toString());
                if(_listener!=null) {
                    _listener.onLoginResult(false);
                    _listener = null;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @Override
    public void onLeaving(BigInteger handleId){
        Logger.log("=====> handle ID Leaveing : "+handleId);
    }

    public void login(String name, AVLoginListener listener){
        _listener = listener;
        if(_websocket!=null){
            _websocket.disconnect();
        }
        _websocket = WebSocketChannel.initWithAccount(name);
        _websocket.setDelegate(this);
    }

    public void trickleCandidate(IceCandidate candidate){
        _websocket.trickleCandidate(_handleId,candidate);
    }

    public void trickleCandidateComplete(){
        _websocket.trickleCandidateComplete(_handleId);
    }

    public void outgoingcall(Context ctx, String callee){
        RTCActivity.outgoingcall(ctx, callee);
    }

    public void incomingcall(Context ctx, String jsep){
        RTCActivity.incomingCall(ctx,jsep);
    }

    public void hangup(boolean mix){
        _websocket.hangup(_handleId,mix);
    }

    public void call2(String callee, SessionDescription sdp){
        _websocket.call2(_handleId,callee,sdp);
    }

    public void record(boolean record, String filename){
        _websocket.record(_handleId,record,filename);
    }

    public void accept(SessionDescription sdp){
        _websocket.accept(_handleId,sdp);
    }

    public void abort(){
        _websocket.setDelegate(null);
        _listener = null;
        _handleId = null;
        _observer = null;
        _websocket.disconnect();
        _websocket = null;
    }
}
