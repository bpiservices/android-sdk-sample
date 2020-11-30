package com.gridler.imatchsample;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
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

import androidx.core.app.ActivityCompat;

import com.gridler.imatchlib.FingerQuality;
import com.gridler.imatchlib.ImageType;
import com.gridler.imatchlib.ImatchFingerPrintListener;
import com.gridler.imatchlib.ImatchManagerListener;
import com.gridler.imatchlib.ImatchSmartCardListener;
import com.gridler.imatchlib.Utils;
import com.gridler.imatchsdk.FingerprintImage;
import com.gridler.imatchsdk.ImatchFPEnrollmentParams;
import com.gridler.imatchsdk.ImatchFPEnrollmentResult;
import com.gridler.imatchsdk.ImatchFingerprintReader;
import com.gridler.imatchsdk.ILVAsyncMessage;
import com.gridler.imatchsdk.ILVConstant;
import com.gridler.imatchsdk.ImatchManager;

import com.gridler.imatchlib.Device;
import com.gridler.imatchlib.ImatchDevice;
import com.gridler.imatchlib.Method;
import com.gridler.imatchlib.ImatchListener;
import com.gridler.imatchsdk.ImatchSmartcardReader;
import com.regula.documentreader.api.DocumentReader;
import com.regula.documentreader.api.completions.IDocumentReaderCompletion;
import com.regula.documentreader.api.completions.IDocumentReaderInitCompletion;
import com.regula.documentreader.api.enums.DocReaderAction;
import com.regula.documentreader.api.results.DocumentReaderResults;
import com.regula.documentreader.api.results.DocumentReaderTextField;

import org.jmrtd.Util;
import org.jnbis.api.Jnbis;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Map;

import eu.bpiservices.idreadersdk.CertValidator;
import eu.bpiservices.idreadersdk.MrtdUtils;
import eu.bpiservices.idreadersdk.ReadProcess;
import eu.bpiservices.idreadersdk.ReadResponse;
import eu.bpiservices.idreadersdk.ReadTask;

import static eu.bpiservices.idreadersdk.Utils.READ_MRTD_FILE_ALL_CODE;
import static eu.bpiservices.idreadersdk.Utils.getBouncyCastleProvider;

public class MainActivity extends ListActivity implements ImatchManagerListener, ImatchListener, ImatchFingerPrintListener, ImatchSmartCardListener, PermissionResultCallback, ReadResponse, ReadProcess {
    static String TAG = MainActivity.class.getSimpleName();
    ImatchFingerprintReader mFpReader;
    ImatchSmartcardReader mScReader;

    Button connectButton;
    Button readPassportNativeButton;
    Button readPassportIDReaderButton;
    Button readPassportIDReaderNFCButton;
    Button scanMrzButton;
    Button scanFingerprintButton;
    Button scanIBFingerprintButton;
    Button scanSmartCardButton;
    boolean scanIBFingerprint;
    boolean poweredIBFingerprint;
    CheckBox saveWsqCheckbox;
    ImageView photoImageView;
    ImageView fp1ImageView;
    ImageView fp2ImageView;
    TextView batteryText;
    boolean documentReaderLicensed;
    String vizMrz;
    LeDeviceListAdapter leDeviceListAdapter;
    AlphaAnimation inAnimation;
    AlphaAnimation outAnimation;
    FrameLayout progressBarHolder;

    boolean activityOnTop = true;
    boolean readingChip = false;

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
        readPassportNativeButton = findViewById(R.id.readPassportNativeButton);
        readPassportIDReaderButton = findViewById(R.id.readPassportIDReaderButton);
        readPassportIDReaderNFCButton = findViewById(R.id.readPassportIDReaderNFCButton);
        scanMrzButton = findViewById(R.id.scanMrzButton);
        scanFingerprintButton = findViewById(R.id.scanFingerprintButton);
        scanIBFingerprintButton = findViewById(R.id.scanIBFingerprint);
        scanSmartCardButton = findViewById(R.id.scanSmartCardButton);
        saveWsqCheckbox = findViewById(R.id.saveWsqCheckbox);
        photoImageView = findViewById(R.id.photo);
        fp1ImageView = findViewById(R.id.fp1);
        fp2ImageView = findViewById(R.id.fp2);
        progressBarHolder = findViewById(R.id.progressBarHolder);

