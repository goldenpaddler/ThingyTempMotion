package com.doggonics.thingytempmotion;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
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
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.TestLooperManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import java.lang.String;



import android.widget.TextView;
import android.widget.Toast;


import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.MarkerImage;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private float startSampleTime;
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

    private SensorManager mSensorManager;
    private Sensor mTempSensor;
    
    private LineChart mChart;
    private Thread thread;
    private boolean plotData=true;
    


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
        tvsampleTime=findViewById(R.id.sampleTime);
        tvsampleTime.setVisibility(View.INVISIBLE);
        btnConnectDisconnect=findViewById(R.id.btn_select);
        mChart=(LineChart)findViewById(R.id.lineChart);
        
        mSensorManager= (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mTempSensor=mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if(mTempSensor!=null){
            mSensorManager.registerListener(this,mTempSensor,SensorManager.SENSOR_DELAY_GAME);
        }

        mChart.setTouchEnabled(false);
        mChart.setDragEnabled(false);
        mChart.setScaleEnabled(false);
        mChart.setPinchZoom(false);
        mChart.getDescription().setEnabled(false);
        mChart.getDescription().setText("Temperature");
        mChart.setBackgroundColor(Color.WHITE);
         
        LimitLine lower_limit=new LimitLine(32f,"Low");
        lower_limit.setLineWidth(4f);
        lower_limit.setLineColor(Color.BLUE);
        lower_limit.enableDashedLine(10f,10f,0f);
        lower_limit.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
        lower_limit.setTextSize(15f);
        lower_limit.setTextColor(Color.BLUE);

        YAxis leftAxis=mChart.getAxisLeft();
        leftAxis.removeAllLimitLines();
        leftAxis.addLimitLine(lower_limit);
        leftAxis.setAxisMaximum(40f);
        leftAxis.setAxisMinimum(15f);

        mChart.getAxisRight().setEnabled(false);
        XAxis xAxis=mChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);


        LineData data=new LineData();
        data.setValueTextColor(Color.WHITE);
        mChart.setData(data);

                     
        service_init();
        startPlot();


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

    private void startPlot(){
        if(thread!=null){
            thread.interrupt();
        }
        thread=new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    plotData=true;
                    try{
                        Thread.sleep(10);
                    }catch (InterruptedException e){
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();
    }
    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        thread.interrupt();
        mSensorManager.unregisterListener(MainActivity.this);
        Log.d(TAG,"onDestroy");
        try{
            LocalBroadcastManager.getInstance(this).unregisterReceiver(ThingyStatusChangeReceiver);
        }catch(Exception ignore){
            Log.e(TAG,ignore.toString());
        }
        if(mService!=null){mService.close();}
        if(mServiceConnection!=null){unbindService(mServiceConnection);}
        super.onDestroy();

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
        if(thread!=null){
            thread.interrupt();
        }
        mSensorManager.unregisterListener(this);
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
        mSensorManager.registerListener(this,mTempSensor,SensorManager.SENSOR_DELAY_GAME);
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
                startSampleTime= new Date().getTime();
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
                            float TempValue=(tempWhole+tempFract/(float)100);
                            tvTemperature.setText(String.valueOf(TempValue));
                            SimpleDateFormat timeFormat=new SimpleDateFormat("ss");
                            String timeNow=timeFormat.format(new Date());
                            tvsampleTime.setText(timeNow);
                            float sampleTime=new Date().getTime();
                            float timeValue=(startSampleTime-sampleTime)/1000;

                            addEntry(timeValue,TempValue);
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

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Log.d(TAG,"new Temperature value");
        if(plotData){
            //addEntry(sensorEvent);
            plotData=false;
        }
    }

    public void addEntry(float timeValue, float sensorValue){
        LineData data=mChart.getData();
        Log.d(TAG, Float.toString(sensorValue));
        if (data != null) {
            ILineDataSet set=data.getDataSetByIndex(0);
            if(set==null){
                set=createSet();
               data.addDataSet(set);
            }
            data.addEntry(new Entry(set.getEntryCount(),sensorValue),0);

            data.notifyDataChanged();
            mChart.notifyDataSetChanged();
            mChart.setVisibleXRangeMaximum(20);
            mChart.moveViewToX(data.getEntryCount());
        }
    }
    
    private LineDataSet createSet(){
        LineDataSet set=new LineDataSet(null,"Chest");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setLineWidth(3f);
        set.setColor(Color.MAGENTA);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setCubicIntensity(0.2f);
        set.setDrawValues(false);
        return set;
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        
    }
}

