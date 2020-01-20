package com.gridler.imatchsample;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.gridler.imatchlib.ImatchFingerPrintListener;
import com.gridler.imatchlib.ImatchManagerListener;
import com.gridler.imatchlib.ImatchSmartCardListener;
import com.gridler.imatchlib.MrtdUtils;
import com.gridler.imatchlib.Utils;
import com.gridler.imatchsdk.FingerprintImage;
import com.gridler.imatchsdk.ImatchFPEnrollmentParams;
import com.gridler.imatchsdk.ImatchFPEnrollmentResult;
import com.gridler.imatchsdk.ImatchFingerprintReader;
import com.gridler.imatchsdk.Hex;
import com.gridler.imatchsdk.ILVAsyncMessage;
import com.gridler.imatchsdk.ILVConstant;
import com.gridler.imatchsdk.ImatchManager;

import com.gridler.imatchlib.Device;
import com.gridler.imatchlib.ImatchDevice;
import com.gridler.imatchlib.Method;
import com.gridler.imatchlib.ImatchListener;
import com.gridler.imatchsdk.ImatchSmartcardReader;
import com.regula.documentreader.api.DocumentReader;
import com.regula.documentreader.api.enums.DocReaderAction;
import com.regula.documentreader.api.results.DocumentReaderResults;
import com.regula.documentreader.api.results.DocumentReaderTextField;