        // Initializes list view adapter
        leDeviceListAdapter = new LeDeviceListAdapter(MainActivity.this.getLayoutInflater());
        setListAdapter(leDeviceListAdapter);

        ImatchDevice.getInstance().AddListener(Device.Board, this);
        ImatchSmartcardReader.getInstance().setListener(this);

        mFpReader = ImatchFingerprintReader.getInstance();
        mFpReader.setListener(this);
        checkDocumentReaderLicense();

        CertValidator.getInstance().Init(this);

        checkStoragePermission();
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

                //Initializing the reader
                DocumentReader.Instance().initializeReader(MainActivity.this, license, new IDocumentReaderInitCompletion() {
                    @Override
                    public void onInitCompleted(boolean success, Throwable throwable) {
                        DocumentReader.Instance().customization().edit().setShowResultStatusMessages(true).apply();
                        DocumentReader.Instance().customization().edit().setShowStatusMessages(true).apply();;
                        DocumentReader.Instance().functionality().edit().setVideoCaptureMotionControl(true).apply();;

                        documentReaderLicensed = success;

                        if (documentReaderLicensed) {
                            DocumentReader.Instance().processParams().scenario = "Mrz";
                        }
                        else {
                            // Notify that the Document Reader license is not valid
                            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                            builder.setTitle(R.string.Information);
                            builder.setMessage(R.string.InvalidDocumentReaderLicense + ": " + throwable.getMessage());
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
        DocumentReader.Instance().showScanner(this, completion);
    }

    /**
     * Called when the user taps the Read Passport Native button
     */
    public void readPassportNative(View view) {
        new ReadPassportNativeTask().execute();
    }

    /**
     * Called when the user taps the Read Passport ID Reader button
     */
    public void readPassportIDReader(View view) {
        ImatchCardService iMatchCardService = new ImatchCardService(ImatchDevice.getInstance());
        ReadTask readTask = new ReadTask(MainActivity.this, vizMrz, READ_MRTD_FILE_ALL_CODE, this);
        readTask.setCardService(iMatchCardService);
        readTask.setBypassPace(true);
        readTask.setApduLogging(true);
        readTask.execute();
    }

    /**
     * Called when the user taps the Read Passport ID Reader NFC button
     */
    public void readPassportIDReaderNFC(View view) {
        ReadTask readTask = new ReadTask(MainActivity.this, vizMrz, READ_MRTD_FILE_ALL_CODE, this);
        readTask.setApduLogging(true);

        File rootPath = Environment.getExternalStorageDirectory();
        String keystorePath = "Android/data/eu.bpiservices/terminalCertificates/";
        String issuer = "";
        readTask.setChipAuthentication(true);
        try {
            readTask.setTerminalAuthentication(rootPath, keystorePath, issuer, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        readTask.execute();
    }

    /**
     * Called when the user taps the Read Fingerprint button
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
                            photoImageView.setVisibility(View.VISIBLE);
                            fp1ImageView.setVisibility(View.GONE);
                            fp2ImageView.setVisibility(View.GONE);
                        }
                    });

                    ImatchFPEnrollmentParams enrollmentParams = new ImatchFPEnrollmentParams();
                    enrollmentParams.configAsynchronousEvent(ImatchFPEnrollmentParams.ASYNC_MSG_FINGER_POSITION | ImatchFPEnrollmentParams.ASYNC_MSG_ENROLLMENT_STEP);
                    enrollmentParams.setConsolidation((byte) 1);
                    enrollmentParams.setExportTemplate(false);
                    enrollmentParams.setExportImage(ILVConstant.ID_COMPRESSION_WSQ, (byte) 15);

                    scanIBFingerprint = false;
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
     * Called when the user taps the Scan IB Fingerprint button
     */
    public void scanIBFingerprint(View view) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Drawable myDrawable = getResources().getDrawable(R.drawable.fingerprint);
                            fp1ImageView.setImageDrawable(myDrawable);
                            fp2ImageView.setImageDrawable(myDrawable);
                            fp1ImageView.setVisibility(View.VISIBLE);
                            fp2ImageView.setVisibility(View.VISIBLE);
                            photoImageView.setVisibility(View.GONE);
                        }
                    });

                    if (poweredIBFingerprint) {
                        Log.d(TAG, "FingerprintEvent debug : enrolling");
                        mFpReader.enroll(ImageType.FLAT_TWO_FINGERS);
                    } else {
                        Log.d(TAG, "FingerprintEvent debug : powering on");
                        scanIBFingerprint = true;
                        mFpReader.powerOn(); // wait for callback from power on function
                    }
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
            Log.d(TAG, "onFingerprintEvent method: " + method);
            Log.d(TAG, "onFingerprintEvent data: " + data);

            if (method == Method.POWERON) {
                if (scanIBFingerprint) {
                    poweredIBFingerprint = true;
                    mFpReader.enroll(ImageType.FLAT_TWO_FINGERS);
                }
            }

            final byte[] dataBytes = Base64.decode(data, Base64.NO_WRAP);

            if (method == Method.FP_QUALITY) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        FingerQuality fp1Quality = FingerQuality.values()[dataBytes[0]];
                        FingerQuality fp2Quality = FingerQuality.values()[dataBytes[1]];

                        Log.e(TAG, "fp1Quality: " + fp1Quality);
                        Log.e(TAG, "fp2Quality: " + fp2Quality);

                        if (fp1Quality == FingerQuality.NOT_PRESENT) {
                            fp1ImageView.setImageDrawable(getResources().getDrawable(R.drawable.fingerprint));
                        }
                        if (fp1Quality == FingerQuality.POOR) {
                            fp1ImageView.setImageDrawable(getResources().getDrawable(R.drawable.fingerprint_orange));
                        }
                        if (fp1Quality == FingerQuality.FAIR) {
                            fp1ImageView.setImageDrawable(getResources().getDrawable(R.drawable.fingerprint_yellow));
                        }
                        if (fp1Quality == FingerQuality.GOOD) {
                            fp1ImageView.setImageDrawable(getResources().getDrawable(R.drawable.fingerprint_green));
                        }

                        if (fp2Quality == FingerQuality.NOT_PRESENT) {
                            fp2ImageView.setImageDrawable(getResources().getDrawable(R.drawable.fingerprint));
                        }
                        if (fp2Quality == FingerQuality.POOR) {
                            fp2ImageView.setImageDrawable(getResources().getDrawable(R.drawable.fingerprint_orange));
                        }
                        if (fp2Quality == FingerQuality.FAIR) {
                            fp2ImageView.setImageDrawable(getResources().getDrawable(R.drawable.fingerprint_yellow));
                        }
                        if (fp2Quality == FingerQuality.GOOD) {
                            fp2ImageView.setImageDrawable(getResources().getDrawable(R.drawable.fingerprint_green));
                        }
                    }
                });
            }

            if (method == Method.FP_IMAGE) {
                displayLog("Power off and fingerprint data received: " + dataBytes.length);
                mFpReader.powerOff();
                poweredIBFingerprint = false;

                if (saveWsqCheckbox.isChecked()) {
                    String tempfile = (String) android.text.format.DateFormat.format("yyyyMMddhhmmss", new java.util.Date());
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(Environment.getExternalStorageDirectory().getPath() + "/iMatchSample/" + tempfile + ".wsq"));
                    bos.write(dataBytes);
                    bos.flush();
                    bos.close();
                }
                try {
                    final Bitmap fpImage =  Jnbis.wsq().decode(dataBytes).asBitmap();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            photoImageView.setImageBitmap(fpImage);
                            photoImageView.setVisibility(View.VISIBLE);
                            fp1ImageView.setVisibility(View.GONE);
                            fp2ImageView.setVisibility(View.GONE);
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
            }

            if (method == Method.NOTIFY) {
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
                            final Bitmap fpImage = Jnbis.wsq().decode(fpImageData).asBitmap();

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    photoImageView.setImageBitmap(fpImage);
                                }
                            });
                        } catch (Exception e) {
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
        scanIBFingerprintButton.setEnabled(true);
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
        readPassportNativeButton.setEnabled(false);
        readPassportIDReaderButton.setEnabled(false);
        scanFingerprintButton.setEnabled(false);
        scanIBFingerprintButton.setEnabled(false);
        scanSmartCardButton.setEnabled(false);

        batteryText.setVisibility(View.GONE);
        connectButton.setText(R.string.button_connect);
        poweredIBFingerprint = false;
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
        Log.d(TAG, "received event: " + method + ": " + data);
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
        if (method.equals(Method.INFO)) {
            String sdkFirmwareVersion = ImatchDevice.getInstance().GetSdkFirmwareVersion(this);
            Log.d(TAG, "sdkFirmwareVersion: " + sdkFirmwareVersion);
            if (!ImatchDevice.getInstance().GetFirmwareVersion().equals(sdkFirmwareVersion)) {
                FirmwareUpdate firmwareUpdate = new FirmwareUpdate();
                firmwareUpdate.showFirmwareDialog(MainActivity.this);
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
        if (connected && ImatchDevice.getInstance().Connected()) {
            Log.i(TAG, "Connected to " + ImatchDevice.getInstance().GetName() + " MAC: " + ImatchDevice.getInstance().GetMac());

            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        ImatchDevice.getInstance().SyncDate();
                        Thread.sleep(1024);
                        ImatchDevice.getInstance().RequestDeviceInfo();
                    }
                    catch (Exception e)
                    {
                        Log.e(TAG, e.getMessage());
                    }
                }
            });
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
    private IDocumentReaderCompletion completion = new IDocumentReaderCompletion() {
        @Override
        public void onCompleted(int action, DocumentReaderResults results, Throwable throwable) {
            if (action == DocReaderAction.COMPLETE) {
                if (results == null) {
                    return;
                }
                if (results.textResult == null || results.textResult.fields == null) {
                    return;
                }

                for (DocumentReaderTextField textField : results.textResult.fields) {
                    String value = results.getTextFieldValueByType(textField.fieldType, textField.lcid);
                    switch (textField.fieldType) {
                        case 172:
                        case 51:
                            vizMrz = value;

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    photoImageView.setImageDrawable(null);
                                }
                            });

                            readPassportNativeButton.setEnabled(true);
                            readPassportIDReaderButton.setEnabled(true);
                            readPassportIDReaderNFCButton.setEnabled(true);
                            break;
                    }
                }
            } else if (action == DocReaderAction.CANCEL) {
                Log.e(TAG, "DocReaderAction.CANCEL");
                Toast.makeText(MainActivity.this, "Scanning cancelled", Toast.LENGTH_LONG).show();
            } else if (action == DocReaderAction.ERROR) {
                Log.e(TAG, "DocReaderAction.ERROR: " + throwable.getMessage());
                Toast.makeText(MainActivity.this, throwable.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    public byte[] signTA(String sigAlg, byte[] dtbsBytes) {
        Provider bouncyCastleProvider = getBouncyCastleProvider();
        Signature sig = null;

        PrivateKey terminalKey = getKey(sigAlg);
        try {
            sig = Signature.getInstance(sigAlg, bouncyCastleProvider);
            sig.initSign(terminalKey);
            sig.update(dtbsBytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            e.printStackTrace();
        }

        byte[] signedData = new byte[0];
        try {
            signedData = sig.sign();

            if (sigAlg.toUpperCase().endsWith("ECDSA")) {
                int keySize = (int)Math.ceil(((org.bouncycastle.jce.interfaces.ECPrivateKey)terminalKey).getParameters().getCurve().getFieldSize() / 8.0);
                try {
                    signedData = Util.getRawECDSASignature(signedData, keySize);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (SignatureException e) {
            e.printStackTrace();
        }

        return signedData;
    }

    private PrivateKey getKey(String sigAlg) {
        return null;
    }

    /**
     * Reads the passport with the BAC from the MRZ
     */
    private class ReadPassportNativeTask extends AsyncTask<Void, Void, Exception> {
        ReadPassportNativeTask() {
        }

        @Override
        protected Exception doInBackground(Void... voids) {
            try {
                displayLog("Start reading chip");
                readingChip = true;

                // Initialize the passport read process on the iMatch
                String bypassPACE = "0";
                String checkMAC = "1";
                String includeHeaders = "1";
                String apduLogging = "0";

                String formattedMrz = Utils.formatMrz(vizMrz);
                String params = formattedMrz + "," + bypassPACE + "," + checkMAC + "," + includeHeaders + "," + apduLogging;
                String accessResult = ImatchDevice.getInstance().SendWithResponse(Device.NfcReader, Method.MRTD_INIT, params);
                if (!accessResult.equals("1")) {
                    displayLog("Access control failed.");
                    throw new Exception("Access control failed");
                }

                displayLog("Performed access control.");
                publishProgress();

                // Read DG2 (passport photo)
                String dg2Result = ImatchDevice.getInstance().SendWithResponse(Device.NfcReader, Method.READ, "DG2");
                Log.d(TAG, "DG2 Result: " + dg2Result);
                final byte[] dg2Bytes = Base64.decode(dg2Result, Base64.NO_WRAP);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String photoMimeType;
                        int startIndex = Utils.indexOf(dg2Bytes, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
                        if (startIndex > 0) {
                            photoMimeType = "image/jpeg";
                        } else {
                            startIndex = Utils.indexOf(dg2Bytes, new byte[]{0x00, 0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, 0x20, 0x20, 0x0D, 0x0A, (byte) 0x87, 0x0A}) + 1;
                            photoMimeType = "image/jp2";
                        }

                        byte[] photoBytes = Utils.subbytes(dg2Bytes, startIndex);

                        try {
                            Bitmap photoBitmap = eu.bpiservices.idreadersdk.MrtdUtils.read(new ByteArrayInputStream(photoBytes), photoMimeType);
                            photoImageView.setVisibility(View.VISIBLE);
                            photoImageView.setImageBitmap(photoBitmap);
                        } catch (IOException e) {
                            Log.e(TAG, "DecodeImageTask: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                });

                try {
                    // Read SOD (document security object)
                    final byte[] certBytes = Base64.decode(ImatchDevice.getInstance().SendWithResponse(Device.NfcReader, Method.READ, "SOD"), Base64.NO_WRAP);
                    displayLog("Read SecurityData.");
                    publishProgress();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                                InputStream inputStream = new ByteArrayInputStream(certBytes);
                                X509Certificate cert = (X509Certificate)certFactory.generateCertificate(inputStream);
                                displayLog("Issued by " + cert.getIssuerDN().getName() + ", expires " + cert.getNotAfter().toString());
                                checkCertificateChain(cert);
                            } catch (Exception e) {
                                Log.e(TAG, "cert: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage());
                    e.printStackTrace();
                }

                // Read DG1
                String dg1Mrz = ImatchDevice.getInstance().SendWithResponse(Device.NfcReader, Method.READ, "DG1");
                displayLog("Read DG1.");
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
                displayLog("Finished reading document");
            }
        }
    }

    private void checkCertificateChain(final X509Certificate cert) {
        PKIXCertPathValidatorResult certCheck;
        try {
            if (CertValidator.getInstance().isReady()) {
                certCheck = CertValidator.getInstance().CheckCertificateChain(cert.getEncoded());
                displayLog("Valid certificate - CA name: " + certCheck.getTrustAnchor().toString());
            }
        } catch (CertificateEncodingException e) {
            displayLog("Certificate error: " + e.getMessage());
        } catch (CertPathValidatorException e) {
            displayLog("Certificate error: " + e.getMessage());
        }
    }

    @Override
    public void readCompleted(final eu.bpiservices.idreadersdk.ReadResult readResult) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "ResultCode: " + readResult.ResultCode);

                if (readResult.Cancelled) {
                    return;
                }

                try {
                    Log.d(TAG, "COM LDS Version: " + readResult.COM.getLDSVersion());
                    Log.d(TAG, "COM TAG List: " + readResult.COM.getTagList());

                    Log.d(TAG, "SOD LDS Version: " + readResult.SOD.getLDSVersion());
                    Log.d(TAG, "SOD Digest Algorithm: " + readResult.SOD.getDigestAlgorithm());
                    Log.d(TAG, "SOD Signer Info Digest Algorithm: " + readResult.SOD.getSignerInfoDigestAlgorithm());

                    try{
                        readResult.SOD.getDocSigningCertificate().checkValidity();
                        MrtdUtils.verifySod(readResult.SOD.getEncoded());
                        Log.d(TAG, "SOD verified");
                    } catch (Exception e) {
                        Log.d(TAG, "SOD verification error: " + e.getMessage());
                    }

                    Map<Integer, byte[]> dataGroupHashes = readResult.SOD.getDataGroupHashes();
                    for (Integer key : dataGroupHashes.keySet()) {
                        Log.d(TAG, "DG " + key + " hash: " + Utils.bytesToHex(dataGroupHashes.get(key)));
                    }

                    Log.d(TAG, "DG1 MRZ Info: " + readResult.DG1.getMRZInfo());

                    Boolean dg1Valid = MrtdUtils.validateHash(readResult.SOD, readResult.DG1);
                    Log.d(TAG, "DG1 hash matches: " + dg1Valid);

                    Log.d(TAG, "DG2 face biometric encodings: " + readResult.DG2.getFaceInfos().size());

                    Boolean dg2Valid = MrtdUtils.validateHash(readResult.SOD, readResult.DG2);
                    Log.d(TAG, "DG2 hash matches: " + dg2Valid);

                    if (readResult.DG3 != null ) {
                        Log.d(TAG, "DG3 face biometric encodings: " + readResult.DG3.getFingerInfos().size());

                        Boolean dg3Valid = MrtdUtils.validateHash(readResult.SOD, readResult.DG3);
                        Log.d(TAG, "DG3 hash matches: " + dg3Valid);
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            photoImageView.setVisibility(View.VISIBLE);
                            photoImageView.setImageBitmap(readResult.PhotoBitmap);
                        }
                    });

                    X509Certificate cert = MrtdUtils.getDocSigningCertificate(readResult.SOD.getEncoded());
                    displayLog("Issued by " + cert.getIssuerDN().getName() + ", expires " + cert.getNotAfter().toString());
                    checkCertificateChain(cert);

                    //MrtdUtils.CRLCheck(cert, x509CRL);

                } catch (Exception e) {
                    Log.e(TAG, "cert: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void readProgress(eu.bpiservices.idreadersdk.ReadStep step, long timestamp, String info) {
        Log.d(TAG, "readProgress: " + step + ", " + info);
        displayLog("readProgress: " + step);
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

    public static void showToastInUiThread(final Context ctx, final String message) {
        Handler mainThread = new Handler(Looper.getMainLooper());
        mainThread.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ctx, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    public boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG, "Permission is granted");
                return true;
            } else {

                Log.v(TAG, "Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        } else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG, "Permission is granted");
            return true;
        }
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
