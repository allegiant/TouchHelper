import { GameConfig } from "./config";
import { fightLoop } from "./tasks";

// 脚本主流程
export async function main() {
  log(`🚀 脚本启动...`);
  while (true) {
    await fightLoop();
    log("休息 3 秒...");
    await Thread.sleep(3000);
  }

}
