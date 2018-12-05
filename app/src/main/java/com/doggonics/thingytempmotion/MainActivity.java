package com.doggonics.thingytempmotion;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.IBinder;
import android.os.TestLooperManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;



import android.widget.TextView;
import android.widget.Toast;


import java.io.UnsupportedEncodingException;
import java.sql.Time;
import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;

public class MainActivity extends AppCompatActivity  {

    private static final int REQUEST_SELECT_DEVICE=1;
    private static final int REQUEST_ENABLE_BT=2;
    private static final int THINGY_PROFILE_READY =10;
    private static final String TAG="MainActivity";
    private static final int THINGY_PROFILE_CONNECTED =20;
    private static final int THINGY_PROFILE_DISCONNECTED =21;
    private static final int STATE_OFF=10;

    private int mState= THINGY_PROFILE_DISCONNECTED;
    private ThingyService mService=null;
    private BluetoothDevice mBtDevice=null;
    private BluetoothAdapter mBtAdapter=null;
    private TextView tvTemperature;
    private TextView tvsampleTime;
    private Button btnConnectDisconnect;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBtAdapter=BluetoothAdapter.getDefaultAdapter();
        if(mBtAdapter==null){
            Toast.makeText(this,"Bluetooth is not available",Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        tvTemperature= findViewById(R.id.temperature);
        btnConnectDisconnect=findViewById(R.id.btn_select);


        service_init();

        //handle Disconnect amd Connect button
        btnConnectDisconnect.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                if(!mBtAdapter.isEnabled()) {
                    Log.i(TAG, "onClick-BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }
                else{//Connect button pressed,open DeviceList Activity class with popup window
                    //that scan for devices
                    if(btnConnectDisconnect.getText().equals("Connect")){
                        Log.i(TAG,"Connect Device");
                        Intent newIntent= new Intent(MainActivity.this,DeviceListActivity.class);
                        startActivityForResult(newIntent,REQUEST_SELECT_DEVICE);
                    }
                    else {//Disconnet button pressed
                        if (mBtDevice != null) {
                           Log.i(TAG,"Disconnect Device");
                           mService.disconnect();
                        }
                    }
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"onDestroy");
        try{
            LocalBroadcastManager.getInstance(this).unregisterReceiver(ThingyStatusChangeReceiver);
        }catch(Exception ignore){
            Log.e(TAG,ignore.toString());
        }
        unbindService(mServiceConnection);
    }

    @Override
    protected void onStop() {
        Log.d(TAG,"onStop");
        super.onStop();
    }

    @Override
    protected void onPause(){
        Log.d(TAG,"OnPause");
        super.onPause();
    }

    @Override
    protected void onRestart(){
        Log.d(TAG,"onRestart");
        super.onRestart();
    }

    @Override
    protected void onResume(){
        super.onResume();
        Log.d(TAG,"onResume");
        if(!mBtAdapter.isEnabled()){
            Log.i(TAG,"onResume-BT not enable yet");
            Intent enableIntent=new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
        }
    }

    private void service_init(){
        Intent bindIntent=new Intent(this,ThingyService.class);
        bindService(bindIntent,mServiceConnection, Context.BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(ThingyStatusChangeReceiver,makeGattUpdateFilter());
    }

    /**
     * Creates a service connection for the Thingy Service in the callback
     */
    private ServiceConnection mServiceConnection=new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService=((ThingyService.LocalBinder) service).getService();
            Log.d(TAG,"onServiceConnected mService "+mService);
            if(!mService.initialize()){
                Log.e(TAG,"Unable to initialize Bluetooth");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService=null;
        }
    };

    private final BroadcastReceiver ThingyStatusChangeReceiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();



            if(ThingyService.ACTION_GATT_CONNECTED.equals(action)){
                runOnUiThread((new Runnable() {
                    @Override
                    public void run() {
                        String currentDateTimeString=DateFormat.getDateTimeInstance().format(new Date());
                        Log.d(TAG,"Thingy Gatt Connected");
                        btnConnectDisconnect.setText("Disconnect");
                        ((TextView)findViewById(R.id.deviceName)).setText(mBtDevice.getName()+" ready");
                        mState=THINGY_PROFILE_CONNECTED;
                    }
                }));
            }

            if(ThingyService.ACTION_GATT_DISCONNECTED.equals(action)){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String currrentDateTimeString=DateFormat.getDateTimeInstance().format(new Date());
                        Log.d(TAG,"THINGY_DISCONNECT_MSG");
                        btnConnectDisconnect.setText("Connect");
                        mState=THINGY_PROFILE_DISCONNECTED;
                        ((TextView)findViewById(R.id.deviceName)).setText("Not Connected");

                    }
                });
            }

            if(ThingyService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)){
                Log.d(TAG,"BLE GATT Services discovered");
                mService.enableTempNotification();
            }

            if (ThingyService.ACTION_DATA_AVAILABLE.equals(action)){
                final byte[] tempBytes=intent.getByteArrayExtra(ThingyService.TEMPERATURE_DATA);
                Log.d(TAG,"response value"+tempBytes.toString());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            int tempWhole= tempBytes[0];
                            int tempFract=tempBytes[1];
                            Double TempValue=(tempWhole+tempFract/(double)100);
                            tvTemperature.setText(String.valueOf(TempValue));
                            //tvsampleTime.setText(DateFormat.getDateTimeInstance().format((new Date())));
                        }catch (Exception e){
                            Log.e(TAG,e.toString());
                        }
                    }
                });
            }
            if(ThingyService.DEVICE_DOES_NOT_SUPPORT_THINGY.equals(action)){
                showMessage(("Device doesn't support Thingy Service, Disconnectging"));
                mService.disconnect();
            }
        }
    };

    private static IntentFilter makeGattUpdateFilter(){
        final IntentFilter intentFilter=new IntentFilter();
        intentFilter.addAction(ThingyService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(ThingyService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(ThingyService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(ThingyService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(ThingyService.DEVICE_DOES_NOT_SUPPORT_THINGY);
        return intentFilter;
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
    }


    @Override
    public void onActivityResult(int requestcode,int resultcode,Intent data){
        Log.e(TAG,"Result Code: "+Integer.toString(resultcode));
        switch(requestcode) {
            case REQUEST_SELECT_DEVICE:
                if(resultcode==Activity.RESULT_OK){
                    String deviceAddress=data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    Log.i(TAG, "got device "+ deviceAddress);
                    mBtDevice=BluetoothAdapter.getDefaultAdapter().getRemoteDevice((deviceAddress));
                    ((TextView) findViewById(R.id.deviceName)).setText(mBtDevice.getName());
                    mService.connect(deviceAddress);
                }
                break;
            case REQUEST_ENABLE_BT://when request to enable Bluetooth returns
                if (resultcode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on", Toast.LENGTH_SHORT).show();
                } else {//User did not enable Bluetooth or error occured
                    Log.d(TAG, "Bluetooth not enabled");
                    Toast.makeText(this, "Problem turning on Bluetooth", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
     }

     @Override
     public void onBackPressed(){
        if(mState==THINGY_PROFILE_CONNECTED){
            Intent startMain=new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("Thingy running in Background.\n Disconnect to exit");
        }
        else{
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.popup_message)
                    .setPositiveButton(R.string.popup_yes,new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.popup_no,null)
                    .show();
        }
     }


    private void showMessage(String msg){
         Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();
    }

}

