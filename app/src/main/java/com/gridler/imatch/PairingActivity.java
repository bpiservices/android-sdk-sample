package com.gridler.imatch;

import android.annotation.SuppressLint;
import android.app.ListActivity;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.gridler.gubanilib.PairingListener;
import com.gridler.gubanilib.PairingService;

import java.util.ArrayList;

public class PairingActivity extends ListActivity implements PairingListener {
    private LeDeviceListAdapter mLeDeviceListAdapter;

    PairingService mPairingService = null;

    boolean mScanning = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null)
            getActionBar().setDisplayHomeAsUpEnabled(true);

        setTitle(getString(R.string.activity_title_pairing_activity));

        setContentView(R.layout.activity_pairing);

        mPairingService = new PairingService();
        mPairingService.setListener(this);
        mPairingService.init(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_pairing, menu);
        try {
            if (!mScanning) {
                menu.findItem(R.id.menu_stop).setVisible(false);
                menu.findItem(R.id.menu_scan).setVisible(true);
            } else {
                menu.findItem(R.id.menu_stop).setVisible(true);
                menu.findItem(R.id.menu_scan).setVisible(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return true;
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // clear results
        if (mLeDeviceListAdapter != null)
            mLeDeviceListAdapter.clear();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null)
            return;
    }

    @Override
    public void addDevice(BluetoothDevice dev) {
        final BluetoothDevice _dev = dev;
        runOnUiThread(new Runnable() {


            @Override
            public void run() {
                mLeDeviceListAdapter.addDevice(_dev);
                mLeDeviceListAdapter.notifyDataSetInvalidated();
            }
        });
    }

    @Override
    public void pairDeviceSelector() {
        Log.d("PairingActivity", "pairDeviceSelector");
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mLeDeviceListAdapter.pairDeviceSelector();
            }
        });
    }

    @Override
    public void startActivityForResult() {
        Log.d("PairingActivity", "startActivityForResult");
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mLeDeviceListAdapter.startActivityForResult();
            }
        });
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflator = PairingActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public void pairDeviceSelector() {
            Log.d("PairingActivity", "pairDeviceSelector()");
        }

        public void startActivityForResult() {
            Log.d("PairingActivity", "startActivityForResult()");
        }

        BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @SuppressLint("InflateParams")
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;

            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceName = view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            String deviceName = device.getName();
            String deviceAddress = device.getAddress();

            if (deviceAddress != null && deviceAddress.length() > 0) {
                String devId = deviceAddress.replace(":", "");
                if (devId.length() == 12) {
                    deviceName += "-" + devId.substring(8);
                }
            }

            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);

            return view;
        }
    }

    static class ViewHolder {
        TextView deviceName;
    }
}
