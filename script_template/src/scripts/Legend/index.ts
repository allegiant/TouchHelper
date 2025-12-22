import { fightLoop } from "./tasks";
import { type GameConfig, DEFAULT_CONFIG } from "./config";

declare const GameSettings: GameConfig;

//脚本主流程
export async function main() {
  const config = (typeof GameSettings !== 'undefined') ? GameSettings : DEFAULT_CONFIG;
  log(`🚀 脚本启动...`);
  while (true) {
    await fightLoop(config);
    log("休息 3 秒...");
    await Thread.sleep(3000);
  }

}
