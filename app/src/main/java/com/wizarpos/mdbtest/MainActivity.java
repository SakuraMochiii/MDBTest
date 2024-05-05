package com.wizarpos.mdbtest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.cloudpos.DeviceException;
import com.cloudpos.OperationListener;
import com.cloudpos.OperationResult;
import com.cloudpos.POSTerminal;
import com.cloudpos.TimeConstants;
import com.cloudpos.card.Card;
import com.cloudpos.jniinterface.SerialPortInterface;
import com.cloudpos.jniinterface.Stm32ispInterface;
import com.cloudpos.rfcardreader.RFCardReaderDevice;
import com.cloudpos.rfcardreader.RFCardReaderOperationResult;
import com.cloudpos.smartcardreader.SmartCardReaderDevice;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends Activity implements View.OnClickListener {
    private TextView textView, progress_textView;
    private EditText input;
    private Button openBtn, sendBtn, closeBtn, getVersionBtn, testDataBtn, mdbdownloadfwBtn;
    private static final String TAG = "MDBTEST";
    private final byte[] bytes = {0x09, 0x03, 0x00, (byte) 0x92, (byte) 0x6E, (byte) 0x0D};

    private ProgressBar progress;

    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = (TextView) findViewById(R.id.textviewaa);
        progress_textView = (TextView) findViewById(R.id.progress_textView);
        textView.setMovementMethod(ScrollingMovementMethod.getInstance());
        input = (EditText) findViewById(R.id.input);
        openBtn = (Button) findViewById(R.id.open);
        sendBtn = (Button) findViewById(R.id.send);
        closeBtn = (Button) findViewById(R.id.close);
        openBtn.setOnClickListener(this);
        sendBtn.setOnClickListener(this);
        closeBtn.setOnClickListener(this);
        progress = (ProgressBar) findViewById(R.id.progress);
        getVersionBtn = (Button) findViewById(R.id.getmdbversion);
        testDataBtn = (Button) findViewById(R.id.testmdbdata);
        mdbdownloadfwBtn = (Button) findViewById(R.id.mdbdownloadfw);
        getVersionBtn.setOnClickListener(this);
        testDataBtn.setOnClickListener(this);
        mdbdownloadfwBtn.setOnClickListener(this);
        context = this;
        open();

    }

    void open() {
        int result = SerialPortInterface.open("SERIAL_EXT");
        if (result < 0) {
            Log.d(TAG, "SerialPort open result < 0");
            handler.obtainMessage(BLACK_LOG, "seriaport open failed!" + result).sendToTarget();
        } else {
            result = SerialPortInterface.setBaudrate(115200);
            SystemClock.sleep(100);
            handler.obtainMessage(BLACK_LOG, "seriaport open success!").sendToTarget();
            testMdbRead();
        }
    }

    void close() {
        int result = SerialPortInterface.close();
        if (result < 0) {
            Log.d(TAG, "SerialPort close result < 0");
            handler.obtainMessage(RED_LOG, "seriaport close failed").sendToTarget();
        } else {
            handler.obtainMessage(BLACK_LOG, "seriaport close success").sendToTarget();
        }
    }

    /**
     * 1. test mib
     * send command to serial "Hardware diagnostic test":
     * Eg：
     * Send Data:090300946c0d
     * Recv Data: 09040194006b0d
     * Ps： ifCMDID（0x94）后面的ACK 不为0x00 ，说明MDB 通讯timeout
     * 2.get MIB version
     * send get version cmd
     * Send Data: 090300926e0d
     * Recv Data: 090601920000 0d 600d
     */

    void testMdb() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                open();
                byte[] bytes = {0x09, 0x03, 0x00, (byte) 0x94, (byte) 0x6C, (byte) 0x0D};
                int write = SerialPortInterface.write(bytes, 0, bytes.length);
                if (write >= 0) {
                    handler.obtainMessage(BLACK_LOG, "testing").sendToTarget();
                    byte[] resulta = new byte[16];
                    int read = SerialPortInterface.read(resulta, resulta.length, 3000);
                    if (read >= 0) {
                        String bytes2Str = buf2StringCompact(subByteArray(resulta, read));
                        if (bytes2Str.startsWith("[09 04 01 94 00")) {
                            handler.obtainMessage(BLACK_LOG, "read ok: " + bytes2Str).sendToTarget();
                            handler.obtainMessage(GREEN_LOG, "test ok").sendToTarget();
                        } else {
                            handler.obtainMessage(RED_LOG, "MDB read timeout! " + bytes2Str).sendToTarget();
                            handler.obtainMessage(RED_LOG, "test failed!").sendToTarget();
                        }
                    } else if (read == -262254) {
                        handler.obtainMessage(RED_LOG, "serialport read timeout").sendToTarget();
                    } else {
                        handler.obtainMessage(RED_LOG, "serialport read failed").sendToTarget();
                    }
                }
                close();
            }
        }).start();
    }


    public byte getLRC(byte[] buf) {
        int rr = 0x00;
        for (byte b : buf) {
            rr += (b & 0xFF);
        }
        rr = ~rr + 1;
        return (byte) rr;
    }

    public byte mdbCheckSum(byte[] buf) {
        int rr = 0x00;
        for (byte b : buf) {
            rr += (b & 0xFF);
        }
        return (byte) ((byte) rr & 0xFF);
    }


    private byte[] mergePacket(int mode, byte[] data) {
        byte startCode = 0x09;
        byte endCode = 0x0d;
        byte modebyte = 0x00;
        if (mode == 1) {
            modebyte = 0x01;
        }
        //------
        byte[] modeANDdata = new byte[1 + data.length];
        modeANDdata[0] = modebyte;
        System.arraycopy(data, 0, modeANDdata, 1, data.length);
        byte checkSumLRC = getLRC(modeANDdata);
        //------
        byte[] mergeBytes = new byte[1 + 1 + 1 + data.length + 1 + 1];
        mergeBytes[0] = startCode;
        mergeBytes[1] = (byte) (1 + 1 + data.length);
        mergeBytes[2] = modebyte;
        System.arraycopy(data, 0, mergeBytes, 3, data.length);
        mergeBytes[mergeBytes.length - 2] = checkSumLRC;
        mergeBytes[mergeBytes.length - 1] = endCode;
        //String.format("%02X", byte)
        return mergeBytes;
    }


    public void testMdbRead() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.obtainMessage(BLACK_LOG, "Init, waiting master command...").sendToTarget();
                while (true) {
                    byte[] byteArr = new byte[1];
                    // read start flag 0x09, 1 bit
                    int readResult = SerialPortInterface.read(byteArr, byteArr.length, -1);

                    String sp = "";
                    if (readResult >= 0) {

                        if (buf2StringCompact(byteArr).equals("09")) {
                            // read data length, 1 bit
                            readResult = SerialPortInterface.read(byteArr, byteArr.length, 200);
                            if (readResult > 0) {
                                String lenthHex = buf2StringCompact(byteArr);
                                BigInteger item = new BigInteger(lenthHex, 16);
                                byteArr = new byte[item.intValue()];
                                // read data
                                readResult = SerialPortInterface.read(byteArr, byteArr.length, 200);
                                if (readResult > 0) {
                                    String stringCompact = buf2StringCompact(byteArr);
                                    Log.d("TAG", "read success: " + stringCompact);
                                    sp = stringCompact;
                                } else {
                                    Log.d("TAG", "read failed");
                                }
                            }
                        } else {
                            sp = "reading...";
                        }

                        if (sp.startsWith("00 10")) {
                            if (sp.contains("00 10 10")) { // Reset
                                byte[] wbytes = mergePacket(1, new byte[]{0x00, 0x00,});
                                int wresult = SerialPortInterface.write(wbytes, 0, wbytes.length);
                                if (wresult >= 0) {
                                    handler.obtainMessage(BLACK_LOG, "Reset 0.0%").sendToTarget();
                                }
                            }
                        } else if (sp.startsWith("00 11 00")) { //Setup config data
                            byte[] wbytes = mergePacket(1, new byte[]{0x01, 0x02, 0x00, (byte) 0x86, 0x01, 0x01, 0x00, 0x04, (byte) 0x8f,});
                            int wresult = SerialPortInterface.write(wbytes, 0, wbytes.length);
                            if (wresult >= 0) {
                                handler.obtainMessage(BLACK_LOG, "Setup config data 20.0%").sendToTarget();
                            }
                        } else if (sp.startsWith("00 11 01")) { // Setup Max/Min Price
                            byte[] wbytes = mergePacket(1, new byte[]{0x00});
                            int wresult = SerialPortInterface.write(wbytes, 0, wbytes.length);
                            if (wresult >= 0) {
                                handler.obtainMessage(BLACK_LOG, "Setup Max/Min Price 50.0%").sendToTarget();
                            }
                        } else if (sp.startsWith("00 17 00")) {
                            byte[] wbytes = mergePacket(1, new byte[]{0x09, 0x77, 0x7a, 0x70, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x6d});
                            int wresult = SerialPortInterface.write(wbytes, 0, wbytes.length);
                            if (wresult >= 0) {
                                handler.obtainMessage(BLACK_LOG, "Expansion ID 60.0%").sendToTarget();
                            }
                        } else if (sp.startsWith("00 14 01") || sp.startsWith("00 14 00")) {//Enable Reader
                            byte[] wbytes = mergePacket(1, new byte[]{0x00});
                            int wresult = SerialPortInterface.write(wbytes, 0, wbytes.length);
                            if (wresult >= 0) {
                                handler.obtainMessage(BLACK_LOG, "Enable Reader 80.0%").sendToTarget();
                            }
                            startTransaction();
                        } else if (sp.startsWith("01 00")) {
                            handler.obtainMessage(BLACK_LOG, "Ready! Please input item ID").sendToTarget();
                        } else if (sp.startsWith("00 13 00")) {
                            String amounthex = sp.substring(9, 15).replace(" ", "");
                            String itemhex = sp.substring(15, 21).replace(" ", "");
                            BigInteger amount = new BigInteger(amounthex, 16);
                            BigInteger item = new BigInteger(itemhex, 16);

                            handler.obtainMessage(DIALOG_READCARD, amount + "," + item).sendToTarget();

//                            handler.obtainMessage(BLACK_LOG, "amount: $" + amount + ",item: " + item).sendToTarget();
//                            handler.obtainMessage(DIALOG, amount + "," + item).sendToTarget();

//                            byte[] wbytes = mergePacket(1, new byte[]{0x05, 0x00, 0x64, 0x69});
//                            int wresult = SerialPortInterface.write(wbytes, 0, wbytes.length);
//                            if (wresult >= 0) {
//                                handler.obtainMessage(BLACK_LOG, "approved write success: " + buf2StringCompact(wbytes)).sendToTarget();
//                            }
                        } else if (sp.startsWith("00 13 02")) {
                            byte[] wbytes = mergePacket(1, new byte[]{0x00});
                            int wresult = SerialPortInterface.write(wbytes, 0, wbytes.length);
                            String itemhex = sp.substring(9, 15).replace(" ", "");
                            BigInteger item = new BigInteger(itemhex, 16);
                            if (wresult >= 0) {
                                handler.obtainMessage(DIALOG_ITEM, "Dispatch success! item:" + item).sendToTarget();
                            }
                        } else if (sp.startsWith("00 13 04")) {
                            byte[] wbytes = mergePacket(1, new byte[]{0x07, 0x07,});
                            int wresult = SerialPortInterface.write(wbytes, 0, wbytes.length);
                            if (wresult >= 0) {
                                handler.obtainMessage(BLACK_LOG, "finish.").sendToTarget();
                                SystemClock.sleep(2000);
                                startTransaction();
                            }

                        } else {
                            handler.obtainMessage(RED_LOG, "MDB read timeout! " + sp).sendToTarget();
                        }


                    } else if (readResult == -262254) {
                        handler.obtainMessage(RED_LOG, "serialport read timeout").sendToTarget();
                    } else {
                        handler.obtainMessage(RED_LOG, "serialport read failed = " + readResult).sendToTarget();
                    }
                    SystemClock.sleep(222);
                }
