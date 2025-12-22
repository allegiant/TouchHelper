export interface MapItem {
  id: number,
  name: string,
  x: number,
  y: number,
}

export interface GameConfig {
  monsterColor: string,
  loopTimes: number,
  maps: MapItem[],
}

export const DEFAULT_CONFIG = {
  loopTimes: 5,
  monsterColor: "#FF0000",
  maps: [
    { id: 101, name: "猪洞七层", x: 100, y: 200 },
    { id: 102, name: "祖玛大厅", x: 150, y: 300 }
  ]
} satisfies GameConfig;
