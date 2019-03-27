package com.gridler.imatchsample;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;

// Adapter for holding devices found through scanning.
public class LeDeviceListAdapter extends BaseAdapter {
    private ArrayList<String> bluetoothDevices;
    private LayoutInflater layoutInflater;

    LeDeviceListAdapter(LayoutInflater layoutInflater) {
        super();
        bluetoothDevices = new ArrayList<>();
        this.layoutInflater = layoutInflater;
    }

    public void addDevice(String device) {
        if (!bluetoothDevices.contains(device)) {
            bluetoothDevices.add(device);
        }
    }

    public String getDevice(int position) {
        return bluetoothDevices.get(position);
    }

    public void clear() {
        if (bluetoothDevices != null && !bluetoothDevices.isEmpty()) bluetoothDevices.clear();
    }

    String[] getDeviceNames() {
        if (this.getCount() == 0) return null;
        String[] result = new String[this.getCount()];
        for (int i = 0; i < this.getCount(); i++) {
            String name = this.getDevice(i);
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
        return null;
    }
}
