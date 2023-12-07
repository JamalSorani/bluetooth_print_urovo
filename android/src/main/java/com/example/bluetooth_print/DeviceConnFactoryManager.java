package com.example.bluetooth_print;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
//import com.gprinter.io.*;
import com.smart.io.*;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author thon
 */
public class DeviceConnFactoryManager {
    private static final String TAG = DeviceConnFactoryManager.class.getSimpleName();

    public PortManager mPort;

    public CONN_METHOD connMethod;

    private final String macAddress;

    private final Context mContext;

    private static Map<String, DeviceConnFactoryManager> deviceConnFactoryManagers = new HashMap<>();

    private boolean isOpenPort;
    /**
     * ESC��ѯ��ӡ��ʵʱ״ָ̬��
     */
    private final byte[] esc = {0x10, 0x04, 0x02};

    /**
     * ESC��ѯ��ӡ��ʵʱ״̬ ȱֽ״̬
     */
    private static final int ESC_STATE_PAPER_ERR = 0x20;

    /**
     * ESCָ���ѯ��ӡ��ʵʱ״̬ ��ӡ������״̬
     */
    private static final int ESC_STATE_COVER_OPEN = 0x04;

    /**
     * ESCָ���ѯ��ӡ��ʵʱ״̬ ��ӡ������״̬
     */
    private static final int ESC_STATE_ERR_OCCURS = 0x40;

    /**
     * TSC��ѯ��ӡ��״ָ̬��
     */
    private final byte[] tsc = {0x1b, '!', '?'};

    /**
     * TSCָ���ѯ��ӡ��ʵʱ״̬ ��ӡ��ȱֽ״̬
     */
    private static final int TSC_STATE_PAPER_ERR = 0x04;

    /**
     * TSCָ���ѯ��ӡ��ʵʱ״̬ ��ӡ������״̬
     */
    private static final int TSC_STATE_COVER_OPEN = 0x01;

    /**
     * TSCָ���ѯ��ӡ��ʵʱ״̬ ��ӡ������״̬
     */
    private static final int TSC_STATE_ERR_OCCURS = 0x80;

    private final byte[] cpcl={0x1b,0x68};

    /**
     * CPCLָ���ѯ��ӡ��ʵʱ״̬ ��ӡ��ȱֽ״̬
     */
    private static final int CPCL_STATE_PAPER_ERR = 0x01;
    /**
     * CPCLָ���ѯ��ӡ��ʵʱ״̬ ��ӡ������״̬
     */
    private static final int CPCL_STATE_COVER_OPEN = 0x02;

    private byte[] sendCommand;

    /**
     * �жϴ�ӡ����ʹ��ָ���Ƿ���ESCָ��
     */
    private PrinterCommand currentPrinterCommand;
    public static final byte FLAG = 0x10;
    private static final int READ_DATA = 10000;
    private static final int DEFAUIT_COMMAND=20000;
    private static final String READ_DATA_CNT = "read_data_cnt";
    private static final String READ_BUFFER_ARRAY = "read_buffer_array";
    public static final String ACTION_CONN_STATE = "action_connect_state";
    public static final String ACTION_QUERY_PRINTER_STATE = "action_query_printer_state";
    public static final String STATE = "state";
    public static final String DEVICE_ID = "id";
    public static final int CONN_STATE_DISCONNECT = 0x90;
    public static final int CONN_STATE_CONNECTED = CONN_STATE_DISCONNECT << 3;
    public PrinterReader reader;
    private int queryPrinterCommandFlag;
    private final int ESC = 1;
    private final int TSC = 3;
    private final int CPCL = 2;

    public enum CONN_METHOD {
        //��������
        BLUETOOTH("BLUETOOTH"),
        //USB����
        USB("USB"),
        //wifi����
        WIFI("WIFI"),
        //��������
        SERIAL_PORT("SERIAL_PORT");

        private final String name;

