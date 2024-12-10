const fs = require("fs");
const path = require("path");

module.exports = function (context) {
  const platformRoot = path.join(context.opts.projectRoot, "platforms/android");
  const buildGradlePath = path.join(platformRoot, "app/build.gradle");

  if (fs.existsSync(buildGradlePath)) {
    const content = fs.readFileSync(buildGradlePath, "utf-8");
    if (
      !content.includes(
        "implementation(name: 'blue-releasev_20241125', ext: 'aar')"
      )
    ) {
      fs.appendFileSync(
        buildGradlePath,
        `
dependencies {
   implementation(name: 'blue-releasev_20241125', ext: 'aar')
}
`
      );
    }
  }
};
