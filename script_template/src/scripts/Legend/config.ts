export const GameConfig = {
  // 全局 Config 对象由底层注入，ts声明在 global.d.ts
  monsterColor: Config.get("target_color") || "#FF0000",
  loopTimes: Config.getInt("loop_times") || 5,

  // 静态配置
  maps: [
    { name: "猪洞七层", x: 100, y: 200 },
    { name: "祖玛大厅", x: 150, y: 300 }
  ]
}


