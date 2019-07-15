package com.qp.RTC;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.extensions.IExtension;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.IProtocol;
import org.java_websocket.protocols.Protocol;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import utils.Logger;

public class WebSocketChannel extends WebSocketClient {
    //private final static String URL = "ws://192.168.18.213:8188";
    private final static String URL = "http://47.110.157.52:8188";
    private final RandomString stringGenerator = new RandomString();
    private ConcurrentHashMap<BigInteger, PlugHandle> attachedPlugins = new ConcurrentHashMap();
    private ConcurrentHashMap<String, ITransaction> transactions = new ConcurrentHashMap();
    private volatile Thread keep_alive = null;
    boolean connected = false;
    private Delegate _delegate = null;
    private BigInteger sessionId = null;
    private String _name = null;

    public WebSocketChannel(URI serverUri, Draft protocolDraft) {
        super(serverUri, protocolDraft);
    }

    public static WebSocketChannel initWithUsrName(String name){
        WebSocketChannel websocket = null;
        try {
            ArrayList<IProtocol> protocols = new ArrayList<IProtocol>();
            protocols.add(new Protocol("janus-protocol"));
            Draft_6455 proto_janus = new Draft_6455(Collections.<IExtension>emptyList(), protocols);
            websocket = new WebSocketChannel(new URI(URL),proto_janus);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        websocket.open(name);
        return websocket;
    }

    private class RandomString {
        final String str = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final Random rnd = new Random();

        public String randomString(Integer length) {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append(str.charAt(rnd.nextInt(str.length())));
            }
            return sb.toString();
        }
    }

    private void startKeepAliveTimer(){
        connected = true;
        keep_alive = new Thread(new Runnable() {
            @Override
            public void run() {
                Thread thisThread = Thread.currentThread();
                while (keep_alive == thisThread) {
                    try {
                        thisThread.sleep(25000);
                    } catch (InterruptedException ex) {
                    }
                    if (!connected) {
                        return;
                    }
                    JSONObject obj = new JSONObject();
                    try {
                        obj.put("janus", "keepalive");
                        obj.put("session_id", sessionId);
                        obj.put("transaction", stringGenerator.randomString(12));
                        sendmessage(obj.toString());
                    } catch (JSONException e) {
                        connected = false;
                        return;
                    }
                }
            }
        }, "KeepAlive");
        keep_alive.start();
    }

    private void stopKeepAliveTimer(){
        keep_alive = null;
        connected = false;
    }

