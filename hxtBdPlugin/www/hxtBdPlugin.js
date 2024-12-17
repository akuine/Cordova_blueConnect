var exec = require("cordova/exec");

var hxtBdPlugin = {
    // 状态管理
    scanCallback: null,
    isScanning: false,
    isConnected: false,
    connectedDevice: null,
    scanListeners: new Set(),
    connectionListeners: new Set(),

    /**
     * 添加扫描监听器
     * @param {Function} listener 监听器函数
     */
    addScanListener: function (listener) {
        if (typeof listener === "function") {
            this.scanListeners.add(listener);
        }
    },

    /**
     * 删除扫描监听器
     * @param {Function} listener 监听器函数
     */
    removeScanListener: function (listener) {
        this.scanListeners.delete(listener);
    },

    /**
     * 连接状态监听器
     * @param {Function} listener 监听器添加器函数
     */
    addConnectionListener: function (listener) {
        if (typeof listener === "function") {
            this.connectionListeners.add(listener);
        }
    },

    /**
     * 删除连接状态监听器
     * @param {Function} listener 监听器函数
     */
    removeConnectionListener: function (listener) {
        this.connectionListeners.delete(listener);
    },

    /**
     * 通知所有扫描监听器
     * @param {Object} result 扫描结果
     */
    notifyScanListeners: function (result) {
        this.scanListeners.forEach(function (listener) {
            try {
                listener(result);
            } catch (error) {
                console.error("扫描监听器出错:", error);
            }
        });
    },

    /**
     * 通知所有连接状态监听器
     * @param {Object} result 连接状态结果
     */
    notifyConnectionListeners: function (result) {
        this.connectionListeners.forEach(function (listener) {
            try {
                listener(result);
            } catch (error) {
                console.error("连接监听器出错:", error);
            }
        });
    },

    /**
     * 开始扫描设备
     * @param {Function} successCallback 成功回调函数，返回扫描结果
     * @param {Function} errorCallback   错误回调函数
     */
    startScan: function (successCallback, errorCallback) {
        console.log("JS层：开始扫描");
        if (this.isScanning) {
            console.error("JS层: 扫描已经在进行中");
            errorCallback && errorCallback("扫描已在进行中");
            return;
        }
        this.isScanning = true;
        this.scanCallback = function (result) {
            console.log("JS层:收到扫描结果", result);
            if (result && result.type) {
                switch (result.type) {
                    case "device":
                        // 发现设备
                        var deviceResult = {
                            type: "device",
                            device: {
                                name: result.data.name,
                                address: result.data.address,
                            },
                        };
                        successCallback && successCallback(deviceResult);
                        break;

                    case "connected":
                        this.isConnected = true; // 设置连接状态
                        console.log("JS层: 设备已连接，更新连接状态为true");
                        successCallback && successCallback(result);
                        break;

                    case "position":
                        // 接收位置信息说明设备已连接
                        this.isConnected = true;
                        console.log("JS层:接收位置信息，更新连接状态为true");
                        successCallback && successCallback(result);
                        break;

                    case "rtk":
                        // 接收RTK信息说明设备已连接
                        this.isConnected = true;
                        console.log("JS层:接收RTK信息，更新连接状态为true");
                        successCallback && successCallback(result);
                        break;

                    case "disconnected":
                        this.isConnected = false;
                        console.log("JS层: 设备断开，更新连接状态为 false");
                        successCallback && successCallback(result);
                        break;

                    case "connectionLoss":
                        this.isConnected = false;
                        console.log("JS层:连接丢失，更新连接状态为 false");
                        successCallback && successCallback(result);
                        break;
                        
                    default:
                        successCallback && successCallback(result);
                }
            }
        }.bind(this);

        exec(
            this.scanCallback,
            function (error) {
                console.error("JS层: 扫描错误", error);
                this.isScanning = false;
                errorCallback && errorCallback(error);
            }.bind(this),
            "hxtBdPlugin",
            "startScan",
            []
        );
    },

    /**
     * 停止扫描
     * @param {Function} successCallback 成功回调函数
     * @param {Function} errorCallback   错误回调函数
     */
    stopScan: function (successCallback, errorCallback) {
        if (!this.isScanning) {
            errorCallback && errorCallback("没有正在进行的扫描");
            return;
        }

        exec(
            function (result) {
                this.isScanning = false;
                this.scanCallback = null;
                var stopResult = {
                    type: "scanStopped",
                    message: result.data,
                };
                successCallback && successCallback(stopResult);
                this.notifyScanListeners(stopResult);
            }.bind(this),
            function (error) {
                this.isScanning = false;
                errorCallback && errorCallback(error);
                this.notifyScanListeners({
                    type: "error",
                    error: error,
                });
            }.bind(this),
            "hxtBdPlugin",
            "stopScan",
            []
        );
    },

    /**
     * 连接设备
     * @param {string}   deviceName       设备名称
     * @param {Function} successCallback  成功回调函数
     * @param {Function} errorCallback    错误回调函数
     */
    connect: function (deviceName, successCallback, errorCallback) {
        console.log("JS层:开始连接设备:", deviceName);
        if (!deviceName) {
            console.error("JS层: 设备名称为空");
            errorCallback && errorCallback("设备名称不能为空");
            return;
        }

        exec(
            function (result) {
                console.log("JS层:连接设备成功结果:", result);
                if (result.type === "connected") {
                    console.log("JS层:设备已连接");
                    this.isConnected = true;
                    this.connectedDevice = deviceName;
                }
                successCallback && successCallback(result);
                this.notifyConnectionListeners(result);
            }.bind(this),
            function (error) {
                console.error("JS层: 连接错误:", error);
                errorCallback && errorCallback(error);
                this.notifyConnectionListeners({ type: "error", error: error });
            }.bind(this),
            "hxtBdPlugin",
            "connect",
            [deviceName]
        );
    },

    /**
     * 断开设备连接
     * @param {Function} successCallback 成功回调函数
     * @param {Function} errorCallback   错误回调函数
     */
    disconnect: function (successCallback, errorCallback) {
        console.log("JS层: 执行断开连接");
        exec(
            function (result) {
                console.log("JS层: 断开连接成功回调", result);
                successCallback && successCallback(result);
            },
            function (error) {
                console.log("JS层: 断开连接错误回调", error);
                errorCallback && errorCallback(error);
            },
            "hxtBdPlugin",
            "disconnect",
            []
        );
    },

    /**
     * 设置CORS账户
     * @param {string}   username        用户名
     * @param {string}   password        密码
     * @param {Function} successCallback 成功回调函数
     * @param {Function} errorCallback   错误回调函数
     */
    setCorsAccount: function (username, password, successCallback, errorCallback) {
        if (!username || !password) {
            errorCallback && errorCallback("用户名和密码不能为空");
            return;
        }
        if (!this.isConnected) {
            errorCallback && errorCallback("请先连接设备");
            return;
        }

        exec(
            function (result) {
                successCallback && successCallback({
                    type: "corsSet",
                    data: result.data,
                });
            },
            errorCallback,
            "hxtBdPlugin",
            "setCorsAccount",
            [username, password]
        );
    },
    setCorsAccount2: function (username, password, successCallback, errorCallback) {
        if (!username || !password) {
            errorCallback && errorCallback("用户名和密码不能为空");
            return;
        }
        if (!this.isConnected) {
            errorCallback && errorCallback("请先连接设备");
            return;
        }

        exec(
            function (result) {
                successCallback && successCallback({
                    type: "corsSet",
                    data: result.data,
                });
            },
            errorCallback,
            "hxtBdPlugin",
            "setCorsAccount2",
            [username, password]
        );
    },

    /**
     * 获取CORS账户信息
     * @param {Function} successCallback 成功回调函数
     * @param {Function} errorCallback   错误回调函数
     */
    getCorsAccount: function (successCallback, errorCallback) {
        if (!this.isConnected) {
            errorCallback && errorCallback("请先连接设备");
            return;
        }

        exec(
            function (result) {
                successCallback && successCallback({
                    type: "corsGet",
                    data: result.data,
                });
            },
            errorCallback,
            "hxtBdPlugin",
            "getCorsAccount",
            []
        );
    },

    /**
     * 获取当前连接状态
     * @returns {boolean} 是否已连接
     */
    getConnectionStatus: function () {
        return this.isConnected;
    },

    /**
     * 获取当前连接的设备
     * @returns {string|null} 当前连接的设备名称
     */
    getConnectedDevice: function () {
        return this.connectedDevice;
    },

    /**
     * 获取当前扫描状态
     * @returns {boolean} 是否正在扫描
     */
    getScanningStatus: function () {
        return this.isScanning;
    },

    /**
     * 清理所有监听器
     */
    clearAllListeners: function () {
        this.scanListeners.clear();
        this.connectionListeners.clear();
    },

    /**
     * 获取北斗卡号
     * @param {Function} successCallback 成功回调
     * @param {Function} errorCallback   错误回调
     */
    getBdICR: function (successCallback, errorCallback) {
        console.log("JS层: 开始获取北斗卡号");
        exec(
            function (result) {
                console.log("JS层: 获取北斗卡号结果:", result);
                successCallback && successCallback(result);
            },
            function (error) {
                console.error("JS层: 获取北斗卡号错误：", error);
                errorCallback && errorCallback(error);
            },
            "hxtBdPlugin",
            "getBdICR",
            []
        );
    },

    /**
     * （重复定义的getCorsAccount，前面已定义过，如不需要可删除）
     * 获取CORS账号信息(与上面的getCorsAccount逻辑重复)
     */
    // getCorsAccount: function (successCallback, errorCallback) {
    //     console.log("JS层: 获取CORS账号");
    //     exec(
    //         function (result) {
    //             console.log("JS层: 获取CORS账号结果:", result);
    //             successCallback && successCallback(result);
    //         },
    //         function (error) {
    //             console.error("JS层: 获取CORS账号错误：", error);
    //             errorCallback && errorCallback(error);
    //         },
    //         "hxtBdPlugin",
    //         "getCorsAccount",
    //         []
    //     );
    // },

    /**
     * 设置惯性导蓝牙到RTK
     * @param {string}   insBlueName     惯性导蓝牙名称
     * @param {Function} successCallback 成功回调
     * @param {Function} errorCallback   错误回调
     */
    setInsBlueToRtk: function (insBlueName, successCallback, errorCallback) {
        console.log("JS层:设置惯性导蓝牙", insBlueName);
        exec(
            function (result) {
                console.log("JS层: 设置惯性导蓝牙结果:", result);
                successCallback && successCallback(result);
            },
            function (error) {
                console.error("JS层: 设置惯性导蓝牙错误:", error);
                errorCallback && errorCallback(error);
            },
            "hxtBdPlugin",
            "setInsBlueToRtk",
            [insBlueName]
        );
    },

    /**
     * 获取惯导蓝牙名称
     * @param {Function} successCallback 成功回调
     * @param {Function} errorCallback   错误回调
     */
    getInsBlueFromRtk: function (successCallback, errorCallback) {
        console.log("JS层: 获取惯性导蓝牙名称");
        exec(
            function (result) {
                console.log("JS层:获取惯性导蓝牙名称结果:", result);
                successCallback && successCallback(result);
            },
            function (error) {
                console.error("JS层:获取惯性导蓝牙名称错误:", error);
                errorCallback && errorCallback(error);
            },
            "hxtBdPlugin",
            "getInsBlueFromRtk",
            []
        );
    },

    /**
     * 设置MQTT账号信息
     * @param {Object}   mqttConfig      MQTT配置信息
     * @param {Function} successCallback 成功回调
     * @param {Function} errorCallback   错误回调
     */
    setMqttAccount: function (mqttConfig, successCallback, errorCallback) {
        console.log("JS层: 设置MQTT账号", mqttConfig);
        exec(
            function (result) {
                console.log("JS层: 设置MQTT账号结果:", result);
                successCallback && successCallback(result);
            },
            function (error) {
                console.error("JS层: 设置MQTT账号错误：", error);
                errorCallback && errorCallback(error);
            },
            "hxtBdPlugin",
            "setMqttAccount",
            [mqttConfig.ip, mqttConfig.account, mqttConfig.password, mqttConfig.topic]
        );
    },

    /**
     * 获取MQTT账号信息
     * @param {Function} successCallback 成功回调
     * @param {Function} errorCallback   错误回调
     */
    getMqttAccount: function (successCallback, errorCallback) {
        console.log("JS层: 获取MQTT账号信息");
        exec(
            function (result) {
                console.log("JS层: 获取MQTT账号信息结果:", result);
                successCallback && successCallback(result);
            },
            function (error) {
                console.error("JS层: 获取MQTT账号信息错误:", error);
                errorCallback && errorCallback(error);
            },
            "hxtBdPlugin",
            "getMqttAccount",
            []
        );
    },

    /**
     * 开启键盘按钮监听
     * @param {Function} successCallback 成功回调
     * @param {Function} errorCallback   错误回调
     */
    startGather: function (successCallback, errorCallback) {
        console.log("JS层: 开启键盘按钮监听");
        exec(
            function (result) {
                console.log("JS层: 开启键盘按键监听结果:", result);
                successCallback && successCallback(result);
            },
            function (error) {
                console.error("JS层: 开启键盘监听错误:", error);
                errorCallback && errorCallback(error);
            },
            "hxtBdPlugin",
            "startGather",
            []
        );
    },

    /**
     * 发送数据到蓝牙设备
     * @param {string}   data            要发送的数据
     * @param {Function} successCallback 成功回调
     * @param {Function} errorCallback   错误回调
     */
    sendBlueData: function (data, successCallback, errorCallback) {
        console.log("JS层: 发送数据到蓝牙", data);
        exec(
            function (result) {
                console.log("JS层: 发送数据结果:", result);
                successCallback && successCallback(result);
            },
            function (error) {
                console.error("JS层: 发送数据错误: ", error);
                errorCallback && errorCallback(error);
            },
            "hxtBdPlugin",
            "sendBlueData",
            [data]
        );
    },

    /**
     * 发送GGA数据到CORS
     * @param {string}   gga             GGA数据
     * @param {Function} successCallback 成功回调
     * @param {Function} errorCallback   错误回调
     */
    sendGgaToCors: function (gga, successCallback, errorCallback) {
        console.log("JS层:发送GGA到CORS", gga);
        exec(
            function (result) {
                console.log("JS层:发送GGA结果:", result);
                successCallback && successCallback(result);
            },
            function (error) {
                console.error("JS层:发送GGA错误:", error);
                errorCallback && errorCallback(error);
            },
            "hxtBdPlugin",
            "sendGgaToCors",
            [gga]
        );
    }
};

// 注册插件到全局对象
if (!window.plugins) {
    window.plugins = {};
}
if (!window.plugins.hxtBdPlugin) {
    window.plugins.hxtBdPlugin = hxtBdPlugin;
}

module.exports = hxtBdPlugin;
