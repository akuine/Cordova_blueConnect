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
    private static final String CORS_SERVER = "";
    private static final String CORS_PORT = "";
    private static final String CORS_FORMAT = "";
    private static final String CORS_INTERVAL = "";

    public BlueCorsUtils(Activity activity) {
        try {
            if (gBluetoothManageAdapter != null) {
                Log.e(TAG, "initManage: 已经初始化过，无需重复初始化");
                return;
            }
            
            mainActivity = activity;
            gConfBean = new GConfBean(UseMode.rtk);
            initBluetoothAdapter();
            Log.d(TAG, "BlueCorsUtils 初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "初始化 BlueCorsUtils 出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initBluetoothAdapter() {
        try {
            gBluetoothManageAdapter = new GBluetoothManageAdapter(gConfBean, mainActivity) {
                @Override
                public void slog(String msg) {
                    Log.d(TAG, "蓝牙日志: " + msg);
                    if (checkBluListener()) {
                        bluEventListener.bluSlog(msg);
                    }
                }

                @Override
                public void scanResult(GBlueBean gBlueBean) {
                    // 添加详细的日志记录
                    Log.d(TAG, "扫描结果 - 原始数据: " + gBlueBean.toString());
                    
                    if (gBlueBean == null) {
                        Log.e(TAG, "收到空的 GBlueBean 对象");
                        return;
                    }
                    
                    String deviceName = gBlueBean.getName();
                    String deviceAddress = gBlueBean.getAddress();
                    
                    Log.d(TAG, "发现设备 - 名称: " + deviceName + ", 地址: " + deviceAddress);
                    
                    if (deviceName == null || deviceName.trim().isEmpty()) {
                        Log.d(TAG, "跳过名称为空的设备");
                        return;
                    }
                
                    String deviceId = deviceAddress != null ? deviceAddress : deviceName;
                    if (!discoveredDevices.contains(deviceId)) {
                        discoveredDevices.add(deviceId);
                        Log.d(TAG, "新设备加入发现列表: " + deviceName);
                        if (checkBluListener()) {
                            bluEventListener.bluScanResult(gBlueBean);
                        } else {
                            Log.e(TAG, "蓝牙事件监听器为空，无法通知新设备");
                        }
                    } else {
                        Log.d(TAG, "设备已被发现: " + deviceName);
                    }
                }

                @Override
                public void revice(GPositionBean gPositionBean) {
                    if (isConnected) {
                        Log.d(TAG, "接收到位置数据: " + gPositionBean.toString());
                        if (checkBluListener()) {
                            bluEventListener.bluRevice(gPositionBean);
                        }
                    }
                }

                @Override
                public void revice(Hs hs) {
                    if (isConnected) {
                        Log.d(TAG, "接收到RTK数据: " + hs.toString());
                        if (checkBluListener()) {
                            bluEventListener.bluRevice(hs);
                        }
                    }
                }

                @Override
                public void sendGGATocors(String gga) {
                    Log.d(TAG, "发送GGA到CORS: " + gga);
                }

                @Override
                public void connecting(String s, int i) {
                    Log.d(TAG, "正在连接到: " + s);
                    isConnected = false;
                    if (checkBluListener()) {
                        bluEventListener.bluConnecting(s, i);
                    }
                }

                @Override
                public void connectSuccess(String s, int i) {
                    Log.d(TAG, "连接成功: " + s);
                    isConnected = true;
                    connectedDeviceName = s;
                    if (checkBluListener()) {
                        bluEventListener.bluConnectSuccess(s, i);
                    }
                }

                @Override
                public void connectLoss(String s, int i) {
                    Log.e(TAG, "连接丢失: " + s);
                    isConnected = false;
                    connectedDeviceName = null;
                    if (checkBluListener()) {
                        bluEventListener.bluConnectLoss(s, i);
                    }
                }

                @Override
                public void disconnect(int i) {
                    Log.d(TAG, "断开连接: " + i);
                    isConnected = false;
                    connectedDeviceName = null;
                    if (checkBluListener()) {
                        bluEventListener.bluDisconnect(i);
                    }
                }
            };
        } catch (Exception e) {
            Log.e(TAG, "初始化蓝牙适配器出错: " + e.getMessage());
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
                Log.w(TAG, "扫描已经在进行中");
                return;
            }

            bluEventListener = listener;
            if (gBluetoothManageAdapter != null) {
                discoveredDevices.clear();
                isScanning = true;
                gBluetoothManageAdapter.startScan(activity);
                Log.d(TAG, "开始蓝牙扫描");
            } else {
                Log.e(TAG, "蓝牙适配器未初始化");
                throw new IllegalStateException("蓝牙适配器未初始化");
            }
        } catch (Exception e) {
            Log.e(TAG, "启动扫描出错: " + e.getMessage());
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
                Log.d(TAG, "停止蓝牙扫描");
            } else {
                Log.w(TAG, "没有扫描正在进行或适配器未初始化");
            }
        } catch (Exception e) {
            Log.e(TAG, "停止扫描出错: " + e.getMessage());
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
                Log.d(TAG, "设置CORS账号信息，用户: " + userName);
            } else {
                String error = isConnected ? "蓝牙适配器未初始化" : "设备未连接";
                Log.e(TAG, error);
                throw new IllegalStateException(error);
            }
        } catch (Exception e) {
            Log.e(TAG, "设置CORS账号出错: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void getCors2Rtk() {
        try {
            if (gBluetoothManageAdapter != null && isConnected) {
                gBluetoothManageAdapter.getCors2RTK();
                Log.d(TAG, "获取CORS账号信息");
            } else {
                String error = isConnected ? "蓝牙适配器未初始化" : "设备未连接";
                Log.e(TAG, error);
                throw new IllegalStateException(error);
            }
        } catch (Exception e) {
            Log.e(TAG, "获取CORS账号出错: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public synchronized void disconnect() {
        try {
            if (gBluetoothManageAdapter != null && isConnected) {
                gBluetoothManageAdapter.disconnect(1);
                Log.d(TAG, "断开与设备的连接: " + connectedDeviceName);
            } else {
                Log.w(TAG, "未连接设备或适配器未初始化");
            }
        } catch (Exception e) {
            Log.e(TAG, "断开连接出错: " + e.getMessage());
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
                    Log.w(TAG, "已连接到设备: " + blueName);
                    return;
                }
                Log.d(TAG, "正在连接到设备: " + blueName);
                gBluetoothManageAdapter.connectBlueName(blueName);
            } else {
                Log.e(TAG, "蓝牙适配器未初始化");
                throw new IllegalStateException("蓝牙适配器未初始化");
            }
        } catch (Exception e) {
            Log.e(TAG, "连接设备出错: " + e.getMessage());
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
            Log.e(TAG, "蓝牙事件监听器未初始化");
            return false;
        }
    }

    private boolean checkCorsListener() {
        if (corsEventListener != null) {
            return true;
        } else {
            Log.e(TAG, "CORS事件监听器未初始化");
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