    private void createSession(){
        String tid = stringGenerator.randomString(12);
        transactions.put(tid, new ITransaction(tid) {
            @Override
            public void onSuccess(JSONObject msg) throws Exception{
                JSONObject data = msg.getJSONObject("data");
                sessionId = new BigInteger(data.getString("id"));
                startKeepAliveTimer();
                createHandle();
            }
        });

        try{
            JSONObject obj = new JSONObject();
            obj.put("janus", "create");
            obj.put("transaction", tid);
            sendmessage(obj.toString());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void createHandle(){
        String tid = stringGenerator.randomString(12);
        transactions.put(tid, new ITransaction(tid) {
            @Override
            public void onSuccess(JSONObject msg) throws Exception{
                JSONObject data = msg.getJSONObject("data");
                BigInteger handleId = new BigInteger(data.getString("id"));
                PlugHandle handle = new PlugHandle(handleId);
                attachedPlugins.put(handleId,handle);
                registerUsrname(handleId);
            }
        });

        try{
            JSONObject obj = new JSONObject();
            obj.put("janus", "attach");
            obj.put("transaction", tid);
            obj.put("plugin", "janus.plugin.videocall");
            obj.put("session_id",sessionId);
            sendmessage(obj.toString());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void registerUsrname(BigInteger handleId){
        String tid = stringGenerator.randomString(12);
        try{
            JSONObject obj = new JSONObject();
            JSONObject msg = new JSONObject();
            msg.put("request", "register");
            msg.put("username", _name);
            msg.put("device", "Android");
            obj.put("janus","message");
            obj.put("transaction", tid);
            obj.put("session_id",sessionId);
            obj.put("handle_id",handleId);
            obj.put("body", msg);
            sendmessage(obj.toString());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        Logger.log("[wesocket channel] onOpen....");
        createSession();
    }

    @Override
    public void onMessage(String message) {
        Logger.log("[wesocket channel] onMessage====>"+message);
        try{
            JSONObject msg = new JSONObject(message);
            String janus = msg.getString("janus");
            if(janus.equals("success")){
                String tid = msg.getString("transaction");
                ITransaction transaction = transactions.get(tid);
                if(transaction!=null){
                    transactions.remove(tid);
                    transaction.onSuccess(msg);
                }
            }else if(janus.equals("error")){
                String tid = msg.getString("transaction");
                ITransaction transaction = transactions.get(tid);
                if(transaction!=null){
                    transactions.remove(tid);
                    transaction.onError();
                }
            }else if(janus.equals("ack")){
            }else{
                if(msg.has("sender")) {
                    BigInteger sender = new BigInteger(msg.getString("sender"));
                    PlugHandle handle = attachedPlugins.get(sender);
                    if (handle != null) {
                        if (janus.equals("event")) {
                            JSONObject data = msg.getJSONObject("plugindata").getJSONObject("data");
                            JSONObject jsep = null;
                            if(msg.has("jsep")){
                                jsep = msg.getJSONObject("jsep");
                            }
                            _delegate.onMessage(sender, data, jsep);
                        } else if (janus.equals("detached")) {
                            _delegate.onLeaving(sender);
                        }
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Logger.log("[wesocket channel] onClose....");
    }

    @Override
    public void onError(Exception ex) {
        Logger.log("[wesocket channel] onError....");
    }

    public interface Delegate{
        void onMessage(BigInteger handleId, JSONObject msg, JSONObject jsep);
        void onLeaving(BigInteger handleId);
    }

    private void open(String name){
        _name = name;
        connect();
    }

    public void disconnect(){
        stopKeepAliveTimer();
        super.close();
    }

    public void sendmessage(String message){
        Logger.log("SEND===>"+message);
        send(message);
    }

    public void setDelegate(Delegate delegate){
        _delegate =delegate;
    }

    public void call2(BigInteger handleId, String callee, SessionDescription sdp){
        String tid = stringGenerator.randomString(12);
        try {
            JSONObject obj = new JSONObject();
            JSONObject msg = new JSONObject();
            JSONObject jsep = new JSONObject();
            msg.put("request", "call");
            msg.put("username", callee);
            jsep.put("type",sdp.type.canonicalForm());
            jsep.put("sdp", sdp.description);
            obj.put("janus","message");
            obj.put("transaction", tid);
            obj.put("session_id",sessionId);
            obj.put("handle_id",handleId);
            obj.put("body",msg);
            obj.put("jsep",jsep);
            sendmessage(obj.toString());
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
    }

    public void accept(BigInteger handleId, SessionDescription sdp){
        String tid = stringGenerator.randomString(12);
        try {
            JSONObject obj = new JSONObject();
            JSONObject msg = new JSONObject();
            JSONObject jsep = new JSONObject();
            msg.put("request", "accept");
            jsep.put("type",sdp.type.canonicalForm());
            jsep.put("sdp", sdp.description);
            obj.put("janus","message");
            obj.put("transaction", tid);
            obj.put("session_id",sessionId);
            obj.put("handle_id",handleId);
            obj.put("body",msg);
            obj.put("jsep",jsep);
            sendmessage(obj.toString());
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
    }

    public void record(BigInteger handleId, boolean record, String name){
        String tid = stringGenerator.randomString(12);
        try {
            JSONObject obj = new JSONObject();
            JSONObject msg = new JSONObject();
            msg.put("request", "set");
            msg.put("audio", true);
            msg.put("video", true);
            msg.put("bitrate", 128000);
            msg.put("record", record);
            msg.put("filename", name);
            obj.put("janus","message");
            obj.put("transaction", tid);
            obj.put("session_id",sessionId);
            obj.put("handle_id",handleId);
            obj.put("body",msg);
            sendmessage(obj.toString());
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
    }

    public void hangup(BigInteger handleId,boolean mix){
        record(handleId,false,""); //record close
        String tid = stringGenerator.randomString(12);
        try {
            JSONObject obj = new JSONObject();
            JSONObject msg = new JSONObject();
            msg.put("request", "hangup");
            msg.put("mix", mix);
            obj.put("janus","message");
            obj.put("transaction", tid);
            obj.put("session_id",sessionId);
            obj.put("handle_id",handleId);
            obj.put("body",msg);
            sendmessage(obj.toString());
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
    }

    public void trickleCandidate(BigInteger handleId, IceCandidate candidate){
        String tid = stringGenerator.randomString(12);
        try {
            JSONObject obj = new JSONObject();
            JSONObject msg = new JSONObject();
            if (candidate == null)
                msg.put("completed", true);
            else {
                msg.put("candidate", candidate.sdp);
                msg.put("sdpMid", candidate.sdpMid);
                msg.put("sdpMLineIndex", candidate.sdpMLineIndex);
            }
            obj.put("janus","trickle");
            obj.put("transaction", tid);
            obj.put("session_id",sessionId);
            obj.put("handle_id",handleId);
            obj.put("candidate", msg);
            sendmessage(obj.toString());
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
    }

    public void trickleCandidateComplete(BigInteger handleId){
        trickleCandidate(handleId,null);
    }
}
