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
    private String[] permissions = {
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
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
            Log.d(TAG, "Plugin initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing plugin: " + e.getMessage());
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
            ActivityCompat.requestPermissions(activity, permissionsNeeded.toArray(new String[0]), 1);
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, "Executing action: " + action);

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
                Log.d(TAG, "Invalid action: " + action);
                return false;
        }
    }

    private void startScan() {
        try {
            if (blueCorsUtils != null) {
                Log.d(TAG, "Starting scan operation");
                blueCorsUtils.startBlueScan(activity, new BlueCorsUtils.OnBluEventListener() {
                    @Override
                    public void bluSlog(String msg) {
                        Log.d(TAG, "bluSlog: " + msg);
                        sendScanResult("log", msg);
                    }

                    @Override
                    public void bluScanResult(GBlueBean gBlueBean) {
                        try {
                            if (gBlueBean == null) {
                                Log.e(TAG, "Received null GBlueBean object");
                                return;
                            }

                            Log.d(TAG, "Processing scan result for device: " + gBlueBean.getName());
                            JSONObject deviceInfo = new JSONObject();
                            deviceInfo.put("name", gBlueBean.getName());
                            deviceInfo.put("address", gBlueBean.getAddress());
                            
                            Log.d(TAG, "Sending device info to JS: " + deviceInfo.toString());
                            sendScanResult("device", deviceInfo);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error processing scan result: " + e.getMessage());
                            sendError(scanCallbackContext, "Error processing scan result: " + e.getMessage());
                        }
                    }

                    @Override
                    public void bluRevice(GPositionBean gPositionBean) {
                        try {
                            if (gPositionBean == null) {
                                Log.e(TAG, "Received null GPositionBean object");
                                return;
                            }

                            JSONObject positionInfo = new JSONObject();
                            positionInfo.put("position", gPositionBean.toString());
                            Log.d(TAG, "Position received: " + positionInfo.toString());
                            sendScanResult("position", positionInfo);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error processing position data: " + e.getMessage());
                            sendError(scanCallbackContext, "Error processing position data: " + e.getMessage());
                        }
                    }

                    @Override
                    public void bluRevice(Hs hs) {
                        try {
                            if (hs == null) {
                                Log.e(TAG, "Received null Hs object");
                                return;
                            }

                            Log.d(TAG, "RTK data received: " + hs.toString());
                            sendScanResult("rtk", hs.toString());
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing RTK data: " + e.getMessage());
                            sendError(scanCallbackContext, "Error processing RTK data: " + e.getMessage());
                        }
                    }

                    @Override
                    public void bluConnecting(String s, int i) {
                        try {
                            JSONObject connectingInfo = new JSONObject();
                            connectingInfo.put("device", s);
                            connectingInfo.put("status", i);
                            Log.d(TAG, "Connecting to device: " + s);
                            sendScanResult("connecting", connectingInfo);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error processing connecting status: " + e.getMessage());
                            sendError(scanCallbackContext, "Error processing connecting status: " + e.getMessage());
                        }
                    }

                    @Override
                    public void bluConnectSuccess(String s, int i) {
                        try {
                            JSONObject successInfo = new JSONObject();
                            successInfo.put("device", s);
                            successInfo.put("status", i);
                            Log.d(TAG, "Connected successfully to: " + s);
                            sendScanResult("connected", successInfo);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error processing connect success status: " + e.getMessage());
                            sendError(scanCallbackContext, "Error processing connect success status: " + e.getMessage());
                        }
                    }

                    @Override
                    public void bluConnectLoss(String s, int i) {
                        try {
                            JSONObject lossInfo = new JSONObject();
                            lossInfo.put("device", s);
                            lossInfo.put("status", i);
                            Log.e(TAG, "Connection lost: " + s);
                            sendScanResult("connectionLoss", lossInfo);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error processing connection loss status: " + e.getMessage());
                            sendError(scanCallbackContext, "Error processing connection loss status: " + e.getMessage());
                        }
                    }

                    @Override
                    public void bluDisconnect(int i) {
                        try {
                            JSONObject disconnectInfo = new JSONObject();
                            disconnectInfo.put("status", i);
                            Log.d(TAG, "Device disconnected");
                            sendScanResult("disconnected", disconnectInfo);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error processing disconnect status: " + e.getMessage());
                            sendError(scanCallbackContext, "Error processing disconnect status: " + e.getMessage());
                        }
                    }
                });
            } else {
                Log.e(TAG, "BlueCorsUtils not initialized");
                sendError(scanCallbackContext, "BlueCorsUtils not initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Start scan error: " + e.getMessage());
            sendError(scanCallbackContext, "Start scan error: " + e.getMessage());
        }
    }

    private void stopScan() {
        try {
            if (blueCorsUtils != null) {
                blueCorsUtils.stopBlueScan();
                Log.d(TAG, "Scan stopped successfully");
                sendSuccess(generalCallbackContext, "scanStopped", "Scan stopped successfully");
            } else {
                Log.e(TAG, "BlueCorsUtils not initialized");
                sendError(generalCallbackContext, "BlueCorsUtils not initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Stop scan error: " + e.getMessage());
            sendError(generalCallbackContext, "Stop scan error: " + e.getMessage());
        }
    }

    private void connect(String deviceName) {
        try {
            if (blueCorsUtils != null) {
                Log.d(TAG, "Attempting to connect to device: " + deviceName);
                blueCorsUtils.connectBlueName(deviceName);
            } else {
                Log.e(TAG, "BlueCorsUtils not initialized");
                sendError(generalCallbackContext, "BlueCorsUtils not initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Connect error: " + e.getMessage());
            sendError(generalCallbackContext, "Connect error: " + e.getMessage());
        }
    }

    private void disconnect() {
        try {
            if (blueCorsUtils != null) {
                Log.d(TAG, "Disconnecting device");
                blueCorsUtils.disconnect();
            } else {
                Log.e(TAG, "BlueCorsUtils not initialized");
                sendError(generalCallbackContext, "BlueCorsUtils not initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Disconnect error: " + e.getMessage());
            sendError(generalCallbackContext, "Disconnect error: " + e.getMessage());
        }
    }

    private void setCorsAccount(String username, String password) {
        try {
            if (blueCorsUtils != null) {
                Log.d(TAG, "Setting CORS account for user: " + username);
                blueCorsUtils.setCors2Rtk(username, password);
                sendSuccess(generalCallbackContext, "corsSet", "CORS account set successfully");
            } else {
                Log.e(TAG, "BlueCorsUtils not initialized");
                sendError(generalCallbackContext, "BlueCorsUtils not initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Set CORS account error: " + e.getMessage());
            sendError(generalCallbackContext, "Set CORS account error: " + e.getMessage());
        }
    }

    private void getCorsAccount() {
        try {
            if (blueCorsUtils != null) {
                Log.d(TAG, "Getting CORS account information");
                blueCorsUtils.getCors2Rtk();
            } else {
                Log.e(TAG, "BlueCorsUtils not initialized");
                sendError(generalCallbackContext, "BlueCorsUtils not initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Get CORS account error: " + e.getMessage());
            sendError(generalCallbackContext, "Get CORS account error: " + e.getMessage());
        }
    }

    private void sendScanResult(String type, Object data) {
        if (scanCallbackContext != null) {
            try {
                JSONObject result = new JSONObject();
                result.put("type", type);
                result.put("data", data);
                
                Log.d(TAG, "Sending scan result: " + result.toString());
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
                pluginResult.setKeepCallback(true);
                scanCallbackContext.sendPluginResult(pluginResult);
            } catch (JSONException e) {
                Log.e(TAG, "Error creating scan result: " + e.getMessage());
                sendError(scanCallbackContext, "Error creating scan result: " + e.getMessage());
            }
        } else {
            Log.e(TAG, "Scan callback context is null");
        }
    }

    private void sendSuccess(CallbackContext context, String type, Object data) {
        if (context != null) {
            try {
                JSONObject result = new JSONObject();
                result.put("type", type);
                result.put("data", data);
                Log.d(TAG, "Sending success result: " + result.toString());
                context.success(result);
            } catch (JSONException e) {
                Log.e(TAG, "Error creating success result: " + e.getMessage());
                context.error("Error creating result: " + e.getMessage());
            }
        } else {
            Log.e(TAG, "Callback context is null");
        }
    }

    private void sendError(CallbackContext context, String message) {
        if (context != null) {
            Log.e(TAG, "Error: " + message);
            context.error(message);
        } else {
            Log.e(TAG, "Callback context is null for error: " + message);
        }
    }
}