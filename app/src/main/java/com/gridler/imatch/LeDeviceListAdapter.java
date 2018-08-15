package com.gridler.imatch;

import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;

// Adapter for holding devices found through scanning.
public class LeDeviceListAdapter extends BaseAdapter {
    private ArrayList<BluetoothDevice> bluetoothDevices;
    private LayoutInflater layoutInflater;

    LeDeviceListAdapter(LayoutInflater layoutInflater) {
        super();
        bluetoothDevices = new ArrayList<>();
        this.layoutInflater = layoutInflater;
    }

    public void addDevice(BluetoothDevice device) {
        if (!bluetoothDevices.contains(device)) {
            bluetoothDevices.add(device);
        }
    }

    public BluetoothDevice getDevice(int position) {
        return bluetoothDevices.get(position);
    }

    public void clear() {
        if (bluetoothDevices != null && !bluetoothDevices.isEmpty()) bluetoothDevices.clear();
    }

    String[] getDeviceNames() {
        if (this.getCount() == 0) return null;
        String[] result = new String[this.getCount()];
        for (int i = 0; i < this.getCount(); i++) {
            String name = this.getDevice(i).getName();
            if (!name.contains("-") && this.getDevice(i).getAddress() != null && this.getDevice(i).getAddress().length() > 0) {
                String devId = this.getDevice(i).getAddress().replace(":", "");
                if (devId.length() == 12) {
                    name += "-" + devId.substring(8);
                }
            }
            result[i] = name;
        }
        return result;
    }

    @Override
    public int getCount() {
        return bluetoothDevices.size();
    }

    @Override
    public Object getItem(int i) {
        return bluetoothDevices.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        PairingActivity.ViewHolder viewHolder;
        // General ListView optimization code.
        if (view == null) {
            view = layoutInflater.inflate(R.layout.listitem_device, null);
            viewHolder = new PairingActivity.ViewHolder();
            viewHolder.deviceName = view.findViewById(R.id.device_name);
            view.setTag(viewHolder);
        } else {
            viewHolder = (PairingActivity.ViewHolder) view.getTag();
        }

        BluetoothDevice device = bluetoothDevices.get(i);
        String deviceName = device.getName();
        String deviceAddress = device.getAddress();

        if (!deviceName.contains("-") && deviceAddress != null && deviceAddress.length() > 0) {
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
