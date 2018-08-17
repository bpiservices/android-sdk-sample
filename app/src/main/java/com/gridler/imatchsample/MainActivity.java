package com.gridler.imatchsample;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.gridler.gubanilib.GubaniFingerPrintListener;
import com.gridler.gubanilib.MrtdUtils;
import com.gridler.gubanisdk.GubaniFPEnrollmentParams;
import com.gridler.gubanisdk.GubaniFPEnrollmentResult;
import com.gridler.gubanisdk.GubaniFingerprintReader;
import com.gridler.gubanisdk.Hex;
import com.gridler.gubanisdk.ILVAsyncMessage;
import com.gridler.gubanisdk.ILVConstant;
import com.regula.sdk.CaptureActivity;
import com.regula.sdk.DocumentReader;
import com.regula.sdk.results.TextField;

import com.gridler.gubanilib.Device;
import com.gridler.gubanilib.GubaniDevice;
import com.gridler.gubanilib.Method;
import com.gridler.gubanilib.PairingListener;
import com.gridler.gubanilib.PairingService;
import com.gridler.gubanilib.GubaniListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends ListActivity implements PairingListener, GubaniListener, GubaniFingerPrintListener, PermissionResultCallback {
    static String TAG = MainActivity.class.getSimpleName();
    GubaniFingerprintReader mFpReader;

    Button connectButton;
    Button scanPassportButton;
    Button scanMrzButton;
    Button scanFingerprintButton;
    ImageView photo;
    TextView batteryText;
    boolean documentReaderLicensed;

    String vizMrz;
    LeDeviceListAdapter leDeviceListAdapter;

    boolean activityOnTop = true;
    boolean readingChip = false;

    // list of permissions
    ArrayList<String> permissions=new ArrayList<>();
    PermissionUtils permissionUtils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permissionUtils = new PermissionUtils(this);

        //permissions.add(Manifest.permission.READ_PHONE_STATE);
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
        photo = findViewById(R.id.photo);

        // Initializes list view adapter
        leDeviceListAdapter = new LeDeviceListAdapter(MainActivity.this.getLayoutInflater());
        setListAdapter(leDeviceListAdapter);

        GubaniDevice.getInstance().AddListener(Device.Board, this);

        GubaniDevice.getInstance().PairingService.setListener(MainActivity.this);

        mFpReader = GubaniFingerprintReader.getInstance();
        mFpReader.setListener(this);
        checkDocumentReaderLicense();
    }

    public void checkDocumentReaderLicense() {
        // The MRZ scanning function requires a valid documentReader license. please visit:
        //    https://licensing.regulaforensics.com
        // to obtain a trial license.

        if (!documentReaderLicensed) {
            try {
                // Check if the Document Reader has been licensed
                InputStream licInput = getResources().openRawResource(R.raw.regula);
                byte[] license = new byte[licInput.available()];
                licInput.read(license);
                documentReaderLicensed = DocumentReader.setLibLicense(getApplicationContext(), license);
                licInput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (!documentReaderLicensed) {
            // Notify that the Document Reader license is not valid
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.strInformation);
            builder.setMessage(R.string.strInvalidDocumentReaderLicense);
            builder.setPositiveButton(R.string.strOK, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.show();
            return;
        }
    }

    /**
     * Called when the user taps the Connect button
     */
    public void connectDevice(View view) {
        if (GubaniDevice.getInstance().Connected) {
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
        Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
        MainActivity.this.startActivityForResult(intent, DocumentReader.READER_REQUEST_CODE);
    }

    /**
     * Called when the user taps the Scan Passport button
     */
    public void scanPassport(View view) {
        readMRTD(vizMrz);
    }

    /**
     * Called when the user taps the Scan Passport button
     */
    public void scanFingerprint(View view) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    GubaniFPEnrollmentParams enrollmentParams = new GubaniFPEnrollmentParams();
                    enrollmentParams.configAsynchronousEvent(GubaniFPEnrollmentParams.ASYNC_MSG_FINGER_POSITION | GubaniFPEnrollmentParams.ASYNC_MSG_ENROLLMENT_STEP);
                    // 0=default (3 recordings), 1 is one recording [0, 1, 3]
                    enrollmentParams.setEnrollmentType((byte) 1);
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
     * Called when a fingerprint event is received
     */
    @Override
    public void onFingerprintEvent(Method method, String data) {
        try {
            byte[] dataBytes = Base64.decode(data, Base64.NO_WRAP);
            switch (dataBytes[0]) {
                case 0x21:  // Enroll result
                    GubaniFPEnrollmentResult enroll_result = new GubaniFPEnrollmentResult(dataBytes);
                    byte[] fingerprintBytes = Base64.decode(String.valueOf(enroll_result), Base64.NO_WRAP);
                    Log.d(TAG, fingerprintBytes.toString());
                    displayLog("Fingerprint data received: " + dataBytes.length);
                    break;

                case 0x71:  // Async message
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

    /**
     * Called when an iMatch device is paired
     */
    public void DevicePaired() {
        scanFingerprintButton.setEnabled(true);
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

        batteryText.setVisibility(View.GONE);
        connectButton.setText(R.string.button_connect);
    }

    /**
     * Initializes the pairing service and starts scanning for devices
     */
    private void PairBluetooth() {
        if (!GubaniDevice.getInstance().Connected) {
            GubaniDevice.getInstance().PairingService.init(this.getApplicationContext());
            GubaniDevice.getInstance().PairingService.startScan(this.getApplicationContext(), 2000);
        }
    }

    /**
     * Stops the pairing service
     */
    private void StopBluetooth() {
        if (GubaniDevice.getInstance().Connected) {
            showToastInUiThread(this, "Disconnecting...");
            GubaniDevice.getInstance().PairingService.stop(this.getApplicationContext());
            DeviceUnPaired();
        }
    }

    /**
     * Called when a device is added
     */
    @Override
    public void addDevice(BluetoothDevice dev) {
        final BluetoothDevice _dev = dev;
        Log.d(TAG, "Device added: " + _dev.getName());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                leDeviceListAdapter.addDevice(_dev);
                leDeviceListAdapter.notifyDataSetInvalidated();
            }
        });
    }

    @Override
    public void startActivityForResult() {
        Log.d(TAG, "startActivityForResult!");
        this.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1);
    }

    /**
     * Called when a device has been discovered and can be paired
     */
    @Override
    public void pairDeviceSelector() {
        if (leDeviceListAdapter.isEmpty()) {
            showToastInUiThread(this, getResources().getString(R.string.no_imatch));
            return;
        }

        GubaniDevice.getInstance().DeviceName = leDeviceListAdapter.getDevice(0).getName();
        GubaniDevice.getInstance().DeviceAddress = leDeviceListAdapter.getDevice(0).getAddress();
        if (leDeviceListAdapter.getCount() > 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getString(R.string.select_imatch)).setItems(leDeviceListAdapter.getDeviceNames(), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    GubaniDevice.getInstance().DeviceName = leDeviceListAdapter.getDevice(which).getName();
                    GubaniDevice.getInstance().DeviceAddress = leDeviceListAdapter.getDevice(which).getAddress();
                    Pair();
                }
            });
            builder.show();
        }
    }

    private void Pair() {
        if (GubaniDevice.getInstance().DeviceName != null && GubaniDevice.getInstance().DeviceAddress != null) {
            GubaniDevice.getInstance().Init(this, GubaniDevice.getInstance().DeviceName, GubaniDevice.getInstance().DeviceAddress);
        }
    }

    @Override
    public void onReceiveEvent(Method method, String data) {
        Log.d(TAG, "received event: " + data);
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
        if (GubaniDevice.getInstance().Connected) {
            String name = GubaniDevice.getInstance().DeviceName;
            if (!name.contains("-") && GubaniDevice.getInstance().DeviceAddress != null && GubaniDevice.getInstance().DeviceAddress.length() > 0) {
                String devId = GubaniDevice.getInstance().DeviceAddress.replace(":", "");
                if (devId.length() == 12) {
                    name += "-" + devId.substring(8);
                }
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DevicePaired();
                    try {
                        GubaniDevice.getInstance().Send(Device.Board, Method.RESET, "");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
            showToastInUiThread(this, "Connected to " + name);

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
    public void onResume() {
        activityOnTop = true;
        super.onResume();
    }

    @Override
    public void onPause() {
        activityOnTop = false;
        super.onPause();
    }

    public void onDestroy() {
        try {
            GubaniDevice.getInstance().Disconnect(this);
        } catch (Exception e) {
            Log.e(TAG, "Disconnect Service failed! " + e.getLocalizedMessage());
        }

        super.onDestroy();
        this.finish();
        Log.d(TAG, "Application destroyed");
    }

    /**
     * Called when the Document Reader decodes a MRZ code
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == DocumentReader.READER_REQUEST_CODE) {
                List<TextField> mResultItems = new ArrayList<>();
                mResultItems.addAll(DocumentReader.getAllTextFields());
                for (int i = 0; i < mResultItems.size(); i++) {
                    TextField tf = mResultItems.get(i);
                    Log.i(TAG, "MRZ FIELD: " + tf.fieldType + " --> " + tf.bufText);
                    switch (tf.fieldType) {
                        case 0: // DocType
                            break;
                        case 1: // Nationality
                            break;
                        case 2: // DocNumber
                            break;
                        case 3: // ExpiryDate
                            break;
                        case 5:// BirthDate
                            break;
                        case 7: // PersonalNumber
                            break;
                        case 8: // LastName;
                            break;
                        case 9: // FirstName
                            break;
                        case 12: // Gender
                            break;
                        case 26: // Country
                            break;
                        case 172: // MRZ
                            vizMrz = tf.bufText;
                            scanPassportButton.setEnabled(true);
                            break;
                        case 51: // MRZ
                            vizMrz = tf.bufText;
                            scanPassportButton.setEnabled(true);
                            break;
                    }
                }
            }
        }
    }

    /**
     * Builds the BAC from the MRZ and starts a task to read the passport
     */
    private void readMRTD(final String vizMrz) {
        try {
            String mrz1 = "";
            String mrz2 = "";
            String bacMrz = "";
            Integer counter = 0;
            String[] parts = vizMrz.split("\\^", -1);
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
                String bacResult = GubaniDevice.getInstance().SendWithResponse(Device.NfcReader, Method.MRTD_INIT, this.bacMrz);
                if (bacResult.equals("1")) {
                    displayLog(String.format("Performed ICAO BAC. %1$dms", System.currentTimeMillis() - mark));
                    mark = System.currentTimeMillis();
                    publishProgress();

                    // Read DG2 (passport photo)
                    String dg2Result = GubaniDevice.getInstance().SendWithResponse(Device.NfcReader, Method.READ, "DG2");
                    Log.d(TAG, "DG2 Result: " + dg2Result);
                    byte[] photoBytes = Base64.decode(dg2Result, Base64.NO_WRAP);

                    String hex = Hex.encodeHex(photoBytes, true);
                    String start = "FFD8FF";
                    int startindex = 80;

                    String photoMimeType;
                    if (hex.contains("FFD8FF")) {
                        startindex = (hex.indexOf(start)) / 2;
                        photoMimeType = "image/jpeg";
                    } else
                        photoMimeType = "image/jp2";

                    Log.d(TAG, "mime: photoMimeType: " + photoMimeType);
                    displayLog(String.format("Read DG2. %1$dms", System.currentTimeMillis() - mark));
                    mark = System.currentTimeMillis();
                    publishProgress();
                    new DecodeImageTask(Arrays.copyOfRange(photoBytes, startindex, photoBytes.length), photoMimeType).execute();

                    try {
                        // Read SOD (document security object)
                        final byte[] certBytes = Base64.decode(GubaniDevice.getInstance().SendWithResponse(Device.NfcReader, Method.READ, "SOD"), Base64.NO_WRAP);
                        displayLog(String.format("Read SecurityData. %1$dms", System.currentTimeMillis() - mark));
                        mark = System.currentTimeMillis();
                        publishProgress();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                                    InputStream in = new ByteArrayInputStream(certBytes);
                                    X509Certificate cert = (X509Certificate) certFactory.generateCertificate(in);
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
                    String dg1Mrz = GubaniDevice.getInstance().SendWithResponse(Device.NfcReader, Method.READ, "DG1");
                    displayLog(String.format("Read DG1. %1$dms", System.currentTimeMillis() - mark));
                    mark = System.currentTimeMillis();
                    publishProgress();

                    readingChip = false;
                }
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
                        photo.setImageBitmap(photoBitmap);
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
                while (GubaniDevice.getInstance().Connected && activityOnTop && !readingChip) {
                    final double cv = getStatusInfo();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            batteryText.setVisibility(View.VISIBLE);
                            batteryText.setText(cv + " %");
                        }
                    });
                    try {
                        sleep(10000);
                    } catch (InterruptedException ignored) {

                    }
                }
            }
        };

        task.start();
    }

    /**
     * Gets the status of the connected iMatch (battery level, etc.)
     */
    public static double getStatusInfo() {

        try {
            String GaugeResult = GubaniDevice.getInstance().SendWithResponse(Device.Board, Method.STATUS, "data");
            Log.d(TAG, "sendWithResponse: data: " + GaugeResult);
            JSONObject jObject = new JSONObject(GaugeResult);
            double cv = (int) jObject.getDouble("cv");
            boolean cs = Boolean.valueOf(jObject.getString("cs"));
            double cl = jObject.getDouble("cl");
            Log.d(TAG, "CV: " + cv + " CS: " + cs + " CL: " + cl);

            return cv;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        return 0;
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
