package com.xst.cordova;

import android.app.Activity;
import android.util.Log;
import com.xst.blue.GBlueBean;
import com.xst.blue.GBluetoothManageAdapter;
import com.xst.blue.GPositionBean;
import com.xst.blue.nmea.Hs;
import com.xst.conf.Reason;
import com.xst.cors.GCorsManageAdapter;
import com.xst.util.GConfBean;
import com.xst.util.UseMode;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Set;

public class BlueCorsUtils {
    private static final String TAG = "BlueCorsUtils";
    private GBluetoothManageAdapter gBluetoothManageAdapter = null;
    private GCorsManageAdapter gCorsManageAdapter = null;
    private GConfBean gConfBean = null;
    private OnCorsEventListener corsEventListener;
    private OnBluEventListener bluEventListener;
    private Activity mainActivity;
    private boolean isScanning = false;
    private boolean isConnected = false;
    private String connectedDeviceName = null;
    private Set<String> discoveredDevices = new HashSet<>();
    private static final String CORS_SERVER = "10.10.16.32";
    private static final String CORS_PORT = "2101";
    private static final String CORS_FORMAT = "RTCM32_DBD";
    private static final String CORS_INTERVAL = "1";

    public BlueCorsUtils(Activity activity) {
        try {
            if (gBluetoothManageAdapter != null) {
                Log.e(TAG, "initManage: 已经初始化过，无需重复初始化");
                return;
            }
            
            mainActivity = activity;
            gConfBean = new GConfBean(UseMode.rtk);
            initBluetoothAdapter();
            Log.d(TAG, "BlueCorsUtils initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing BlueCorsUtils: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initBluetoothAdapter() {
        try {
            gBluetoothManageAdapter = new GBluetoothManageAdapter(gConfBean, mainActivity) {
                @Override
                public void slog(String msg) {
                    Log.d(TAG, "Bluetooth log: " + msg);
                    if (checkBluListener()) {
                        bluEventListener.bluSlog(msg);
                    }
                }

                @Override
                public void scanResult(GBlueBean gBlueBean) {
                    // 添加详细的日志记录
                    Log.d(TAG, "Scan result received - Raw data: " + gBlueBean.toString());
                    
                    if (gBlueBean == null) {
                        Log.e(TAG, "Received null GBlueBean object");
                        return;
                    }
                    
                    String deviceName = gBlueBean.getName();
                    String deviceAddress = gBlueBean.getAddress();
                    
                    Log.d(TAG, "Device found - Name: " + deviceName + ", Address: " + deviceAddress);
                    
                    if (deviceName == null || deviceName.trim().isEmpty()) {
                        Log.d(TAG, "Skipping device with empty name");
                        return;
                    }
                
                    String deviceId = deviceAddress != null ? deviceAddress : deviceName;
                    if (!discoveredDevices.contains(deviceId)) {
                        discoveredDevices.add(deviceId);
                        Log.d(TAG, "New device added to discovered list: " + deviceName);
                        if (checkBluListener()) {
                            bluEventListener.bluScanResult(gBlueBean);
                        } else {
                            Log.e(TAG, "BlueListener is null, cannot notify about new device");
                        }
                    } else {
                        Log.d(TAG, "Device already discovered: " + deviceName);
                    }
                }

                @Override
                public void revice(GPositionBean gPositionBean) {
                    if (isConnected) {
                        Log.d(TAG, "Position received: " + gPositionBean.toString());
                        if (checkBluListener()) {
                            bluEventListener.bluRevice(gPositionBean);
                        }
                    }
                }

                @Override
                public void revice(Hs hs) {
                    if (isConnected) {
                        Log.d(TAG, "RTK data received: " + hs.toString());
                        if (checkBluListener()) {
                            bluEventListener.bluRevice(hs);
                        }
                    }
                }

                @Override
                public void sendGGATocors(String gga) {
                    Log.d(TAG, "Sending GGA to CORS: " + gga);
                }

                @Override
                public void connecting(String s, int i) {
                    Log.d(TAG, "Connecting to: " + s);
                    isConnected = false;
                    if (checkBluListener()) {
                        bluEventListener.bluConnecting(s, i);
                    }
                }

                @Override
                public void connectSuccess(String s, int i) {
                    Log.d(TAG, "Connect success: " + s);
                    isConnected = true;
                    connectedDeviceName = s;
                    if (checkBluListener()) {
                        bluEventListener.bluConnectSuccess(s, i);
                    }
                }

                @Override
                public void connectLoss(String s, int i) {
                    Log.e(TAG, "Connect loss: " + s);
                    isConnected = false;
                    connectedDeviceName = null;
                    if (checkBluListener()) {
                        bluEventListener.bluConnectLoss(s, i);
                    }
                }

                @Override
                public void disconnect(int i) {
                    Log.d(TAG, "Disconnected: " + i);
                    isConnected = false;
                    connectedDeviceName = null;
                    if (checkBluListener()) {
                        bluEventListener.bluDisconnect(i);
                    }
                }
            };
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Bluetooth adapter: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isValidDevice(GBlueBean device) {
        if (device == null || device.getName() == null) {
            return false;
        }
        String deviceName = device.getName().trim();
        return !deviceName.isEmpty();
    }

    public synchronized void startBlueScan(Activity activity, OnBluEventListener listener) {
        try {
            if (isScanning) {
                Log.w(TAG, "Scan already in progress");
                return;
            }

            bluEventListener = listener;
            if (gBluetoothManageAdapter != null) {
                discoveredDevices.clear();
                isScanning = true;
                gBluetoothManageAdapter.startScan(activity);
                Log.d(TAG, "Started Bluetooth scan");
            } else {
                Log.e(TAG, "Bluetooth adapter not initialized");
                throw new IllegalStateException("Bluetooth adapter not initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting scan: " + e.getMessage());
            e.printStackTrace();
            isScanning = false;
            throw e;
        }
    }

    public synchronized void stopBlueScan() {
        try {
            if (gBluetoothManageAdapter != null && isScanning) {
                gBluetoothManageAdapter.stopScan();
                isScanning = false;
                discoveredDevices.clear();
                Log.d(TAG, "Stopped Bluetooth scan");
            } else {
                Log.w(TAG, "No scan in progress or adapter not initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping scan: " + e.getMessage());
            e.printStackTrace();
        } finally {
            isScanning = false;
        }
    }

    public void setCors2Rtk(String userName, String keyword) {
        try {
            if (gBluetoothManageAdapter != null && isConnected) {
                gBluetoothManageAdapter.setCors2RTK(
                    CORS_SERVER,
                    CORS_PORT,
                    userName,
                    keyword,
                    CORS_FORMAT,
                    CORS_INTERVAL
                );
                Log.d(TAG, "Set CORS account for user: " + userName);
            } else {
                String error = isConnected ? "Bluetooth adapter not initialized" : "Device not connected";
                Log.e(TAG, error);
                throw new IllegalStateException(error);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting CORS account: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void getCors2Rtk() {
        try {
            if (gBluetoothManageAdapter != null && isConnected) {
                gBluetoothManageAdapter.getCors2RTK();
                Log.d(TAG, "Getting CORS account information");
            } else {
                String error = isConnected ? "Bluetooth adapter not initialized" : "Device not connected";
                Log.e(TAG, error);
                throw new IllegalStateException(error);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting CORS account: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public synchronized void disconnect() {
        try {
            if (gBluetoothManageAdapter != null && isConnected) {
                gBluetoothManageAdapter.disconnect(1);
                Log.d(TAG, "Disconnected from device: " + connectedDeviceName);
            } else {
                Log.w(TAG, "No device connected or adapter not initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error disconnecting: " + e.getMessage());
            e.printStackTrace();
        } finally {
            isConnected = false;
            connectedDeviceName = null;
        }
    }

    public void connectBlueName(String blueName) {
        try {
            if (gBluetoothManageAdapter != null) {
                if (isConnected && blueName.equals(connectedDeviceName)) {
                    Log.w(TAG, "Already connected to device: " + blueName);
                    return;
                }
                Log.d(TAG, "Connecting to device: " + blueName);
                gBluetoothManageAdapter.connectBlueName(blueName);
            } else {
                Log.e(TAG, "Bluetooth adapter not initialized");
                throw new IllegalStateException("Bluetooth adapter not initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error connecting to device: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isScanning() {
        return isScanning;
    }

    public String getConnectedDeviceName() {
        return connectedDeviceName;
    }

    private boolean checkBluListener() {
        if (bluEventListener != null) {
            return true;
        } else {
            Log.e(TAG, "Bluetooth event listener not initialized");
            return false;
        }
    }

    private boolean checkCorsListener() {
        if (corsEventListener != null) {
            return true;
        } else {
            Log.e(TAG, "CORS event listener not initialized");
            return false;
        }
    }

    public interface OnCorsEventListener {
        void corsSlog(String msg);
        void corsConnecting(String msg);
        void corsConnectSuccess(String msg);
        void corsConnectLoss(String s, String s1, Reason reason);
        void corsDisconnect();
    }

    public interface OnBluEventListener {
        void bluSlog(String msg);
        void bluScanResult(GBlueBean gBlueBean);
        void bluRevice(GPositionBean gPositionBean);
        void bluRevice(Hs hs);
        void bluConnecting(String s, int i);
        void bluConnectSuccess(String s, int i);
        void bluConnectLoss(String s, int i);
        void bluDisconnect(int i);
    }
}