import org.jnbis.api.Jnbis;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class MainActivity extends ListActivity implements ImatchManagerListener, ImatchListener, ImatchFingerPrintListener, ImatchSmartCardListener, PermissionResultCallback {
    static String TAG = MainActivity.class.getSimpleName();
    ImatchFingerprintReader mFpReader;
    ImatchSmartcardReader mScReader;

    Button connectButton;
    Button scanPassportButton;
    Button scanMrzButton;
    Button scanFingerprintButton;
    Button scanSmartCardButton;
    CheckBox saveWsqCheckbox;
    ImageView photoImageView;
    TextView batteryText;
    boolean documentReaderLicensed;
    String vizMrz;
    LeDeviceListAdapter leDeviceListAdapter;
    AlphaAnimation inAnimation;
    AlphaAnimation outAnimation;
    FrameLayout progressBarHolder;

    boolean activityOnTop = true;
    boolean readingChip = false;

    ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ArrayList<String> permissions=new ArrayList<>();
        PermissionUtils permissionUtils = new PermissionUtils(this);

        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        permissions.add(Manifest.permission.BLUETOOTH);
        permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        permissions.add(Manifest.permission.CAMERA);
        permissionUtils.check_permission(permissions,"To function correctly this application needs the following permissions",1);


        batteryText = findViewById(R.id.textBatteryPercentage);
        connectButton = findViewById(R.id.connectButton);
        scanPassportButton = findViewById(R.id.scanPassportButton);
        scanMrzButton = findViewById(R.id.scanMrzButton);
        scanFingerprintButton = findViewById(R.id.scanFingerprintButton);
        scanSmartCardButton = findViewById(R.id.scanSmartCardButton);
        saveWsqCheckbox = findViewById(R.id.saveWsqCheckbox);
        photoImageView = findViewById(R.id.photo);
        progressBarHolder = findViewById(R.id.progressBarHolder);

        // Initializes list view adapter
        leDeviceListAdapter = new LeDeviceListAdapter(MainActivity.this.getLayoutInflater());
        setListAdapter(leDeviceListAdapter);

        ImatchDevice.getInstance().AddListener(Device.Board, this);
        ImatchSmartcardReader.getInstance().setListener(this);

        mFpReader = ImatchFingerprintReader.getInstance();
        mFpReader.setListener(this);
        checkDocumentReaderLicense();
    }

    @Override
    public void onResume() {
        ImatchManager.getInstance().Init(getApplication(), true, this);

        File f = new File(Environment.getExternalStorageDirectory(), "iMatchSample");
        if (!f.exists()) {
            f.mkdirs();
        }
        activityOnTop = true;
        super.onResume();
    }

    @Override
    public void onPause() {
        activityOnTop = false;
        super.onPause();
    }

    @Override
    public void onDestroy() {
        try {
            ImatchSmartcardReader.getInstance().removeListeners();
            ImatchDevice.getInstance().Disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Disconnect Service failed! " + e.getLocalizedMessage());
        }

        super.onDestroy();
        this.finish();
        Log.d(TAG, "Application destroyed");
    }

    public void checkDocumentReaderLicense() {
        // The MRZ scanning function requires a valid documentReader license. please visit:
        //    https://licensing.regulaforensics.com
        // to obtain a trial license.

        if (!documentReaderLicensed) {
            try {
                // Check if the Document Reader has been licensed
                InputStream licInput = getResources().openRawResource(R.raw.regula);
                final byte[] license = new byte[licInput.available()];
                licInput.read(license);

                // Prepare MRZ database
                progressDialog = new ProgressDialog(MainActivity.this);
                progressDialog.setMessage("Initializing Document Reader");
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progressDialog.setIndeterminate(true);
                progressDialog.show();

                //Initializing the reader
                DocumentReader.Instance().initializeReader(MainActivity.this, license, new DocumentReader.DocumentReaderInitCompletion() {
                    @Override
                    public void onInitCompleted(boolean success, String error) {
                        DocumentReader.Instance().customization().setShowResultStatusMessages(true);
                        DocumentReader.Instance().customization().setShowStatusMessages(true);
                        DocumentReader.Instance().functionality().setVideoCaptureMotionControl(true);

                        documentReaderLicensed = success;

                        if(progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }

                        if (documentReaderLicensed) {
                            DocumentReader.Instance().processParams().scenario = "Mrz";
                        }
                        else {
                            // Notify that the Document Reader license is not valid
                            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                            builder.setTitle(R.string.Information);
                            builder.setMessage(R.string.InvalidDocumentReaderLicense + ": " + error);
                            builder.setPositiveButton(R.string.strOK, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                            builder.show();
                        }
                    }
                });


                licInput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Called when the user taps the Connect button
     */
    public void connectDevice(View view) {
        if (ImatchDevice.getInstance().Connected()) {
            StopBluetooth();
        } else {
            PairBluetooth();
        }
    }

    /**
     * Called when the user taps the Scan MRZ button
     */
    public void scanMrz(View view) {
        // Launch OCR scanner
        DocumentReader.Instance().showScanner(completion);
    }

    /**
     * Called when the user taps the Scan Passport button
     */
    public void scanPassport(View view) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                photoImageView.setImageDrawable(null);
            }
        });
        readMRTD(vizMrz);
    }

    /**
     * Called when the user taps the Scan Fingerprint button
     */
    public void scanFingerprint(View view) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Drawable myDrawable = getResources().getDrawable(R.drawable.fingerprint);
                            photoImageView.setImageDrawable(myDrawable);
                        }
                    });

                    ImatchFPEnrollmentParams enrollmentParams = new ImatchFPEnrollmentParams();
                    enrollmentParams.configAsynchronousEvent(ImatchFPEnrollmentParams.ASYNC_MSG_FINGER_POSITION | ImatchFPEnrollmentParams.ASYNC_MSG_ENROLLMENT_STEP);
                    enrollmentParams.setConsolidation((byte) 1);
                    enrollmentParams.setExportTemplate(false);
                    enrollmentParams.setExportImage(ILVConstant.ID_COMPRESSION_WSQ, (byte) 15);

                    mFpReader.powerOn();
                    Thread.sleep(500);
                    mFpReader.enroll(enrollmentParams);
                } catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage());
                }
            }
        });
    }

    /**
     * Called when the user taps the Scan Smart Card button
     */
    public void scanSmartCard(View view) {
        powerOnSCR();
        displayLog(getResources().getString(R.string.insert_card));
    }

    /**
     * Called when a fingerprint event is received
     */
    @Override
    public void onFingerprintEvent(Method method, String data) {
        try {
            byte[] dataBytes = Base64.decode(data, Base64.NO_WRAP);
            switch (dataBytes[0]) {
                case ILVConstant.ILV_ENROLL:  // Enroll result
                    ImatchFPEnrollmentResult enroll_result = new ImatchFPEnrollmentResult(dataBytes);
                    FingerprintImage fpImageRaw = enroll_result.getFingerprintImage();
                    byte[] fpImageData = fpImageRaw.getImageData();
                    displayLog("Fingerprint data received: " + fpImageData.length);
                    if (saveWsqCheckbox.isChecked()) {
                        String tempfile = (String) android.text.format.DateFormat.format("yyyyMMddhhmmss", new java.util.Date());
                        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(Environment.getExternalStorageDirectory().getPath() + "/iMatchSample/" + tempfile + ".wsq"));
                        bos.write(fpImageData);
                        bos.flush();
                        bos.close();
                    }
                    try {
                        final Bitmap fpImage =  Jnbis.wsq().decode(fpImageData).asBitmap();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                photoImageView.setImageBitmap(fpImage);
                            }
                        });
                    }
                    catch (Exception e) {
                        Log.e(TAG, "WSQ to Bitmap conversion failed. Error: " + e.getMessage());
                        Log.d(TAG, e.getStackTrace().toString());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Drawable myDrawable = getResources().getDrawable(R.drawable.fingerprint_fail);
                                photoImageView.setImageDrawable(myDrawable);
                            }
                        });
                    }
                    break;

                case ILVConstant.ILV_ASYNC_MESSAGE:  // Async message
                    ILVAsyncMessage msg = null;
                    msg = new ILVAsyncMessage(dataBytes);
                    if (msg.getCommandType() == ILVAsyncMessage.MORPHO_CALLBACK_COMMAND_CMD) {
                        final String instructionText = msg.getCommandCmd().getInstructionText();
                        runOnUiThread(new Runnable() {
                            public void run() {
                                if (instructionText != null && !instructionText.equals("No finger detected")) {
                                    Log.d(TAG, instructionText);
                                    displayLog(instructionText);
                                }
                            }
                        });
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "onFingerprintEvent(): " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Called when a fingerprint error is received
     */
    @Override
    public void onFingerprintError(int code, String message) {
        Log.e(TAG, "Fingerprint Error: " + message);
        displayLog(message);
    }

    public void powerOnSCR() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean result = mScReader.getInstance().powerReaderOn("readKnownATRs");
                    if (result) {
                        Log.d(TAG,"powerOnSCR(): " +  result);
                    }
                    else {
                        Thread.sleep(1000);
                        powerOnSCR();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "powerOnSCR(): " + e.getLocalizedMessage());
                }
            }
        });
    }

    public void powerOffSCR() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean result = mScReader.getInstance().powerReaderOff();
                    if (result) {
                        Log.d(TAG,"powerOffSCR(): " +  result);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "powerOffSCR(): " + e.getLocalizedMessage());
                }
            }
        });
    }

    /**
     * Called when a smart card event is received
     */
    @Override
    public void onSmartCardEvent(Method method, final String data) {
        Log.d(TAG, "onSmartCardEvent():" + method + ": " + data);
        if (method == Method.NOTIFY) {
            byte[] atr = Base64.decode(data, Base64.NO_WRAP);
            final String hexATR = Utils.bytesToHex(atr);
            displayLog("ATR: " + hexATR);
        }
    }

    @Override
    public void onSmartCardError(int code, String message) {
        Log.e(TAG, "Smart card Error: " + message);
        displayLog(message);
    }

    /**
     * Called when an iMatch device is paired
     */
    public void DevicePaired() {
        outAnimation = new AlphaAnimation(1f, 0f);
        outAnimation.setDuration(200);
        progressBarHolder.setAnimation(outAnimation);
        progressBarHolder.setVisibility(View.GONE);
        connectButton.setEnabled(true);
        showToastInUiThread(this, "Connected to " + ImatchDevice.getInstance().GetName());
        scanFingerprintButton.setEnabled(true);
        scanSmartCardButton.setEnabled(true);
        scanMrzButton.setEnabled(documentReaderLicensed);
        monitorStatus();
        connectButton.setText(R.string.button_disconnect);
    }

    /**
     * Called when an iMatch device is paired
     */
    public void DeviceUnPaired() {
        scanMrzButton.setEnabled(false);
        scanFingerprintButton.setEnabled(false);
        scanSmartCardButton.setEnabled(false);

        batteryText.setVisibility(View.GONE);
        connectButton.setText(R.string.button_connect);
    }

    private void PairBluetooth() {
        if (!ImatchDevice.getInstance().Connected()) {
            ImatchManager.getInstance().Scan(2000);
        }
    }

    private void StopBluetooth() {
        if (ImatchDevice.getInstance().Connected()) {
            showToastInUiThread(this, "Disconnecting...");
            ImatchDevice.getInstance().Disconnect();
            DeviceUnPaired();
        }
    }

    private void Pair(String imatchBleName) {
        if (!ImatchDevice.getInstance().Connected()) {
            connectButton.setEnabled(false);
            inAnimation = new AlphaAnimation(0f, 1f);
            inAnimation.setDuration(200);
            progressBarHolder.setAnimation(inAnimation);
            progressBarHolder.setVisibility(View.VISIBLE);
            ImatchDevice.getInstance().Connect(imatchBleName);
        }
    }

    @Override
    public void onReceiveEvent(Method method, String data) {
        Log.d(TAG, "received event: " + data);
        if (method == Method.DATETIME && data.equals("1")) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DevicePaired();
                }
            });
        }
        if (method == Method.RESET && data.equals("1")) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DevicePaired();
                }
            });
        }
        if (method == Method.STATUS) {
            try {
                JSONObject jObject = new JSONObject(data);
                final double cv = (int) jObject.getDouble("cv");
                final boolean cs = Boolean.valueOf(jObject.getString("cs"));
                final double cl = jObject.getDouble("cl");
                Log.d(TAG, "CV: " + cv + " CS: " + cs + " CL: " + cl);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        batteryText.setVisibility(View.VISIBLE);
                        batteryText.setText(cv + " %");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    @Override
    public void onReceiveError(int code, String message) {
        Log.e(TAG, "received error: " + message);
    }

    /**
     * Called when a device is connected or disconnected
     */
    @Override
    public void onConnectionChange(Boolean connected) {
        Log.d(TAG, "onConnectionChange: " + connected);
        if (ImatchDevice.getInstance().Connected()) {
            Log.i(TAG, "Connected to " + ImatchDevice.getInstance().GetName() + " MAC: " + ImatchDevice.getInstance().GetMac());
            ImatchDevice.getInstance().SyncDate();
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DeviceUnPaired();
                }
            });
            showToastInUiThread(this, "Disconnected");
        }
    }


    @Override
    public void onInitSuccess() {
        Log.i(TAG, "onInitSuccess");
    }

    @Override
    public void onError(int code, String message) {
        Log.e(TAG, "iMatch Error received. Code: " + code + " Message: " + message);
        ImatchDevice.getInstance().Reset();
    }

    @Override
    public void onScanResult(final Map<String, String> scanResult) {
        final Context ctx = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for(Map.Entry<String, String> entry: scanResult.entrySet()) {
                    String name = entry.getKey();

                    leDeviceListAdapter.addDevice(entry.getKey());
                    leDeviceListAdapter.notifyDataSetInvalidated();
                }

                if (leDeviceListAdapter.isEmpty()) {
                    showToastInUiThread(ctx, getResources().getString(R.string.no_imatch));
                    return;
                }

                if (leDeviceListAdapter.getCount() > 0) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
                    builder.setTitle(getResources().getString(R.string.select_imatch)).setItems(leDeviceListAdapter.getDeviceNames(), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Pair(leDeviceListAdapter.getDeviceNames()[which]);
                        }
                    });
                    builder.show();
                }
            }
        });
    }

    /**
     * Called when the Document Reader decodes a MRZ code
     */
    private DocumentReader.DocumentReaderCompletion completion = new DocumentReader.DocumentReaderCompletion() {
        @Override
        public void onCompleted(int action, DocumentReaderResults results, String error) {
            if (action == DocReaderAction.COMPLETE) {
                if (results!=null)
                {
                    if(results.textResult != null && results.textResult.fields != null) {
                        for (DocumentReaderTextField textField : results.textResult.fields) {
                            String value = results.getTextFieldValueByType(textField.fieldType, textField.lcid);
                            switch (textField.fieldType)
                            {
                                case 172:
                                case 51:
                                    vizMrz = value;
                                    scanPassportButton.setEnabled(true);
                                    break;
                            }

                            Log.d("MainActivity", value + "\n");
                        }
                    }
                }
            } else {
                if(action==DocReaderAction.CANCEL){
                    Log.e(TAG, "DocReaderAction.CANCEL");
                    Toast.makeText(MainActivity.this, "Scanning cancelled",Toast.LENGTH_LONG).show();
                } else if(action == DocReaderAction.ERROR){
                    Log.e(TAG, "DocReaderAction.ERROR: " + error);
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    /**
     * Builds the BAC from the MRZ and starts a task to read the passport
     */
    private void readMRTD(final String vizMrz) {
        try {
            String mrz1 = "";
            String mrz2 = "";
            String bacMrz = "";
            Integer counter = 0;
            String[] parts = vizMrz.split("\\r?\\n", -1);
            for (String item : parts) {
                if (counter == 0) {
                    mrz1 = item;
                    counter = 1;
                } else if (counter == 1) {
                    mrz2 = item;
                    counter = 2;
                } else if (counter == 2) {
                    counter = 3;
                }
            }
            if (counter == 3) bacMrz = mrz1 + mrz2;
            else if (counter == 2) bacMrz = mrz2;
            if ("".equals(bacMrz)) bacMrz = vizMrz;
            new ReadMRTDTask(this, bacMrz).execute();
        } catch (Exception ex) {
            Log.e(TAG, ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Reads the passport with the BAC from the MRZ
     */
    private class ReadMRTDTask extends AsyncTask<Void, Void, Exception> {
        private String bacMrz;
        long start, stop, mark;

        ReadMRTDTask(Context context, String bacMrz) {
            this.bacMrz = bacMrz;
        }

        @Override
        protected Exception doInBackground(Void... voids) {
            try {
                displayLog("Start reading chip");
                readingChip = true;
                mark = System.currentTimeMillis();
                start = System.currentTimeMillis();

                // Initialize the passport read process on the iMatch
                String bypassPACE = "0";
                String checkMAC = "1";
                String includeHeaders = "1";
                String apduLogging = "0";
                String params = bacMrz + "," + bypassPACE + "," + checkMAC + "," + includeHeaders + "," + apduLogging;
                String bacResult = ImatchDevice.getInstance().SendWithResponse(Device.NfcReader, Method.MRTD_INIT, params);
                if (!bacResult.equals("1")) {
                    displayLog(String.format("BAC failed. %1$dms", System.currentTimeMillis() - mark));
                    throw new Exception("BAC failed");
                }

                displayLog(String.format("Performed ICAO BAC. %1$dms", System.currentTimeMillis() - mark));
                mark = System.currentTimeMillis();
                publishProgress();

                // Read DG2 (passport photo)
                String dg2Result = ImatchDevice.getInstance().SendWithResponse(Device.NfcReader, Method.READ, "DG2");
                Log.d(TAG, "DG2 Result: " + dg2Result);
                byte[] dg2Bytes = Base64.decode(dg2Result, Base64.NO_WRAP);

                String hex = Hex.encodeHex(dg2Bytes, true);
                String start = "FFD8FF";
                int startindex = 80;

                final String photoMimeType;
                if (hex.contains("FFD8FF")) {
                    startindex = (hex.indexOf(start)) / 2;
                    photoMimeType = "image/jpeg";
                } else
                    photoMimeType = "image/jp2";

                Log.d(TAG, "mime: photoMimeType: " + photoMimeType);
                displayLog(String.format("Read DG2. %1$dms", System.currentTimeMillis() - mark));
                mark = System.currentTimeMillis();
                publishProgress();

                final byte[] photoBytes = Arrays.copyOfRange(dg2Bytes, startindex, dg2Bytes.length);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new DecodeImageTask(photoBytes, photoMimeType).execute();
                    }
                });

                try {
                    // Read SOD (document security object)
                    final byte[] certBytes = Base64.decode(ImatchDevice.getInstance().SendWithResponse(Device.NfcReader, Method.READ, "SOD"), Base64.NO_WRAP);
                    displayLog(String.format("Read SecurityData. %1$dms", System.currentTimeMillis() - mark));
                    mark = System.currentTimeMillis();
                    publishProgress();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                X509Certificate cert = Utils.getDocSigningCertificate(certBytes);
                                displayLog("Issued by " + cert.getIssuerDN().getName() + ", expires " + cert.getNotAfter().toString());
                            } catch (Exception ecert) {
                                Log.e(TAG, "cert: " + ecert.getMessage());
                                ecert.printStackTrace();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage());
                    e.printStackTrace();
                }

                // Read DG1
                String dg1Mrz = ImatchDevice.getInstance().SendWithResponse(Device.NfcReader, Method.READ, "DG1");
                displayLog(String.format("Read DG1. %1$dms", System.currentTimeMillis() - mark));
                mark = System.currentTimeMillis();
                publishProgress();

                readingChip = false;
                return null;
            } catch (Exception e) {
                return e;
            }
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }

        @Override
        protected void onPostExecute(Exception result) {
            if (result == null) {
                stop = System.currentTimeMillis();
                displayLog(String.format("Finished reading document in %1$dms", stop - start));
            }
        }
    }

    private class DecodeImageTask extends AsyncTask<Void, Void, Exception> {
        private byte[] photoBytes;
        private String photoMimeType;
        private Bitmap photoBitmap;

        DecodeImageTask(byte[] photoBytes, String photoMimeType) {
            this.photoBytes = photoBytes;
            this.photoMimeType = photoMimeType;
        }

        @Override
        protected Exception doInBackground(Void... params) {
            try {
                photoBitmap = MrtdUtils.read(new ByteArrayInputStream(photoBytes), photoMimeType);
            } catch (IOException e) {
                Log.e(TAG, "DecodeImageTask: " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            if (result == null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        photoImageView.setImageBitmap(photoBitmap);
                    }
                });
            }
        }
    }

    /**
     * Monitors the status of the connected iMatch every 10 seconds
     */
    private void monitorStatus() {
        Thread task = new Thread() {
            @Override
            public void run() {
                while (ImatchDevice.getInstance().Connected() && activityOnTop && !readingChip) {
                    ImatchDevice.getInstance().RequestStatus();
                    try {
                        sleep(30000);
                    } catch (InterruptedException ignored) {}
                }
            }
        };
        task.start();
    }

    private void displayLog(final String message) {
        if (message == null || message.isEmpty()) return;
        Log.d(TAG, message);
        showToastInUiThread(this, message);
    }

    public static void showToastInUiThread(final Context ctx,
                                           final String message) {

        Handler mainThread = new Handler(Looper.getMainLooper());
        mainThread.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ctx, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    public void PermissionGranted(int request_code) {
        Log.i("PERMISSION","GRANTED");
    }

    public void PartialPermissionGranted(int request_code, ArrayList<String> granted_permissions) {
        Log.i("PERMISSION PARTIALLY","GRANTED");
    }

    public void PermissionDenied(int request_code) {
        Log.i("PERMISSION","DENIED");
    }

    public void NeverAskAgain(int request_code) {
        Log.i("PERMISSION","NEVER ASK AGAIN");
    }
}
