package com.xst.cordova;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.app.Activity;
import android.util.Log;
import com.xst.blue.GBlueBean;
import com.xst.blue.GPositionBean;
import com.xst.blue.nmea.Hs;
import android.content.Context;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class BlueConnectPlugin extends CordovaPlugin {
    private static final String TAG = "BlueConnectPlugin";
    private CallbackContext scanCallbackContext;
    private CallbackContext generalCallbackContext;
    private BlueCorsUtils blueCorsUtils;
    private Activity activity;
    private Context appContext;
    // 需要申请的权限列表
    private static final String[] permissions = new String[]{
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    };

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        try {
            activity = cordova.getActivity();
            appContext = activity.getApplicationContext();
            checkAndRequestPermissions();
            blueCorsUtils = new BlueCorsUtils(activity);
            Log.d(TAG, "插件初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "插件初始化错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }
        if (!permissionsNeeded.isEmpty()) {
            Log.d(TAG, "正在请求缺失的权限");
            ActivityCompat.requestPermissions(activity, permissionsNeeded.toArray(new String[0]), 1);
        } else {
            Log.d(TAG, "所有必需权限已获取");
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, "执行操作: " + action);

        switch (action) {
            case "startScan":
                this.scanCallbackContext = callbackContext;
                startScan();
                return true;
            case "stopScan":
                this.generalCallbackContext = callbackContext;
                stopScan();
                return true;
            case "connect":
                this.generalCallbackContext = callbackContext;
                String deviceName = args.getString(0);
                connect(deviceName);
                return true;
            case "disconnect":
                this.generalCallbackContext = callbackContext;
                disconnect();
                return true;
            case "setCorsAccount":
                this.generalCallbackContext = callbackContext;
                String username = args.getString(0);
                String password = args.getString(1);
                setCorsAccount(username, password);
                return true;
            case "getCorsAccount":
                this.generalCallbackContext = callbackContext;
                getCorsAccount();
                return true;
            default:
                Log.d(TAG, "无效的操作: " + action);
                return false;
        }
    }

    private void startScan() {
        try {
            if (blueCorsUtils != null) {
                Log.d(TAG, "开始扫描蓝牙设备");
                blueCorsUtils.startBlueScan(activity, new BlueCorsUtils.OnBluEventListener() {
                    @Override
                    public void bluSlog(String msg) {
                        Log.d(TAG, "蓝牙日志: " + msg);
                        sendScanResult("log", msg);
                    }

                    @Override
                    public void bluScanResult(GBlueBean gBlueBean) {
                        try {
                            if (gBlueBean == null) {
                                Log.e(TAG, "收到空的蓝牙设备对象");
                                return;
                            }

                            Log.d(TAG, "发现新设备: " + gBlueBean.getName());
                            JSONObject deviceInfo = new JSONObject();
                            deviceInfo.put("name", gBlueBean.getName());
                            deviceInfo.put("address", gBlueBean.getAddress());
                            
                            Log.d(TAG, "发送设备信息到JS: " + deviceInfo.toString());
                            sendScanResult("device", deviceInfo);
                        } catch (JSONException e) {
                            Log.e(TAG, "处理扫描结果错误: " + e.getMessage());
                            sendError(scanCallbackContext, "处理扫描结果错误: " + e.getMessage());
                        }
                    }

                    @Override
                    public void bluRevice(GPositionBean gPositionBean) {
                        try {
                            if (gPositionBean == null) {
                                Log.e(TAG, "收到空的位置信息对象");
                                return;
                            }

                            JSONObject positionInfo = new JSONObject();
                            positionInfo.put("position", gPositionBean.toString());
                            Log.d(TAG, "收到位置信息: " + positionInfo.toString());
                            sendScanResult("position", positionInfo);
                        } catch (JSONException e) {
                            Log.e(TAG, "处理位置数据错误: " + e.getMessage());
                            sendError(scanCallbackContext, "处理位置数据错误: " + e.getMessage());
                        }
                    }

                    @Override
                    public void bluRevice(Hs hs) {
                        try {
                            if (hs == null) {
                                Log.e(TAG, "收到空的RTK数据对象");
                                return;
                            }

                            Log.d(TAG, "收到RTK数据: " + hs.toString());
                            sendScanResult("rtk", hs.toString());
                        } catch (Exception e) {
                            Log.e(TAG, "处理RTK数据错误: " + e.getMessage());
                            sendError(scanCallbackContext, "处理RTK数据错误: " + e.getMessage());
                        }
                    }

                    @Override
                    public void bluConnecting(String s, int i) {
                        try {
                            JSONObject connectingInfo = new JSONObject();
                            connectingInfo.put("device", s);
                            connectingInfo.put("status", i);
                            Log.d(TAG, "正在连接设备: " + s);
                            sendScanResult("connecting", connectingInfo);
                        } catch (JSONException e) {
                            Log.e(TAG, "处理连接状态错误: " + e.getMessage());
                            sendError(scanCallbackContext, "处理连接状态错误: " + e.getMessage());
                        }
                    }

                    @Override
                    public void bluConnectSuccess(String s, int i) {
                        try {
                            JSONObject successInfo = new JSONObject();
                            successInfo.put("device", s);
                            successInfo.put("status", i);
                            Log.d(TAG, "设备连接成功: " + s);
                            sendScanResult("connected", successInfo);
                        } catch (JSONException e) {
                            Log.e(TAG, "处理连接成功状态错误: " + e.getMessage());
                            sendError(scanCallbackContext, "处理连接成功状态错误: " + e.getMessage());
                        }
                    }

                    @Override
                    public void bluConnectLoss(String s, int i) {
                        try {
                            JSONObject lossInfo = new JSONObject();
                            lossInfo.put("device", s);
                            lossInfo.put("status", i);
                            Log.e(TAG, "设备连接丢失: " + s);
                            sendScanResult("connectionLoss", lossInfo);
                        } catch (JSONException e) {
                            Log.e(TAG, "处理连接丢失状态错误: " + e.getMessage());
                            sendError(scanCallbackContext, "处理连接丢失状态错误: " + e.getMessage());
                        }
                    }

                    @Override
                    public void bluDisconnect(int i) {
                        try {
                            JSONObject disconnectInfo = new JSONObject();
                            disconnectInfo.put("status", i);
                            Log.d(TAG, "设备已断开连接");
                            sendScanResult("disconnected", disconnectInfo);
                        } catch (JSONException e) {
                            Log.e(TAG, "处理断开连接状态错误: " + e.getMessage());
                            sendError(scanCallbackContext, "处理断开连接状态错误: " + e.getMessage());
                        }
                    }
                });
            } else {
                Log.e(TAG, "蓝牙工具未初始化");
                sendError(scanCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            Log.e(TAG, "启动扫描错误: " + e.getMessage());
            sendError(scanCallbackContext, "启动扫描错误: " + e.getMessage());
        }
    }

    private void stopScan() {
        try {
            if (blueCorsUtils != null) {
                blueCorsUtils.stopBlueScan();
                Log.d(TAG, "扫描停止成功");
                sendSuccess(generalCallbackContext, "scanStopped", "扫描已停止");
            } else {
                Log.e(TAG, "蓝牙工具未初始化");
                sendError(generalCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            Log.e(TAG, "停止扫描错误: " + e.getMessage());
            sendError(generalCallbackContext, "停止扫描错误: " + e.getMessage());
        }
    }

    private void connect(String deviceName) {
        try {
            if (blueCorsUtils != null) {
                Log.d(TAG, "尝试连接设备: " + deviceName);
                blueCorsUtils.connectBlueName(deviceName);
            } else {
                Log.e(TAG, "蓝牙工具未初始化");
                sendError(generalCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            Log.e(TAG, "连接错误: " + e.getMessage());
            sendError(generalCallbackContext, "连接错误: " + e.getMessage());
        }
    }

    private void disconnect() {
        try {
            if (blueCorsUtils != null) {
                Log.d(TAG, "正在断开设备连接");
                blueCorsUtils.disconnect();
            } else {
                Log.e(TAG, "蓝牙工具未初始化");
                sendError(generalCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            Log.e(TAG, "断开连接错误: " + e.getMessage());
            sendError(generalCallbackContext, "断开连接错误: " + e.getMessage());
        }
    }

    private void setCorsAccount(String username, String password) {
        try {
            if (blueCorsUtils != null) {
                Log.d(TAG, "设置CORS账户, 用户名: " + username);
                blueCorsUtils.setCors2Rtk(username, password);
                sendSuccess(generalCallbackContext, "corsSet", "CORS账户设置成功");
            } else {
                Log.e(TAG, "蓝牙工具未初始化");
                sendError(generalCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            Log.e(TAG, "设置CORS账户错误: " + e.getMessage());
            sendError(generalCallbackContext, "设置CORS账户错误: " + e.getMessage());
        }
    }

    private void getCorsAccount() {
        try {
            if (blueCorsUtils != null) {
                Log.d(TAG, "获取CORS账户信息");
                blueCorsUtils.getCors2Rtk();
            } else {
                Log.e(TAG, "蓝牙工具未初始化");
                sendError(generalCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            Log.e(TAG, "获取CORS账户错误: " + e.getMessage());
            sendError(generalCallbackContext, "获取CORS账户错误: " + e.getMessage());
        }
    }

    private void sendScanResult(String type, Object data) {
        if (scanCallbackContext != null) {
            try {
                JSONObject result = new JSONObject();
                result.put("type", type);
                result.put("data", data);
                
                Log.d(TAG, "发送扫描结果: " + result.toString());
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
                pluginResult.setKeepCallback(true);
                scanCallbackContext.sendPluginResult(pluginResult);
            } catch (JSONException e) {
                Log.e(TAG, "创建扫描结果错误: " + e.getMessage());
                sendError(scanCallbackContext, "创建扫描结果错误: " + e.getMessage());
            }
        } else {
            Log.e(TAG, "扫描回调上下文为空");
        }
    }

    private void sendSuccess(CallbackContext context, String type, Object data) {
        if (context != null) {
            try {
                JSONObject result = new JSONObject();
                result.put("type", type);
                result.put("data", data);
                Log.d(TAG, "发送成功结果: " + result.toString());
                context.success(result);
            } catch (JSONException e) {
                Log.e(TAG, "创建成功结果错误: " + e.getMessage());
                context.error("创建结果错误: " + e.getMessage());
            }
        } else {
            Log.e(TAG, "回调上下文为空");
        }
    }

    private void sendError(CallbackContext context, String message) {
        if (context != null) {
            Log.e(TAG, "错误: " + message);
            context.error(message);
        } else {
            Log.e(TAG, "回调上下文为空，错误信息: " + message);
        }
    }
}