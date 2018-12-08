package com.doggonics.thingytempmotion;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.lang.ref.PhantomReference;
import java.util.UUID;

import static android.util.Log.w;

public class ThingyService extends Service {
    private final static String TAG="ThingyService";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState=STATE_DISCONNNCTED;

    private static final int STATE_DISCONNNCTED=0;
    private static final int STATE_CONNECTING=1;
    private static final int STATE_CONNECTED=2;

    public final static String ACTION_GATT_CONNECTED=
            "com.doggonics.thingy.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED=
            "com.doggonics.thingy.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED=
            "com.doggonics.thingy.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE=
            "com.doggonics.thingy.ACTION_DATA_AVAILABLE";
    public final static String TEMPERATURE_DATA=
            "com.doggonics.thingy.EXTRA_DATA";
    public final static String DEVICE_DOES_NOT_SUPPORT_THINGY=
            "com.doggonics.thingy.DEVICE_DOES_NOT_SUPPORT_THINGY";

    public static final UUID THINGY_ENVIRONMENTAL_SERVICE = new UUID(0xEF6802009B354933L, 0x9B1052FFA9740042L);
    public static final UUID TEMPERATURE_CHARACTERISTIC   = new UUID(0xEF6802019B354933L, 0x9B1052FFA9740042L);
    public static final UUID CONFIGURATION_CHARACTERISTIC = new UUID(0xEF6802069B354933L, 0x9B1052FFA9740042L);
    public static final UUID CCCD                         = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    public class LocalBinder extends Binder {
        ThingyService getService(){
            return ThingyService.this;
        }
    }

    private final IBinder mBinder= new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        //after using a given device, you should make sure that the BluetoothGatt.close()
        //is called such that resources are cleaned up properly. In this particular example
        //invoked when the UI is disconnected from the Service
        mBluetoothGatt.close();
        return super.onUnbind(intent);
    }



    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            String intentAction;

            if(newState== BluetoothProfile.STATE_CONNECTED){
                intentAction=ACTION_GATT_CONNECTED;
                mConnectionState=STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG,"BLE Gatt Connected ");
                Log.i(TAG,"attempting to discover services");
                mBluetoothGatt.discoverServices();

            }else if (newState==BluetoothProfile.STATE_DISCONNECTED){
                intentAction=ACTION_GATT_DISCONNECTED;
                mConnectionState=STATE_DISCONNNCTED;
                Log.i(TAG,"Disconnected from GATT Server");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if(status==BluetoothGatt.GATT_SUCCESS){
                Log.w(TAG,"mBluetooth GATT onServicesDiscovered ="+mBluetoothGatt);
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG,"onServicesDiscovered received: "+ status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status==BluetoothGatt.GATT_SUCCESS){
                broadcastUpdate(ACTION_DATA_AVAILABLE,characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE,characteristic);
        }
    };

    private void broadcastUpdate(final String action){
        final Intent intent=new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic){

        final Intent intent=new Intent(action);
        //This is handling for the notification on Temperature characteristic
        if(TEMPERATURE_CHARACTERISTIC.equals(characteristic.getUuid())){
            Log.d(TAG,"Received temperature: "+ characteristic.getValue());
            intent.putExtra(TEMPERATURE_DATA,characteristic.getValue());
        } else{
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * Initializes a reference to the local bluetooth adapter.
     *
     * @return Return true if the initialization is successful
     * */
    public boolean initialize(){
        //For API level 18 and above, get a reference to BluetoothAdapter
        //through BluetoothManager

        if (mBluetoothManager==null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize Bluetooth Manager.");
                return false;
            }
        }
        mBluetoothAdapter=mBluetoothManager.getAdapter();
        if (mBluetoothAdapter==null){
            Log.e(TAG,"Unable to get Bluetooth Adapter.");
            return false;
        }
        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param bluetoothAddress The device address of the destination device
     *
     *  @return Return true if the connection is initiated successfully. The connection
     *  result is reported asynchronously through the {@code BluetoothGattCallback#onConectionStateChange
     *  android.bluetooth.BluetoothGatt,int,int} callback
     */
    public boolean connect (final String bluetoothAddress){
        if (mBluetoothAdapter==null || bluetoothAddress==null){
            Log.w(TAG,"Bluetooth Adapter no initialized or unspecified address");
            return false;
        }
        //Previously connected device. Try to reconnect cache may not be updatae
        if (bluetoothAddress.equals(mBluetoothDeviceAddress)
            && mBluetoothDeviceAddress!=null
            && mBluetoothGatt!=null) {
            Log.d(TAG,"Trying to use an existing mBluetoothGatt for connection");
            if (mBluetoothGatt.connect()) {
                mConnectionState=STATE_CONNECTED;
                return true;
            }else{
                return false;
            }
         }
        final BluetoothDevice device=mBluetoothAdapter.getRemoteDevice(bluetoothAddress);
        if(device==null){
            Log.w(TAG,"Device not found. Unable to connect");
            return false;
        }
        //We want to directly connect to the devicem so we are setting autoconnect to false.
        //Direct connect will not automatically try to reconnect, at will attempt
        //connect for 30 seconds.
        mBluetoothGatt=device.connectGatt(this,false,mGattCallback,BluetoothDevice.TRANSPORT_LE);
        Log.d(TAG,"Trying to create a new connection");
        mBluetoothDeviceAddress=bluetoothAddress;
        mConnectionState=STATE_CONNECTED;
        return true;
    }
    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection
     * result is reported asynchronously through
     *{@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt,int,int)}
     * callback
     * Can call connect() again WITHOUT a call to connectGatt,Gatt resources NOT released
     */
    public void disconnect(){
        if(mBluetoothAdapter==null||mBluetoothGatt==null){
            Log.w(TAG,"BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a BLE device, the app must call this method to ensure resources are released.
     * close() will NOT call onConnectionStateCahage.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            Log.d(TAG, "BluetoothGatt is null");
            return;
        }
        Log.w(TAG,"mBluetoothGatt closed");
        mBluetoothDeviceAddress=null;
        mBluetoothGatt.close();
        mBluetoothGatt=null;
    }
    /**
     * Request a read on a given{@code BluetoothGattCharacteristic} The read result is reported
     * asynchronouslyu through
     * {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt,
     * android.bluetooth.BluetoothGattCharacteristic,int} callback
     *
     * @parm characteristic is the characteristic to read from
     *
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic){
        if (mBluetoothAdapter==null|| mBluetoothGatt==null){
            Log.w(TAG,"BluetoothAdapter not intialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables notification of the temperature characteristic
     **/
    public void enableTempNotification(){
        BluetoothGattService EnvironmentalService=mBluetoothGatt.getService(THINGY_ENVIRONMENTAL_SERVICE);
        if(EnvironmentalService==null){
            showMessage("Thingy Environmental Service not Found");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_THINGY);
            return;
        }
       BluetoothGattCharacteristic TempChar=EnvironmentalService.getCharacteristic(TEMPERATURE_CHARACTERISTIC);
       if(TempChar==null){
            showMessage("Temperature Characteristic not found");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_THINGY);
            return;
        }
       mBluetoothGatt.setCharacteristicNotification(TempChar,true);
       BluetoothGattDescriptor descriptor=TempChar.getDescriptor(CCCD);
       descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
       mBluetoothGatt.writeDescriptor(descriptor);
    }


    public void showMessage(String msg){
       Log.e(TAG,msg);
    }
}
