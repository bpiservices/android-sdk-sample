package com.gridler.imatchsample;

import android.util.Log;

import com.gridler.imatchlib.Device;
import com.gridler.imatchlib.ImatchDevice;
import com.gridler.imatchlib.Method;
import com.gridler.imatchlib.Utils;

import net.sf.scuba.smartcards.APDUEvent;
import net.sf.scuba.smartcards.CardService;
import net.sf.scuba.smartcards.CardServiceException;
import net.sf.scuba.smartcards.CommandAPDU;
import net.sf.scuba.smartcards.ResponseAPDU;

public class ImatchCardService extends CardService {

    private final static String TAG = ImatchCardService.class.getSimpleName();

    private ImatchDevice imatchDevice;
    private int apduCount;

    /**
     * Constructs a new card service.
     *
     * @param imatch the card terminal to connect to
     */
    public ImatchCardService(ImatchDevice imatch) {
        this.imatchDevice = imatch;
        apduCount = 0;
    }

    /**
     * Opens a session with the card.
     */
    public void open() {
        if (isOpen()) {
            state = SESSION_STARTED_STATE;
        }
    }

    /**
     * Whether there is an open session with the card.
     */
    public boolean isOpen() {
        try {
            if (state == SESSION_STARTED_STATE) {
                Log.d(TAG, "isOpen already open");
                return true;
            }
            String powerOnResult = imatchDevice.SendWithResponse(Device.NfcReader, Method.POWERONRAW, "");
            if ("1".equals(powerOnResult)) {
                Log.d(TAG, "isOpen sent power on");
                state = SESSION_STARTED_STATE;
                return true;
            }
            Log.d(TAG, "isOpen power on failed");
            state = SESSION_STOPPED_STATE;
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Power on failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sends an APDU to the card.
     *
     * @param ourCommandAPDU the command apdu to send
     * @return the response from the card, including the status word
     * @throws CardServiceException - if the card operation failed
     */
    public ResponseAPDU transmit(CommandAPDU ourCommandAPDU) throws CardServiceException {
        try {
            if (state == SESSION_STOPPED_STATE) {
                throw new CardServiceException("No session started");
            }

            byte[] responseBytes = imatchDevice.SendBytesWithResponse(ourCommandAPDU.getBytes());
            if (responseBytes == null || responseBytes.length < 2) {
                throw new CardServiceException("Failed response");
            }

            ResponseAPDU ourResponseAPDU = new ResponseAPDU(responseBytes);
            APDUEvent event = new APDUEvent(this, "RAW", ++apduCount, ourCommandAPDU, ourResponseAPDU);
            notifyExchangedAPDU(event);
            return ourResponseAPDU;
        } catch (Exception e) {
            throw new CardServiceException(e.getMessage());
        }
    }

    public byte[] getATR() {
        return null; // FIXME
    }

    public boolean isExtendedAPDULengthSupported() {
        return true;
    }

    /**
     * Closes the session with the card.
     */
    public void close() {
        try {
            if (state != SESSION_STOPPED_STATE) {
                imatchDevice.SendBytes(Utils.hexStringToByteArray("0002DEAD"));
                state = SESSION_STOPPED_STATE;
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isConnectionLost(Exception e) {
        return false;
    }
}
