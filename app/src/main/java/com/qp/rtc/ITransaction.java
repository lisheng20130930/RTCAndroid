package com.qp.rtc;

import org.json.JSONObject;

public abstract class ITransaction {
    public String _tid = null;
    public ITransaction(String tid){
        _tid = tid;
    }
    public void onError(){};
    public void onSuccess(JSONObject data)throws Exception{};
}