//                close();
            }
        }).start();
    }


    private void startTransaction() {
        byte[] wbytes = mergePacket(0, new byte[]{0x03, 0x00, 0x64, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x00, 0x00, 0x00, 0x63});
        int wresult = SerialPortInterface.write(wbytes, 0, wbytes.length);
        if (wresult >= 0) {
            handler.obtainMessage(BLACK_LOG, "Compleate 100.0%").sendToTarget();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        close();
    }

    //06 01 92 00 00 0E 5F 0D
    void getVersion() {
//        open();
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
        byte[] bytes = {0x09, 0x03, 0x00, (byte) 0x92, (byte) 0x6E, (byte) 0x0D};
        int write = SerialPortInterface.write(bytes, 0, bytes.length);
        if (write >= 0) {
            handler.obtainMessage(BLACK_LOG, "Getting version, wait " + write).sendToTarget();
            byte[] resulta = new byte[16];
            int read = SerialPortInterface.read(resulta, resulta.length, 3000);
            if (read >= 0) {
                String bytes2Str = buf2StringCompact(subByteArray(resulta, read));
                handler.obtainMessage(BLACK_LOG, "get result:" + bytes2Str).sendToTarget();

            } else if (read == -262254) {
                handler.obtainMessage(RED_LOG, "serialport read timeout").sendToTarget();
            } else {
                handler.obtainMessage(RED_LOG, "serialport read failed").sendToTarget();
            }
        }
//            }
//        }).start();
//        close();
    }

    void realSend(byte[] bytes) {
        open();
        int write = SerialPortInterface.write(bytes, 0, bytes.length);
        if (write >= 0) {
            handler.obtainMessage(BLACK_LOG, "write ok, result=" + write).sendToTarget();
            byte[] resulta = new byte[16];
            int read = SerialPortInterface.read(resulta, resulta.length, 3000);
            if (read >= 0) {
                String bytes2Str = buf2StringCompact(subByteArray(resulta, read));
                handler.obtainMessage(BLACK_LOG, "read ok:\n" + bytes2Str).sendToTarget();
            } else if (read == -262254) {
                handler.obtainMessage(RED_LOG, "serialport read timeout").sendToTarget();
            } else {
                handler.obtainMessage(RED_LOG, "serialport read failed").sendToTarget();
            }
        }
        close();
    }

    final int BLACK_LOG = 1;
    final int RED_LOG = 2;
    final int GREEN_LOG = 4;
    final int DIALOG = 8;
    final int DIALOG_ITEM = 16;
    final int DIALOG_READCARD = 17;
    final int DISABLE_UPDATE_BUTTON = 18;
    final int ENABLE_UPDATE_BUTTON = 19;


    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case DISABLE_UPDATE_BUTTON:
                    mdbdownloadfwBtn.setEnabled(false);
                    break;
                case ENABLE_UPDATE_BUTTON:
                    mdbdownloadfwBtn.setEnabled(true);
                    break;
                case BLACK_LOG:
                    LogHelper.appendBlackMsg((String) msg.obj, textView);
                    break;
                case RED_LOG:
//                    LogHelper.appendREDMsg((String) msg.obj, textView);
                    break;
                case GREEN_LOG:
                    LogHelper.appendGreenMsg((String) msg.obj, textView);
                    break;
                case 3:
                    float obj = (float) msg.obj;
                    progress.setProgress((int) obj);
                    progress_textView.setText(obj + "%");
                    break;
                case DIALOG:
                    String obj1 = (String) msg.obj;
                    String[] split = obj1.split(",");
                    AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Please confirm item and amount")
                            .setMessage("Vend product\n " + "Item: " + split[1] + ",Amount: $" + split[0])
                            .setNegativeButton("Deny", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    byte[] wbytes = mergePacket(1, new byte[]{0x06, 0x06,});
                                    int wresult = SerialPortInterface.write(wbytes, 0, wbytes.length);
                                    if (wresult >= 0) {
                                        handler.obtainMessage(BLACK_LOG, "denied!").sendToTarget();
                                    }
                                    dialog.dismiss();
                                }
                            })
                            .setPositiveButton("Approve", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    int a = Integer.parseInt(split[0]);
                                    byte[] bytes = {0x05, 0x00, (byte) Integer.parseInt(split[0]), 0x00};
                                    byte lrc = mdbCheckSum(bytes);
                                    bytes[3] = lrc;
                                    byte[] wbytes = mergePacket(1, bytes);
                                    int wresult = SerialPortInterface.write(wbytes, 0, wbytes.length);
                                    if (wresult >= 0) {
                                        handler.obtainMessage(BLACK_LOG, "approved!").sendToTarget();
                                    }
                                    dialog.dismiss();
                                }
                            }).create();
                    dialog.show();
                    break;
                case DIALOG_ITEM:
                    AlertDialog dialog1 = new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Dispense")
                            .setMessage(msg.obj + "")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            }).create();
                    dialog1.show();
                    break;
                case DIALOG_READCARD:
                    AlertDialog cardDialog = new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Read card")
                            .setMessage("Please swipe card.")
                            .create();
                    cardDialog.show();
                    String obj2 = (String) msg.obj;
                    String[] split2 = obj2.split(",");
                    Log.d(TAG, "Read item data " + split2[1] + "," + split2[0]);

                    if (rfCardReaderDevice == null) {
                        rfCardReaderDevice = (RFCardReaderDevice) POSTerminal.getInstance(context)
                                .getDevice("cloudpos.device.rfcardreader");
                    }

                    if (smartCardReaderDevice == null) {
                        smartCardReaderDevice = (SmartCardReaderDevice) POSTerminal.getInstance(context)
                                .getDevice("cloudpos.device.smartcardreader");
                    }

                    try {
                        rfCardReaderDevice.open();
                        try {
                            OperationListener listener = new OperationListener() {

                                @Override
                                public void handleResult(OperationResult arg0) {

                                    try {
                                        if (arg0.getResultCode() == OperationResult.SUCCESS) {
                                            rfCard = ((RFCardReaderOperationResult) arg0).getCard();
                                            handler.obtainMessage(BLACK_LOG, "amount: $" + split2[1] + ",item: " + split2[0]).sendToTarget();
                                            handler.obtainMessage(DIALOG, split2[1] + "," + split2[0]).sendToTarget();
                                        } else {
                                            Log.d(TAG, "find_card_failed");
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    } finally {
                                        try {
                                            rfCardReaderDevice.close();
                                            cardDialog.dismiss();
                                        } catch (DeviceException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            };
                            rfCardReaderDevice.listenForCardPresent(listener, TimeConstants.FOREVER);
                        } catch (DeviceException e) {
                            e.printStackTrace();
                        }
                    } catch (DeviceException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    };
    private Card rfCard;

    private RFCardReaderDevice rfCardReaderDevice = null;
    private SmartCardReaderDevice smartCardReaderDevice = null;


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.open:
                open();
                break;
            case R.id.close:
                close();
                break;
            case R.id.getmdbversion:
//                textView.setText("");
                getVersion();
                break;
            case R.id.testmdbdata:
                textView.setText(getTimeWithFormat() + "\n");
//                testMdb();
                testMdbRead();
                break;
            case R.id.mdbdownloadfw:
                textView.setText(getTimeWithFormat() + "\n");
                downloadFw();
                break;
            case R.id.send:
                byte[] bytes = createBytes(input.getText().toString());
                realSend(bytes);
                break;
        }
    }

    /**
     * split byte[]
     */
    public byte[][] splitBytes(byte[] bytes, int size) {
        double splitLength = Double.parseDouble(size + "");
        int arrayLength = (int) Math.ceil(bytes.length / splitLength);
        byte[][] result = new byte[arrayLength][];
        int from, to;
        for (int i = 0; i < arrayLength; i++) {

            from = (int) (i * splitLength);
            to = (int) (from + splitLength);
            if (to > bytes.length)
                to = bytes.length;
            result[i] = Arrays.copyOfRange(bytes, from, to);
        }
        return result;
    }

    private byte stringToByte(String strInput) {
        byte[] byteArry = strInput.getBytes();
        for (int i = 0; i < 2; i++) {

            if (byteArry[i] <= 0x39 && byteArry[i] >= 0x30) {
                byteArry[i] -= 0x30;
            } else if (byteArry[i] <= 0x46 && byteArry[i] >= 0x41) {
                byteArry[i] -= 0x37;
            } else if (byteArry[i] <= 0x66 && byteArry[i] >= 0x61) {
                byteArry[i] -= 0x57;
            }
        }
        // Log.i("APP", String.format("byteArry[0] = 0x%X\n", byteArry[0]));
        // Log.i("APP", String.format("byteArry[1] = 0x%X\n", byteArry[1]));
        return (byte) ((byteArry[0] << 4) | (byteArry[1] & 0x0F));
    }

    boolean isDownload;

    Thread downloadTh;


    byte[] readSDfile(String sdcard_mib_path) {
        File file = new File(sdcard_mib_path);
        try {
            FileInputStream fis = new FileInputStream(file);
            int ch = 0;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((ch = fis.read()) != -1) {
                baos.write(ch);
            }
            baos.flush();
            baos.close();
            fis.close();
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void downloadFw() {
        if (downloadTh != null && downloadTh.getState() != Thread.State.TERMINATED) {
            Log.d(TAG, "downloadFw  return" + " downloadFw  return");
            handler.obtainMessage(BLACK_LOG, "downloadFw  return").sendToTarget();
            return;
        }
        listAssets();
        downloadTh = new Thread(new Runnable() {
            @Override
            public void run() {
                isDownload = true;
                SerialPortInterface.close();
                int open = Stm32ispInterface.ispOpen();
                if (open >= 0) {
                    handler.obtainMessage(BLACK_LOG, "stm32 open success").sendToTarget();
                    try {

                        String binPath = Environment.getExternalStorageDirectory().getPath() + "/mib_test.bin";
                        byte[] fwTotal = readSDfile(binPath);
                        if (fwTotal != null && fwTotal.length > 100) {
                            handler.obtainMessage(BLACK_LOG, "\nfound sdcard mib firmware ...,\n, " + binPath).sendToTarget();
                        } else {
                            binPath = assetFiles.get(assetFiles.size() - 1);
                            handler.obtainMessage(BLACK_LOG, "\nfound mib firmware ...,\n, " + binPath).sendToTarget();
                            fwTotal = toByteArray(binPath);
                        }
//                        if (!fwName.contains("facttest")) {
                        fwTotal = Arrays.copyOfRange(fwTotal, 0x80, fwTotal.length);
//                        }

                        float length = fwTotal.length;
                        handler.obtainMessage(BLACK_LOG, "\ndownloading mib firmware...,\n name= " + binPath + ", size= " + length).sendToTarget();
                        byte[][] bytes1 = splitBytes(fwTotal, 0x100);
                        int count = 0;
                        for (byte[] b : bytes1) {
                            Stm32ispInterface.ispDownload(b, b.length);
                            float writeLength = count += b.length;
                            float f = writeLength / length;
                            handler.obtainMessage(3, f * 100).sendToTarget();
                        }
                        handler.obtainMessage(GREEN_LOG, "\ndownload complete!").sendToTarget();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        int i = Stm32ispInterface.ispClose();
                        if (i >= 0) {
                            handler.obtainMessage(1, "stm32 close success").sendToTarget();
                        } else {
                            handler.obtainMessage(2, "stm32 close failed").sendToTarget();
                        }
                        isDownload = false;
                    }
                    SystemClock.sleep(111);
                    int result = SerialPortInterface.open("SERIAL_EXT");
                    getVersion();
                } else {
                    handler.obtainMessage(2, "stm32 open failed").sendToTarget();
                    isDownload = false;
                }
            }
        });
        downloadTh.start();


    }

    ArrayList<String> assetFiles = new ArrayList<>();

    private void listAssets() {
        String[] list = new String[0];
        try {
            list = getAssets().list("");
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (String fileName : list) {
            if (fileName.endsWith(".bin")) {//fileName.startsWith("mib") &&
                Log.d(TAG, "assetFile=" + fileName);
                assetFiles.add(fileName);
            }
        }
    }

    public byte[] toByteArray(String fname) throws IOException {
        InputStream fis = getAssets().open(fname);
        int ch = 0;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((ch = fis.read()) != -1) {
            baos.write(ch);
        }
        baos.flush();
        baos.close();
        fis.close();
        return baos.toByteArray();
    }

    public void senddd() {
        int open = SerialPortInterface.open("SERIAL_EXT");
        if (open >= 0) {
            int write = SerialPortInterface.setBaudrate(115200);
            write = SerialPortInterface.write(bytes, 0, bytes.length);
            if (write >= 0) {
                handler.obtainMessage(BLACK_LOG, "write ok").sendToTarget();
                byte[] result = new byte[16];
                int read = SerialPortInterface.read(result, result.length, 5000);
                if (read >= 0) {
                    handler.obtainMessage(BLACK_LOG, "read ok:\n" + buf2StringCompact(subByteArray(result, read))).sendToTarget();
                } else {
                    handler.obtainMessage(BLACK_LOG, "read failed").sendToTarget();
                }
            }
            SerialPortInterface.close();
        }


    }

    public static byte[] subByteArray(byte[] byteArray, int length) {
        byte[] arrySub = new byte[length];
        if (length >= 0) System.arraycopy(byteArray, 0, arrySub, 0, length);
        return arrySub;
    }

    public static byte[] subByteArrayIgnore(byte[] byteArray, int startIndex, int length) {
        int realLength = length - startIndex;
        byte[] arrySub = new byte[realLength];
        if (length >= 0) System.arraycopy(byteArray, startIndex, arrySub, 0, realLength);
        return arrySub;
    }

    private byte[] createBytes(String edit) {
        ArrayList<Byte> as = new ArrayList<>();

        int s1 = 9;
        int e1 = 13;

        int len;
        int lrc;

        String[] split = edit.replace(",", " ").split(" ");
        len = split.length + 1;

        for (String spli : split) {
            as.add((byte) Integer.parseInt(spli, 16));
        }

        byte[] by = new byte[as.size()];

        for (int i = 0; i < as.size(); i++) {
            by[i] = as.get(i);
        }

        lrc = LRCCheckInt(by);


        ArrayList<Byte> as2 = new ArrayList<>();

        as2.add((byte) s1);
        as2.add((byte) len);

        for (byte b : by) {
            as2.add(b);
        }

        as2.add((byte) lrc);
        as2.add((byte) e1);


        byte[] by2 = new byte[as2.size()];

        for (int i = 0; i < as2.size(); i++) {
//            Log.d("buf2StringCompact by2","as2.get(i)" +as2.get(i));
            by2[i] = as2.get(i);
        }

//        for(byte s : by2){
//            Log.d("buf2StringCompact by2","++++ " + Integer.toHexString(s));
//        }

        String s = buf2StringCompact(by2);
        Log.d("buf2StringCompact", "ss = " + s);
        return by2;
    }

    private int LRCCheckInt(byte[] bytes) {
        int l1 = 0;
        for (byte b : bytes) {
            l1 = l1 + (int) b;
        }
        return ~l1 + 1;
    }

    private String LRCCheck(byte[] bytes) {
        int l1 = 0;
        for (byte b : bytes) {
            l1 = l1 + (int) b;
        }
        return Integer.toHexString(~l1 + 1);
    }

    public static String buf2StringCompact(byte[] buf) {
        int i, index;
        StringBuilder sBuf = new StringBuilder();
//        sBuf.append("[");
        for (i = 0; i < buf.length; i++) {
            index = buf[i] < 0 ? buf[i] + 256 : buf[i];
            if (index < 16) {
                sBuf.append("0").append(Integer.toHexString(index));
            } else {
                sBuf.append(Integer.toHexString(index));
            }
            sBuf.append(" ");
        }
        String substring = sBuf.substring(0, sBuf.length() - 1);
//        return (substring + "]").toUpperCase();
        return (substring).toUpperCase();
    }

    public static String getTimeWithFormat() {
        long time = System.currentTimeMillis();
        SimpleDateFormat sdr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        return sdr.format(time);
    }
}