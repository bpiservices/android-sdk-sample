package com.gridler.imatchsample;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;

import com.gridler.imatchlib.FirmwareUpdateResponse;
import com.gridler.imatchlib.FirmwareUpdateTask;
import com.gridler.imatchlib.ImatchDevice;

public class FirmwareUpdate implements FirmwareUpdateResponse {
    private final static String TAG = FirmwareUpdateResponse.class.getSimpleName();
    private ProgressDialog progressDialog;
    private Context context;

    public void showFirmwareDialog(Context context)
    {
        this.context = context;

        ImatchDevice device = ImatchDevice.getInstance();
        String installedFirmwareVersion = device.GetFirmwareVersion();
        String sdkFirmwareVersion = device.GetSdkFirmwareVersion(context);

        progressDialog = new ProgressDialog(context);
        progressDialog.setMessage("Updating Firmware");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(false);
        progressDialog.setProgress(0);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        progressDialog.setMax(device.GetSdkFirmwareSize(context));

        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    progressDialog.show();
                    updateFirmware();
                }
            }
        };

        String message = "A different version of the firmware is available compared to what is on the iMatch. Update now?";
        message += "\r\n" + "Installed: " + installedFirmwareVersion;
        message += "\r\n" + "Available: " + sdkFirmwareVersion;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message)
                .setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("No", dialogClickListener).show();
    }

    private void updateFirmware() {
        FirmwareUpdateTask firmwareUpdateTask = new FirmwareUpdateTask(this.context, this);
        firmwareUpdateTask.execute();
    }

    @Override
    public void updateProgress(int progress) {
        progressDialog.setProgress(progress);
    }

    @Override
    public void restart(int restartTicks) {
        progressDialog.setProgress(0);
        progressDialog.setMax(restartTicks);
    }

    @Override
    public void updateCompleted() {
        progressDialog.dismiss();
    }

    @Override
    public void updateFailed(Exception exception) {
        progressDialog.dismiss();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Firmware Update Error").setMessage(exception.getMessage()).show();
    }
}