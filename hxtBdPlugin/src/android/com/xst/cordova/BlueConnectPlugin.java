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
        Log.d(TAG, "执行操作: " + action); // 添加这行来确认是否收到断开请求

        switch (action) {
            case "startScan":
            this.scanCallbackContext = callbackContext;  // 设置扫描回调上下文
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    startScan();
                }
            });
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
                // 添加日志
                Log.d(TAG, "收到断开连接请求");
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        disconnect();
                    }
                });
                return true;
            case "setCorsAccount":
                final String username = args.getString(0);
                final String password = args.getString(1);
                this.generalCallbackContext = callbackContext;
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            Log.d(TAG, "Native层: 开始设置CORS账号");
                            if (blueCorsUtils != null) {
                                // 添加连接状态检查
                                if (blueCorsUtils.isConnected()) {
                                    Log.d(TAG, "Native层: 正在设置CORS账号, username=" + username);
                                    blueCorsUtils.setCors2Rtk(username, password);
                                    sendSuccess(generalCallbackContext, "corsSet", "CORS账号设置成功");
                                } else {
                                    Log.e(TAG, "Native层: 设备未连接");
                                    sendError(generalCallbackContext, "请先连接设备");
                                }
                            } else {
                                Log.e(TAG, "Native层: blueCorsUtils未初始化");
                                sendError(generalCallbackContext, "蓝牙工具未初始化");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Native层: 设置CORS账号错误", e);
                            sendError(generalCallbackContext, "设置CORS账号错误: " + e.getMessage());
                        }
                    }
                });
                return true;
            case "getCorsAccount":
                this.generalCallbackContext = callbackContext;
                getCorsAccount();
                return true;
            case "getBdICR":
                this.generalCallbackContext = callbackContext;
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        getBdICR();
                    }
                });
                return true;

            case "getMqttAccount":
                this.generalCallbackContext = callbackContext;
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        getMqttAccountInfo();  // 重命名方法避免冲突
                    }
                });
                return true;


            case "setInsBlueToRtk":
                final String insBlueName = args.getString(0);
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        setInsBlueToRtk(insBlueName);
                    }
                });
                return true;

            case "getInsBlueFromRtk":
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        getInsBlueFromRtk();
                    }
                });
                return true;

            case "setMqttAccount":
                final String mqttIp = args.getString(0);
                final String mqttAccount = args.getString(1);
                final String mqttPwd = args.getString(2);  // 修改变量名避免冲突
                final String mqttTopic = args.getString(3);
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        setMqttAccount(mqttIp, mqttAccount, mqttPwd, mqttTopic);
                    }
                });
                return true;



            case "startGather":  // 开启键盘按钮监听
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        startGather();
                    }
                });
                return true;

            case "sendBlueData":  // 发送数据到蓝牙
                final String data = args.getString(0);
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        sendBlueData(data);
                    }
                });
                return true;

            case "sendGgaToCors":  // 发送GGA数据到CORS
                final String gga = args.getString(0);
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        sendGgaToCors(gga);
                    }
                });
                return true;
            default:
                Log.d(TAG, "无效的操作: " + action);
                return false;
        }
    }
    private void getBdICR() {
        try {
            if (blueCorsUtils != null) {
                blueCorsUtils.getBdICR();
                sendSuccess(generalCallbackContext, "bdICR", "正在获取北斗卡号");
            } else {
                sendError(generalCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            sendError(generalCallbackContext, "获取北斗卡号错误: " + e.getMessage());
        }
    }

    private void getMqttAccountInfo() {
        try {
            if (blueCorsUtils != null) {
                blueCorsUtils.getMqtt2RTK();
                sendSuccess(generalCallbackContext, "mqttGet", "正在获取MQTT账号信息");
            } else {
                sendError(generalCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            sendError(generalCallbackContext, "获取MQTT账号错误: " + e.getMessage());
        }
    }

    private void setInsBlueToRtk(String insBlueName) {
        try {
            if (blueCorsUtils != null) {
                blueCorsUtils.setInsBlueName2RTK(insBlueName);
                sendSuccess(generalCallbackContext, "insBlueSet", "设置惯导蓝牙成功");
            } else {
                sendError(generalCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            sendError(generalCallbackContext, "设置惯导蓝牙错误: " + e.getMessage());
        }
    }

    private void getInsBlueFromRtk() {
        try {
            if (blueCorsUtils != null) {
                blueCorsUtils.getInsBlueName2RTK();
                sendSuccess(generalCallbackContext, "insBlueGet", "正在获取惯导蓝牙名称");
            } else {
                sendError(generalCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            sendError(generalCallbackContext, "获取惯导蓝牙名称错误: " + e.getMessage());
        }
    }

    private void setMqttAccount(String ip, String account, String password, String topic) {
        try {
            if (blueCorsUtils != null) {
                blueCorsUtils.setMqtt2RTK(ip, account, password, topic);
                sendSuccess(generalCallbackContext, "mqttSet", "设置MQTT账号成功");
            } else {
                sendError(generalCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            sendError(generalCallbackContext, "设置MQTT账号错误: " + e.getMessage());
        }
    }

    private void getMqttAccount() {
        try {
            if (blueCorsUtils != null) {
                blueCorsUtils.getMqtt2RTK();
                sendSuccess(generalCallbackContext, "mqttGet", "正在获取MQTT账号信息");
            } else {
                sendError(generalCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            sendError(generalCallbackContext, "获取MQTT账号错误: " + e.getMessage());
        }
    }

    private void startGather() {
        try {
            if (blueCorsUtils != null) {
                blueCorsUtils.startGather();
                sendSuccess(generalCallbackContext, "gather", "已开启键盘按钮监听");
            } else {
                sendError(generalCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            sendError(generalCallbackContext, "开启键盘按钮监听错误: " + e.getMessage());
        }
    }

    private void sendBlueData(String data) {
        try {
            if (blueCorsUtils != null) {
                blueCorsUtils.rewriteBlue(data.getBytes());
                sendSuccess(generalCallbackContext, "dataSent", "数据发送成功");
            } else {
                sendError(generalCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            sendError(generalCallbackContext, "发送数据错误: " + e.getMessage());
        }
    }

    private void sendGgaToCors(String gga) {
        try {
            if (blueCorsUtils != null) {
                blueCorsUtils.sendGGATocors(gga);
                sendSuccess(generalCallbackContext, "ggaSent", "GGA数据发送成功");
            } else {
                sendError(generalCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            sendError(generalCallbackContext, "发送GGA数据错误: " + e.getMessage());
        }
    }
    private void startScan() {
        try {
            Log.d(TAG, "Native层: 开始执行扫描");
            if (blueCorsUtils != null) {
                Log.d(TAG, "Native层: 调用startBlueScan");
                blueCorsUtils.startBlueScan(activity, new BlueCorsUtils.OnBluEventListener() {
                    @Override
                    public void bluSlog(String msg) {
                        Log.d(TAG, "蓝牙日志: " + msg);
                    }
    
                    @Override
                    public void bluScanResult(GBlueBean gBlueBean) {
                        try {
                            if (gBlueBean == null) {
                                Log.e(TAG, "收到空的GBlueBean对象");
                                return;
                            }
    
                            String deviceName = gBlueBean.getName();
                            String deviceAddress = gBlueBean.getAddress();
                            
                            if (deviceName == null || deviceName.trim().isEmpty()) {
                                Log.d(TAG, "设备名称为空，跳过");
                                return;
                            }
    
                            Log.d(TAG, "发现设备 - 名称: " + deviceName + ", 地址: " + deviceAddress);
                            
                            JSONObject deviceInfo = new JSONObject();
                            deviceInfo.put("name", deviceName);
                            deviceInfo.put("address", deviceAddress);
                            
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
            Log.e(TAG, "扫描错误: " + e.getMessage());
            e.printStackTrace();
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
            Log.d(TAG, "Native层: 开始执行断开操作");
            if (blueCorsUtils != null) {
                // 添加更多状态检查
                boolean isConnected = blueCorsUtils.isConnected();
                Log.d(TAG, "Native层: blueCorsUtils状态 - 是否连接: " + isConnected);
                
                if (isConnected) {
                    // 发送断开连接状态
                    sendScanResult("disconnecting", null);
                    
                    // 执行断开
                    blueCorsUtils.disconnect();
                    Log.d(TAG, "Native层: 断开指令已发送");
                } else {
                    Log.w(TAG, "Native层: 设备未连接");
                    sendError(generalCallbackContext, "当前没有连接的设备");
                }
            } else {
                Log.e(TAG, "Native层: blueCorsUtils为空");
                sendError(generalCallbackContext, "蓝牙工具未初始化");
            }
        } catch (Exception e) {
            Log.e(TAG, "Native层: 断开连接错误", e);
            e.printStackTrace();
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
                Log.e(TAG, "创建JSON结果错误: " + e.getMessage());
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