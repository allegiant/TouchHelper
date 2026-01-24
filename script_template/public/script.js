"use strict";
var GameScript = (() => {
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

  // src/scripts/Legend/index.ts
  var index_exports = {};
  __export(index_exports, {
    main: () => main
  });

  // src/scripts/Legend/tasks.ts
  async function fightLoop(conf) {
    Thread.sleep(2e3);
    const color = conf.monsterColor;
    log(`[Task] 开始找怪，颜色: ${color}`);
    const img = Device.capture();
    img.applyFilter({
      "binarization": {
        "mode": "manual",
        "thresholdMin": 0,
        "thresholdMax": 72,
        "isRgbAvg": true,
        "sauvolaK": 0.20000000298023224,
        "windowSize": 15
      }
    });
    img.applyFilter({
      "blackWhiteInvert": {
        "mode": "autoToWhiteBg"
      }
    });
    const config = {
      "mode": "connectedComp",
      "padding": 0,
      "minWidth": 10,
      "minHeight": 10,
      "maxWidth": 0,
      "maxHeight": 0,
      "mergeDistance": 0,
      "startX": 0,
      "startY": 0,
      "cellWidth": 32,
      "cellHeight": 32,
      "colCount": 1,
      "rowCount": 1,
      "colGap": 0,
      "rowGap": 0,
      "splitRows": true,
      "splitCols": true,
      "projectionThreshold": 128
    };
    const fontLibrary = {
      "1": "00781FFFFFFFFFFFF0008000000001FFFFFFFFFF800000000000007FFFFF80000000000000000001800000000000000000008000000007FF000000008000000003FF8000000080000000001FC0000000800000000007C0000000800000000007C0000000800000000007800000008000000000070000000080000000000F0000000080000000001E0000000080000000003C0000000080000000003800000000800000000070000000008000000000E0000000018000000000C000000000C0000000018000000000C0000000010000000000C0000000000000000000C0000000000000000000C0000000040000000000C00000000C0000000001C0000000080000000001C0000000180000000001C0000000100000000001C0000000300000000001C0000000300000000FE18000000078000007FFF1800000007E00007FFFF0800000003FF803FFF801C000000001FC3FFF80008000000000FFFFFC000080000000003FFF0000008000000000FFFE000000C000000007FFF8000000C00000007FFF38000000C0000003FFF0100000008000003FFF003C000000800001FFF8001C0000008000FFFFC0001E0000008003FFFF80001E0000008001FF8000001E00000080000C0000001E0000008000000000000E0000008000000000001E0000008000000000001E0000008000000000001E0000008000000000001E0000008000000000003C0000008000000000003C0000008000000000007C00000080000000000078000000800000001000F8000001800000001801F0000001800000001801E0000001800000000C03E0000001800000000E03C00000018000000007078000000180000000070F8000000180000000031F000000018000000003BF000000018000000001FE000000018000000001FC000000018000000001FC000000018000000000F800000001C3FF800000F0000000019FFFFFF8007800000001FFFFFFFFFFF80001FF83"
    };
    const result = img.ocrGrid(config, fontLibrary, 0.8);
    log(`识别结果: ${result}`);
    for (let i = 0; i < 3; i++) {
      const point = Colors.findColorPoint(color);
      if (point) {
        log(`Found monster at ${point[0]}, ${point[1]}`);
        Device.click(point[0], point[1]);
        await Thread.sleep(2e3);
      } else {
        log("未发现怪物...");
        await Thread.sleep(1e3);
      }
    }
  }

  // src/scripts/Legend/config.ts
  var DEFAULT_CONFIG = {
    loopTimes: 5,
    monsterColor: "#FF0000",
    maps: [
      { id: 101, name: "猪洞七层", x: 100, y: 200 },
      { id: 102, name: "祖玛大厅", x: 150, y: 300 }
    ]
  };

  // src/scripts/Legend/index.ts
  async function main() {
    const config = typeof GameSettings !== "undefined" ? GameSettings : DEFAULT_CONFIG;
    log(`🚀 脚本启动...`);
    while (true) {
      await fightLoop(config);
      log("休息 3 秒...");
      await Thread.sleep(3e3);
    }
  }
  return __toCommonJS(index_exports);
})();
//# sourceMappingURL=script.js.map
