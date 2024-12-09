var exec = require('cordova/exec');

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
    addScanListener: function(listener) {
        if (typeof listener === 'function') {
            this.scanListeners.add(listener);
        }
    },

    /**
     * 移除扫描监听器
     * @param {Function} listener 监听器函数
     */
    removeScanListener: function(listener) {
        this.scanListeners.delete(listener);
    },

    /**
     * 添加连接状态监听器
     * @param {Function} listener 监听器函数
     */
    addConnectionListener: function(listener) {
        if (typeof listener === 'function') {
            this.connectionListeners.add(listener);
        }
    },

    /**
     * 移除连接状态监听器
     * @param {Function} listener 监听器函数
     */
    removeConnectionListener: function(listener) {
        this.connectionListeners.delete(listener);
    },

    /**
     * 通知所有扫描监听器
     * @param {Object} result 扫描结果
     */
    notifyScanListeners: function(result) {
        this.scanListeners.forEach(function(listener) {
            try {
                listener(result);
            } catch (error) {
                console.error('Error in scan listener:', error);
            }
        });
    },

    /**
     * 通知所有连接状态监听器
     * @param {Object} result 连接状态结果
     */
    notifyConnectionListeners: function(result) {
        this.connectionListeners.forEach(function(listener) {
            try {
                listener(result);
            } catch (error) {
                console.error('Error in connection listener:', error);
            }
        });
    },

    /**
     * 开始扫描设备
     * @param {Function} successCallback 成功回调函数，返回扫描结果
     * @param {Function} errorCallback 错误回调函数
     */
    startScan: function(successCallback, errorCallback) {
        if (this.isScanning) {
            errorCallback && errorCallback('扫描已在进行中');
            return;
        }

        this.isScanning = true;
        this.scanCallback = function(result) {
            if (result && result.type) {
                switch(result.type) {
                    case 'device':
                        // 扫描到设备
                        var deviceResult = {
                            type: 'device',
                            device: {
                                name: result.data.name,
                                address: result.data.address
                            }
                        };
                        successCallback && successCallback(deviceResult);
                        this.notifyScanListeners(deviceResult);
                        break;

                    case 'position':
                        // 位置信息
                        var positionResult = {
                            type: 'position',
                            position: result.data
                        };
                        successCallback && successCallback(positionResult);
                        break;

                    case 'rtk':
                        // RTK信息
                        var rtkResult = {
                            type: 'rtk',
                            data: result.data
                        };
                        successCallback && successCallback(rtkResult);
                        break;

                    case 'log':
                        // 日志信息
                        successCallback && successCallback({
                            type: 'log',
                            message: result.data
                        });
                        break;

                    case 'connecting':
                        // 连接中
                        var connectingResult = {
                            type: 'connecting',
                            status: result.data
                        };
                        successCallback && successCallback(connectingResult);
                        this.notifyConnectionListeners(connectingResult);
                        break;

                    case 'connected':
                        // 已连接
                        this.isConnected = true;
                        this.connectedDevice = result.data.device;
                        var connectedResult = {
                            type: 'connected',
                            status: result.data
                        };
                        successCallback && successCallback(connectedResult);
                        this.notifyConnectionListeners(connectedResult);
                        break;

                    case 'connectionLoss':
                        // 连接丢失
                        this.isConnected = false;
                        this.connectedDevice = null;
                        var lossResult = {
                            type: 'connectionLoss',
                            status: result.data
                        };
                        successCallback && successCallback(lossResult);
                        this.notifyConnectionListeners(lossResult);
                        break;

                    case 'disconnected':
                        // 已断开连接
                        this.isConnected = false;
                        this.connectedDevice = null;
                        var disconnectedResult = {
                            type: 'disconnected',
                            status: result.data
                        };
                        successCallback && successCallback(disconnectedResult);
                        this.notifyConnectionListeners(disconnectedResult);
                        break;

                    default:
                        // 其他状态信息
                        successCallback && successCallback({
                            type: result.type,
                            data: result.data
                        });
                }
            }
        }.bind(this);

        exec(this.scanCallback, 
            function(error) {
                this.isScanning = false;
                errorCallback && errorCallback(error);
                this.notifyScanListeners({
                    type: 'error',
                    error: error
                });
            }.bind(this), 
            'hxtBdPlugin', 
            'startScan', 
            []
        );
    },

    /**
     * 停止扫描
     * @param {Function} successCallback 成功回调函数
     * @param {Function} errorCallback 错误回调函数
     */
    stopScan: function(successCallback, errorCallback) {
        if (!this.isScanning) {
            errorCallback && errorCallback('没有正在进行的扫描');
            return;
        }

        exec(function(result) {
                this.isScanning = false;
                this.scanCallback = null;
                var stopResult = {
                    type: 'scanStopped',
                    message: result.data
                };
                successCallback && successCallback(stopResult);
                this.notifyScanListeners(stopResult);
            }.bind(this), 
            function(error) {
                this.isScanning = false;
                errorCallback && errorCallback(error);
                this.notifyScanListeners({
                    type: 'error',
                    error: error
                });
            }.bind(this), 
            'hxtBdPlugin', 
            'stopScan', 
            []
        );
    },

    /**
     * 连接设备
     * @param {string} deviceName 设备名称
     * @param {Function} successCallback 成功回调函数
     * @param {Function} errorCallback 错误回调函数
     */
    connect: function(deviceName, successCallback, errorCallback) {
        if (!deviceName) {
            var error = "设备名称不能为空";
            errorCallback && errorCallback(error);
            this.notifyConnectionListeners({
                type: 'error',
                error: error
            });
            return;
        }

        if (this.isConnected && this.connectedDevice === deviceName) {
            var error = "已经连接到该设备";
            errorCallback && errorCallback(error);
            this.notifyConnectionListeners({
                type: 'error',
                error: error
            });
            return;
        }

        exec(function(result) {
                if (result.type === 'connected') {
                    this.isConnected = true;
                    this.connectedDevice = deviceName;
                }
                successCallback && successCallback({
                    type: result.type,
                    data: result.data
                });
                this.notifyConnectionListeners(result);
            }.bind(this), 
            function(error) {
                errorCallback && errorCallback(error);
                this.notifyConnectionListeners({
                    type: 'error',
                    error: error
                });
            }.bind(this), 
            'hxtBdPlugin', 
            'connect', 
            [deviceName]
        );
    },

    /**
     * 断开设备连接
     * @param {Function} successCallback 成功回调函数
     * @param {Function} errorCallback 错误回调函数
     */
    disconnect: function(successCallback, errorCallback) {
        if (!this.isConnected) {
            var error = "当前没有连接的设备";
            errorCallback && errorCallback(error);
            this.notifyConnectionListeners({
                type: 'error',
                error: error
            });
            return;
        }

        exec(function(result) {
                this.isConnected = false;
                this.connectedDevice = null;
                successCallback && successCallback({
                    type: result.type,
                    data: result.data
                });
                this.notifyConnectionListeners(result);
            }.bind(this), 
            function(error) {
                errorCallback && errorCallback(error);
                this.notifyConnectionListeners({
                    type: 'error',
                    error: error
                });
            }.bind(this), 
            'hxtBdPlugin', 
            'disconnect', 
            []
        );
    },

    /**
     * 设置Cors账户
     * @param {string} username 用户名
     * @param {string} password 密码
     * @param {Function} successCallback 成功回调函数
     * @param {Function} errorCallback 错误回调函数
     */
    setCorsAccount: function(username, password, successCallback, errorCallback) {
        if (!username || !password) {
            errorCallback && errorCallback("用户名和密码不能为空");
            return;
        }

        if (!this.isConnected) {
            errorCallback && errorCallback("请先连接设备");
            return;
        }

        exec(function(result) {
                successCallback && successCallback({
                    type: 'corsSet',
                    data: result.data
                });
            },
            errorCallback,
            'hxtBdPlugin',
            'setCorsAccount',
            [username, password]
        );
    },

    /**
     * 获取Cors账户信息
     * @param {Function} successCallback 成功回调函数
     * @param {Function} errorCallback 错误回调函数
     */
    getCorsAccount: function(successCallback, errorCallback) {
        if (!this.isConnected) {
            errorCallback && errorCallback("请先连接设备");
            return;
        }

        exec(function(result) {
                successCallback && successCallback({
                    type: 'corsGet',
                    data: result.data
                });
            },
            errorCallback,
            'hxtBdPlugin',
            'getCorsAccount',
            []
        );
    },

    /**
     * 获取当前连接状态
     * @returns {boolean} 是否已连接
     */
    getConnectionStatus: function() {
        return this.isConnected;
    },

    /**
     * 获取当前连接的设备
     * @returns {string|null} 当前连接的设备名称
     */
    getConnectedDevice: function() {
        return this.connectedDevice;
    },

    /**
     * 获取当前扫描状态
     * @returns {boolean} 是否正在扫描
     */
    getScanningStatus: function() {
        return this.isScanning;
    },

    /**
     * 清理所有监听器
     */
    clearAllListeners: function() {
        this.scanListeners.clear();
        this.connectionListeners.clear();
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