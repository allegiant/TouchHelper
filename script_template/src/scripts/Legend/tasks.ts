import { type GameConfig } from './config';


export async function fightLoop(config: GameConfig) {
  const color = config.monsterColor;
  log(`[Task] 开始找怪，颜色: ${color}`);

  for (let i = 0; i < 3; i++) {
    // Colors 是全局对象
    const point = Colors.findColorPoint(color);
    if (point) {
      log(`Found monster at ${point[0]}, ${point[1]}`);
      Device.click(point[0], point[1]);
      await Thread.sleep(2000);
    } else {
      log("未发现怪物...");
      await Thread.sleep(1000);
    }
  }
}
