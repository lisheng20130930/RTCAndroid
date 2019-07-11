package com.qp;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.qp.RTC.AVChatManager;
import com.qp.RTC.R;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button caller = (Button)findViewById(R.id.caller);
        caller.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
               AVChatManager.getInstance().login("lisheng", new AVChatManager.AVLoginListener() {
                   @Override
                   public void onLoginResult(boolean success) {
                       AVChatManager.getInstance().outgoingcall(MainActivity.this,"zhangsan");
                   }
               });
            }
        });
        final Button calee = (Button)findViewById(R.id.callee);
        calee.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AVChatManager.getInstance().login("zhangsan", new AVChatManager.AVLoginListener() {
                    @Override
                    public void onLoginResult(boolean success) {
                        calee.setText("等待中");
                        AVChatManager.getInstance().setIncomingObserver(new AVChatManager.IncomingCallObserver() {
                            @Override
                            public void onIncomingCall(String caller, String jsep) {
                                calee.setText("等待");
                                AVChatManager.getInstance().incomingcall(MainActivity.this, jsep);
                            }
                        });
                    }
                });
            }
        });
    }
}