        private CONN_METHOD(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    public static Map<String, DeviceConnFactoryManager> getDeviceConnFactoryManagers() {
        return deviceConnFactoryManagers;
    }

    /**
     * �򿪶˿�
     */
    public void openPort() {
        DeviceConnFactoryManager deviceConnFactoryManager = deviceConnFactoryManagers.get(macAddress);
        if(deviceConnFactoryManager == null){
            return;
        }

        deviceConnFactoryManager.isOpenPort = false;
        if (deviceConnFactoryManager.connMethod == CONN_METHOD.BLUETOOTH) {
            mPort = new BluetoothPort(macAddress);
            isOpenPort = deviceConnFactoryManager.mPort.openPort();
        }

        //�˿ڴ򿪳ɹ��󣬼�����Ӵ�ӡ����ʹ�õĴ�ӡ��ָ��ESC��TSC
        if (isOpenPort) {
            queryCommand();
        } else {
            if (this.mPort != null) {
                this.mPort=null;
            }

        }
    }

    /**
     * ��ѯ��ǰ���Ӵ�ӡ����ʹ�ô�ӡ��ָ�ESC��EscCommand.java����TSC��LabelCommand.java����
     */
    private void queryCommand() {
        //������ȡ��ӡ�����������߳�
        reader = new PrinterReader();
        reader.start(); //��ȡ�����߳�
        //��ѯ��ӡ����ʹ��ָ��
        queryPrinterCommand(); //СƱ�����Ӳ���  ע�����У�������������д��롣ʹ��ESCָ��
    }

    /**
     * ��ȡ�˿����ӷ�ʽ
     */
    public CONN_METHOD getConnMethod() {
        return connMethod;
    }

    /**
     * ��ȡ�˿ڴ�״̬��true �򿪣�false δ�򿪣�
     */
    public boolean getConnState() {
        return isOpenPort;
    }

    /**
     * ��ȡ���������������ַ
     */
    public String getMacAddress() {
        return macAddress;
    }

    /**
     * �رն˿�
     */
    public void closePort() {
        if (this.mPort != null) {
            if(reader!=null) {
                reader.cancel();
                reader = null;
            }
            boolean b= this.mPort.closePort();
            if(b) {
                this.mPort=null;
                isOpenPort = false;
                currentPrinterCommand = null;
            }

            Log.e(TAG, "******************* close Port macAddress -> " + macAddress);
        }
    }

    public static void closeAllPort() {
        for (DeviceConnFactoryManager deviceConnFactoryManager : deviceConnFactoryManagers.values()) {
            if (deviceConnFactoryManager != null) {
                Log.e(TAG, "******************* close All Port macAddress -> " + deviceConnFactoryManager.macAddress);

                deviceConnFactoryManager.closePort();
                deviceConnFactoryManagers.put(deviceConnFactoryManager.macAddress, null);
            }
        }
    }

    private DeviceConnFactoryManager(Build build) {
        this.connMethod = build.connMethod;
        this.macAddress = build.macAddress;
        this.mContext = build.context;
        deviceConnFactoryManagers.put(build.macAddress, this);
    }

    /**
     * ��ȡ��ǰ��ӡ��ָ��
     *
     * @return PrinterCommand
     */
    public PrinterCommand getCurrentPrinterCommand() {
        return Objects.requireNonNull(deviceConnFactoryManagers.get(macAddress)).currentPrinterCommand;
    }

    public static final class Build {
        private String macAddress;
        private CONN_METHOD connMethod;
        private Context context;

        public DeviceConnFactoryManager.Build setMacAddress(String macAddress) {
            this.macAddress = macAddress;
            return this;
        }

        public DeviceConnFactoryManager.Build setConnMethod(CONN_METHOD connMethod) {
            this.connMethod = connMethod;
            return this;
        }

        public DeviceConnFactoryManager.Build setContext(Context context) {
            this.context = context;
            return this;
        }

        public DeviceConnFactoryManager build() {
            return new DeviceConnFactoryManager(this);
        }
    }

    public void sendDataImmediately(final Vector<Byte> data) {
        if (this.mPort == null) {
            return;
        }
        try {
            this.mPort.writeDataImmediately(data, 0, data.size());
        } catch (Exception e) {//�쳣�жϷ���
            mHandler.obtainMessage(Constant.abnormal_Disconnection).sendToTarget();
//            e.printStackTrace();

        }
    }
    public void sendByteDataImmediately(final byte [] data) {
        if (this.mPort != null) {
            Vector<Byte> datas = new Vector<Byte>();
            for (byte datum : data) {
                datas.add(Byte.valueOf(datum));
            }
            try {
                this.mPort.writeDataImmediately(datas, 0, datas.size());
            } catch (IOException e) {//�쳣�ж�
                mHandler.obtainMessage(Constant.abnormal_Disconnection).sendToTarget();
            }
        }
    }
//    public int readDataImmediately(byte[] buffer){
//        int r = 0;
//        if (this.mPort == null) {
//            return r;
//        }
//
//        try {
//            r =  this.mPort.readData(buffer);
//        } catch (IOException e) {
//            closePort();
//        }
//
//        return  r;
//    }

    public boolean readDataImmediately(byte[] buffer, int len, long timeout) throws IOException {
        return this.mPort.readData(buffer, len, timeout);
    }

    /**
     * ��ѯ��ӡ����ǰʹ�õ�ָ�ESC��CPCL��TSC����
     */
    private void queryPrinterCommand() {
        queryPrinterCommandFlag = TSC;
        ThreadPool.getInstantiation().addSerialTask(new Runnable() {
            @Override
            public void run() {
                //������ʱ������2000����û��û����ֵʱ���Ͳ�ѯ��ӡ��״ָ̬��ȷ�Ʊ�ݣ��浥����ǩ
                final ThreadFactoryBuilder threadFactoryBuilder = new ThreadFactoryBuilder("Timer");
                final ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(1, threadFactoryBuilder);
                scheduledExecutorService.scheduleAtFixedRate(threadFactoryBuilder.newThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentPrinterCommand == null && queryPrinterCommandFlag > TSC) {
                            if (reader != null) {//����״̬����ѯ�޷���ֵ����������ʧ�ܹ㲥
                                reader.cancel();
                                mPort.closePort();
                                isOpenPort = false;

                                scheduledExecutorService.shutdown();
                            }
                        }
                        if (currentPrinterCommand != null) {
                            if (!scheduledExecutorService.isShutdown()) {
                                scheduledExecutorService.shutdown();
                            }
                            return;
                        }
                        switch (queryPrinterCommandFlag) {
                            case ESC:
                                //����ESC��ѯ��ӡ��״ָ̬��
                                sendCommand = esc;
                                break;
                            case TSC:
                                //����ESC��ѯ��ӡ��״ָ̬��
                                sendCommand = tsc;
                                break;
                            case CPCL:
                                //����CPCL��ѯ��ӡ��״ָ̬��
                                sendCommand = cpcl;
                                break;
                            default:
                                break;
                        }
                        Vector<Byte> data = new Vector<>(sendCommand.length);
                        for (byte b : sendCommand) {
                            data.add(b);
                        }
                        sendDataImmediately(data);
                        queryPrinterCommandFlag++;
                    }
                }), 1500, 1500, TimeUnit.MILLISECONDS);
            }
        });
    }

    class PrinterReader extends Thread {
        private boolean isRun = false;
        private final byte[] buffer = new byte[100];

        public PrinterReader() {
            isRun = true;
        }

        @Override
        public void run() {
            try {
                while (isRun && mPort != null) {
                    //��ȡ��ӡ��������Ϣ,��ӡ��û�з���ֽ����-1
                    Log.e(TAG,"******************* wait read ");
                    int len = mPort.getInputStream().read(buffer);
                    Log.e(TAG,"******************* read "+len);
                    if (len > 0) {
                        Message message = Message.obtain();
                        message.what = READ_DATA;
                        Bundle bundle = new Bundle();
                        bundle.putInt(READ_DATA_CNT, len); //���ݳ���
                        bundle.putByteArray(READ_BUFFER_ARRAY, buffer); //����
                        message.setData(bundle);
                        mHandler.sendMessage(message);
                    }
                }
            } catch (Exception e) {//�쳣�Ͽ�
                if (deviceConnFactoryManagers.get(macAddress) != null) {
                    closePort();
                    mHandler.obtainMessage(Constant.abnormal_Disconnection).sendToTarget();
                }
            }
        }

        public void cancel() {
            isRun = false;
        }
    }

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constant.abnormal_Disconnection://�쳣�Ͽ�����
                    Log.d(TAG, "******************* abnormal disconnection");
                    sendStateBroadcast(Constant.abnormal_Disconnection);
                    break;
                case DEFAUIT_COMMAND://Ĭ��ģʽ

                    break;
                case READ_DATA:
                    int cnt = msg.getData().getInt(READ_DATA_CNT); //���ݳ��� >0;
                    byte[] buffer = msg.getData().getByteArray(READ_BUFFER_ARRAY);  //����
                    //����ֻ�Բ�ѯ״̬����ֵ��������������ֵ�ɲο�����ֲ�������
                    if (buffer == null) {
                        return;
                    }
                    int result = judgeResponseType(buffer[0]); //��������
                    String status = "";
                    if (sendCommand == esc) {
                        //���õ�ǰ��ӡ��ģʽΪESCģʽ
                        if (currentPrinterCommand == null) {
                            currentPrinterCommand = PrinterCommand.ESC;
                            sendStateBroadcast(CONN_STATE_CONNECTED);
                        } else {//��ѯ��ӡ��״̬
                            if (result == 0) {//��ӡ��״̬��ѯ
                                Intent intent = new Intent(ACTION_QUERY_PRINTER_STATE);
                                intent.putExtra(DEVICE_ID, macAddress);
                                if(mContext!=null){
                                    mContext.sendBroadcast(intent);
                                }
                            } else if (result == 1) {//��ѯ��ӡ��ʵʱ״̬
                                if ((buffer[0] & ESC_STATE_PAPER_ERR) > 0) {
                                    status += "*******************  Printer out of paper";
                                }
                                if ((buffer[0] & ESC_STATE_COVER_OPEN) > 0) {
                                    status += "*******************  Printer open cover";
                                }
                                if ((buffer[0] & ESC_STATE_ERR_OCCURS) > 0) {
                                    status += "*******************  Printer error";
                                }
                                Log.d(TAG, status);
                            }
                        }
                    }else if (sendCommand == tsc) {
                        //���õ�ǰ��ӡ��ģʽΪTSCģʽ
                        if (currentPrinterCommand == null) {
                            currentPrinterCommand = PrinterCommand.TSC;
                            sendStateBroadcast(CONN_STATE_CONNECTED);
                        } else {
                            if (cnt == 1) {//��ѯ��ӡ��ʵʱ״̬
                                if ((buffer[0] & TSC_STATE_PAPER_ERR) > 0) {
                                    //ȱֽ
                                    status += "*******************  Printer out of paper";
                                }
                                if ((buffer[0] & TSC_STATE_COVER_OPEN) > 0) {
                                    //����
                                    status += "*******************  Printer open cover";
                                }
                                if ((buffer[0] & TSC_STATE_ERR_OCCURS) > 0) {
                                    //��ӡ������
                                    status += "*******************  Printer error";
                                }
                                Log.d(TAG, status);
                            } else {//��ӡ��״̬��ѯ
                                Intent intent = new Intent(ACTION_QUERY_PRINTER_STATE);
                                intent.putExtra(DEVICE_ID, macAddress);
                                if(mContext!=null){
                                    mContext.sendBroadcast(intent);
                                }
                            }
                        }
                    }else if(sendCommand==cpcl){
                        if (currentPrinterCommand == null) {
                            currentPrinterCommand = PrinterCommand.CPCL;
                            sendStateBroadcast(CONN_STATE_CONNECTED);
                        }else {
                            if (cnt == 1) {

                                if ((buffer[0] ==CPCL_STATE_PAPER_ERR)) {//ȱֽ
                                    status += "*******************  Printer out of paper";
                                }
                                if ((buffer[0] ==CPCL_STATE_COVER_OPEN)) {//����
                                    status += "*******************  Printer open cover";
                                }
                                Log.d(TAG, status);
                            } else {//��ӡ��״̬��ѯ
                                Intent intent = new Intent(ACTION_QUERY_PRINTER_STATE);
                                intent.putExtra(DEVICE_ID, macAddress);
                                if(mContext!=null){
                                    mContext.sendBroadcast(intent);
                                }
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * ���͹㲥
     */
    private void sendStateBroadcast(int state) {
        Intent intent = new Intent(ACTION_CONN_STATE);
        intent.putExtra(STATE, state);
        intent.putExtra(DEVICE_ID, macAddress);
        if(mContext != null){
            mContext.sendBroadcast(intent);//�˴�������ָ�������Ҫ���嵥�ļ�application��ǩ��ע����࣬�ο�demo
        }
    }

    /**
     * �ж���ʵʱ״̬��10 04 02�����ǲ�ѯ״̬��1D 72 01��
     */
    private int judgeResponseType(byte r) {
        return (byte) ((r & FLAG) >> 4);
    }

}