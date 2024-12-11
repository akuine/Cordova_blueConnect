const fs = require('fs');
const path = require('path');

module.exports = function(context) {
    // 获取平台路径
    const platformRoot = path.join(context.opts.projectRoot, 'platforms/android');
    const gradleFile = path.join(platformRoot, 'build.gradle');

    if (!fs.existsSync(gradleFile)) {
        console.log("找不到 build.gradle 文件");
        return;
    }

    let content = fs.readFileSync(gradleFile, 'utf-8');

    // 修改仓库配置
    content = content.replace(/jcenter\(\)/, '');
    content = content.replace(/google\(\)/, 
        `mavenCentral()
        google()
        maven {
            url "https://plugins.gradle.org/m2/"
        }`
    );

    fs.writeFileSync(gradleFile, content);
    console.log("成功修改 build.gradle");

    // 修改CordovaLib的build.gradle
    const cordovaLibGradle = path.join(platformRoot, 'CordovaLib/build.gradle');
    if (fs.existsSync(cordovaLibGradle)) {
        let cordovaContent = fs.readFileSync(cordovaLibGradle, 'utf-8');
        
        // 移除bintray相关配置
        cordovaContent = cordovaContent.replace(/classpath[\s\S]*?gradle-bintray-plugin[\s\S]*?\n/, '');
        cordovaContent = cordovaContent.replace(/apply plugin: 'com.jfrog.bintray'[\s\S]*?\n/, '');
        
        // 更新仓库
        cordovaContent = cordovaContent.replace(/jcenter\(\)/, '');
        cordovaContent = cordovaContent.replace(/google\(\)/, 
            `mavenCentral()
            google()
            maven {
                url "https://plugins.gradle.org/m2/"
            }`
        );

        fs.writeFileSync(cordovaLibGradle, cordovaContent);
        console.log("成功修改 CordovaLib/build.gradle");
    }
};