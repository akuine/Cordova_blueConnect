document.addEventListener("deviceready", function () {
  console.log("Cordova is ready");

  const resultBox = document.getElementById("result");

  function callMethod(methodName, args = []) {
    console.log(window, "window");
    console.log(window.cordova, "window.cordova");
    const plugin = window.cordova?.plugins?.BeidouPositioningForWireProtection;
    if (plugin && typeof plugin[methodName] === "function") {
      plugin[methodName](
        (res) => {
          resultBox.textContent = `方法: ${methodName}\n返回值: ${JSON.stringify(
            res,
            null,
            2
          )}`;
          console.log(`方法: ${methodName}`, res);
        },
        (err) => {
          resultBox.textContent = `方法: ${methodName}\n错误: ${JSON.stringify(
            err,
            null,
            2
          )}`;
          console.error(`方法: ${methodName}`, err);
        },
        ...args
      );
    } else {
      resultBox.textContent = `方法 ${methodName} 不存在或不可调用`;
      console.error(`方法 ${methodName} 不存在或不可调用`);
    }
  }

  // 示例按钮绑定
  document
    .getElementById("startScanning")
    .addEventListener("click", () => callMethod("startScanning"));
  document
    .getElementById("stopScanning")
    .addEventListener("click", () => callMethod("stopScanning"));
  document.getElementById("connectDevice").addEventListener("click", () => {
    const deviceId = prompt("请输入设备 ID:");
    if (deviceId) callMethod("connectToDevice", [deviceId]);
  });
  document
    .getElementById("disconnectDevice")
    .addEventListener("click", () => callMethod("disconnectDevice"));
  document
    .getElementById("getCors2Rtk")
    .addEventListener("click", () => callMethod("getCors2Rtk"));
});
