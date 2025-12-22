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

  // src/scripts/Legend/config.ts
  var GameConfig = {
    // 全局 Config 对象由底层注入，ts声明在 global.d.ts
    monsterColor: Config.get("target_color") || "#FF0000",
    loopTimes: Config.getInt("loop_times") || 5,
    // 静态配置
    maps: [
      { name: "猪洞七层", x: 100, y: 200 },
      { name: "祖玛大厅", x: 150, y: 300 }
    ]
  };

  // src/scripts/Legend/tasks.ts
  async function fightLoop() {
    const color = GameConfig.monsterColor;
    log(`[Task] 开始找怪，颜色: ${color}`);
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

  // src/scripts/Legend/index.ts
  async function main() {
    log(`🚀 脚本启动1...`);
    while (true) {
      await fightLoop();
      log("休息 3 秒...");
      await Thread.sleep(3e3);
    }
  }
  return __toCommonJS(index_exports);
})();
//# sourceMappingURL=script.js.map
