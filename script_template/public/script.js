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
  async function fightLoop(config) {
    Thread.sleep(2e3);
    const color = config.monsterColor;
    log(`[Task] 开始找怪，颜色: ${color}`);
    const img = Device.capture();
    img.applyFilter({
      "Grayscale": {
        "mode": "WEIGHTED"
      }
    });
    img.applyFilter({
      "Binarization": {
        "mode": "Manual",
        threshold_min: 0,
        threshold_max: 72,
        is_rgb_avg: true,
        sauvola_k: 0.2,
        window_size: 15
      }
    });
    const segConfig = {
      "mode": "Projection",
      padding: 0,
      min_width: 10,
      min_height: 10,
      max_width: 0,
      max_height: 0,
      merge_distance: 0,
      start_x: 0,
      start_y: 0,
      cell_width: 32,
      cell_height: 32,
      col_count: 1,
      row_count: 1,
      col_gap: 0,
      row_gap: 0,
      split_rows: true,
      split_cols: true,
      projection_threshold: 128
    };
    const fontLibrary = {
      "本": "0001E0000000780000001E0000000780000001E0000000780000001E0000000780007FFFFFFF9FFFFFFFE7FFFFFFF9FFFFFFFE003DEF00000F79E000079E780001E79F0000F1E3C0007C78F8001E1E1E000F8787C007C1E0F801E0781F00F81E07E07C0780FC3F01E03F9FFFFFFFFFDFFFFEF9E7FFFF9C31FFFFE30000780000001E0000000780000001E0000000780000001E0000",
      "次": "0000000000C000000C000400C000700C0003C1C0000E1FFFE041FFFE0030006003000C0060C0C0060C0C00C0C1800C0C180000C000000E000600E000600E000C01F001C01B001803180300318070060C0600E060C01C070403803800F001C01C00070180002",
      "数": "010000021083002108300311820011302000100600FFFE600FFFE7FF078060C07E0E0C0D30E08391CE087101A18C1012180003318020011006001307FF81B07FF80E008180E018100C030300E01C601E007C01B003C0318077060C0C11C0E7803803C006001"
    };
    const result = img.ocrGrid(segConfig, fontLibrary, 0.8);
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
