package com.doggonics.thingytempmotion;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.os.Handler;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by soggy on 10/7/2018.
 */

public class DeviceListActivity extends AppCompatActivity {
    private BluetoothAdapter mBluetoothAdapter;

    //private BluetoothAdapter mBtAdapter;
    private TextView mEmptyList;
    public static final String TAG="DeviceListActivity";
    private static final int REQUEST_BT_ENABLE=1;
    private BluetoothLeScanner mBluetoothLeScanner;

    TextView tvTitle;
    List<BluetoothDevice> deviceList;
    private DeviceAdapter deviceAdapter;

    private ServiceConnection onService=null;
    Map<String,Integer> devRssiValues;
    private static final long SCAN_PERIOD=20000; //scanning for 10 seconds
    private Handler mHandler;
    private boolean mScanning;

    @Override
    protected void onCreate(Bundle saveInstanceState){
        super.onCreate(saveInstanceState);
        Log.d(TAG,"OnCreate");
        this.getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,R.layout.title_bar);
        setContentView(R.layout.device_list);
        android.view.WindowManager.LayoutParams layoutParams=this.getWindow().getAttributes();
        layoutParams.gravity= Gravity.TOP;
        layoutParams.y=200;
        mHandler=new Handler();
        //Use this to determine whether BLE is supported ont the device.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
            Toast.makeText(this,R.string.ble_not_supported,Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        //Initialize Bluetooth Adapter
        //grab reference to Bluetooth adaptor through the Bluetooth manager
        final BluetoothManager bluetoothmanager=(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter=bluetoothmanager.getAdapter();

        //Checks if Bluetooth is supported on the device
        if(mBluetoothAdapter==null){
            Toast.makeText(this,R.string.ble_not_supported,Toast.LENGTH_SHORT).show();
            finish();
            return;
        }else{
            if(!mBluetoothAdapter.isEnabled()){
                Intent enableBTintent=new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBTintent,REQUEST_BT_ENABLE);
            }
        }
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        populateList();
        Button cancelButton=(Button)findViewById(R.id.btn_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mScanning == false) scanLeDevice(true);
                else {
                    finish();
                    return;
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mBluetoothLeScanner.stopScan(mLeScanCallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothLeScanner.stopScan(mLeScanCallback);
    }

    private void populateList() {
        Log.d(TAG,"populatelist");
        deviceList=new ArrayList<BluetoothDevice>();
        deviceAdapter= new DeviceAdapter(this, deviceList);
        devRssiValues =new HashMap<String,Integer>();

        ListView newDeviceListView=(ListView) findViewById(R.id.new_devices);
        newDeviceListView.setAdapter(deviceAdapter);
         newDeviceListView.setOnItemClickListener(mDeviceClickListener);
        Log.d(TAG,"going to scan device");
        scanLeDevice(true);
    }

    private AdapterView.OnItemClickListener mDeviceClickListener=new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            BluetoothDevice device=deviceList.get(position);
            mBluetoothLeScanner.stopScan(mLeScanCallback);
            Bundle b=new Bundle();
            b.putString(BluetoothDevice.EXTRA_DEVICE,deviceList.get(position).getAddress());
            Intent result=new Intent();
            result.putExtras(b);
            setResult(Activity.RESULT_OK,result);
            Log.d(TAG,"setitemOnClickListner");
            finish();
        }
    };

    class DeviceAdapter extends BaseAdapter{
        Context mContext;
        List<BluetoothDevice> mDevices;
        LayoutInflater inflater;


        public DeviceAdapter(Context context,List<BluetoothDevice> devices) {
            this.mContext=context;
            inflater=LayoutInflater.from(context);
            this.mDevices=devices;
            Log.i(TAG,devices.toString());
        }

        @Override
        public int getCount() {
            return mDevices.size();
        }

        @Override
        public Object getItem(int position) {
            return mDevices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewGroup vg;

            if(convertView!=null) {
                vg=(ViewGroup) convertView;
            } else{
                vg=(ViewGroup) inflater.inflate(R.layout.device_element,null);
            }
            BluetoothDevice device=mDevices.get(position);
            final TextView tvadd=(TextView) vg.findViewById(R.id.address);
            final TextView tvname=(TextView) vg.findViewById(R.id.name);
            final TextView tvpaired=(TextView) vg.findViewById(R.id.paired);
            final TextView tvrssi=(TextView) vg.findViewById(R.id.rssi);

            tvrssi.setVisibility(View.VISIBLE);
            byte rssivalue=(byte) devRssiValues.get(device.getAddress()).intValue();
            if(rssivalue!=0){
                tvrssi.setText("Rssi = "+String.valueOf(rssivalue));
            }
            tvname.setText((device.getName() !=null) ? device.getName():"");
            tvadd.setText(device.getAddress());
            if(device.getBondState()==BluetoothDevice.BOND_BONDED){
                Log.i(TAG,"device::"+device.getName());
                tvname.setTextColor(Color.WHITE);
                tvadd.setTextColor(Color.WHITE);
                tvpaired.setTextColor(Color.GRAY);
                tvpaired.setVisibility(View.VISIBLE);
                tvpaired.setText(R.string.paired);
                tvrssi.setVisibility((View.VISIBLE));
                tvrssi.setTextColor(Color.WHITE);
            } else {
                tvname.setTextColor(Color.WHITE);
                tvadd.setTextColor(Color.WHITE);
                tvpaired.setVisibility(View.GONE);
                tvrssi.setVisibility(View.VISIBLE);
                tvrssi.setTextColor(Color.WHITE);
            }
            return vg;

            }
        }

    private  void scanLeDevice(final boolean enable){
        final Button cancelButton=(Button) findViewById(R.id.btn_cancel);
        Log.d(TAG,"scanLeDevice");
        Log.d(TAG,Boolean.toString(enable));

        if(enable) {
            //Stops scanning after a pre-defined scan period
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothLeScanner.stopScan(mLeScanCallback);
                    //mEmptyList.setText("Scan Complete");
                }
            }, SCAN_PERIOD);
            mScanning = true;
            //mEmptyList.setText("Scanning...");
            String pattern="/.*/";
            ScanFilter scanFilter=new ScanFilter.Builder().setDeviceName("myThingy").build();
            List<ScanFilter> scanFilters= new ArrayList<ScanFilter>();
            scanFilters.add(scanFilter);
            ScanSettings scanSettings=new ScanSettings.Builder().build();

            mBluetoothLeScanner.startScan(scanFilters, scanSettings,mLeScanCallback);
            cancelButton.setText(R.string.cancel);
        }
        else{
            mScanning=false;
            mBluetoothLeScanner.stopScan(mLeScanCallback);
            cancelButton.setText(R.string.scan);
        }
    }

    private ScanCallback mLeScanCallback=new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i(TAG,"mLeScanCallback Type "+String.valueOf(callbackType));
            BluetoothDevice btDevice=result.getDevice();

            if (!(deviceList.contains(btDevice))){
                deviceList.add(btDevice);
                devRssiValues.put(btDevice.getAddress(),result.getRssi());
                deviceAdapter.notifyDataSetChanged();
            }
            //Log.i(TAG,deviceList.toString());
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            /*process a batch of scan results*/
             for(ScanResult sr:results){
                 Log.i(TAG,sr.toString());
                 deviceList.add(sr.getDevice());
             }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    private void showMessage(String msg){
        Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();
    }
}
