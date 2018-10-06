package com.doggonics.thingytempmotion;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

public class MainActivity extends AppCompatActivity implements RadioGroup.OnCheckedChangeListener {

        private static final int REQUEST_SELECT_DEVICE=1;
        private static final int REQUEST_ENABLE_BT=2;
        private static final int ENVIRO_PROFILE_READY=10;
        private static final String TAG="ThingyEnviro";
        private static final int ENVIRO_PROFILE_CONNECTED=20;
        private static final int ENVIRO_PROFILE_DISCONNECTED=21;
        private static final int STATE_OFF=10;

        TextView mRemoteRssiVal;
        RadioGroup mRg;
        private int mState=ENVIRO_PROFILE_DISCONNECTED;
        private EnviroService mService=null;
        private BluetoothDevice mBtDevice=null;
        private BluetoothAdapter mBtAdapter=null;
        private ListView messageListView;
        private ArrayAdapter<String> listAdapter;
        private Button btnConnectDisconnect,btnSend;
        private EditText edtMessage;



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
        messageListView=(ListView)findViewById(R.id.listMessage);
        listAdapter=new ArrayAdapter<String>(this,R.layout.message_detail);

    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

    }
}

;