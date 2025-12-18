import { spawn, exec } from 'child_process';

// 配置
const PORT = 5173;
const PACKAGE_NAME = "org.eu.freex.app"; // 你的包名

console.log(`🚀 [TouchHelper] Starting Dev Environment...`);

// 1. 启动 Vite
const vite = spawn('npm', ['run', 'dev'], { stdio: 'inherit', shell: true });

// 等待 Vite 启动一点点时间 (简单粗暴法)
setTimeout(() => {
  console.log(`\n🔌 [TouchHelper] Configuring ADB...`);

  // 2. ADB Reverse (端口转发)
  exec(`adb reverse tcp:${PORT} tcp:${PORT}`, (err) => {
    if (err) {
      console.error(`❌ ADB Reverse Failed: ${err.message}`);
      console.log("👉 Please connect your device and enable USB Debugging.");
      return;
    }
    console.log(`✅ Port Forwarding: PC:${PORT} <-> Phone:${PORT}`);

    // 3. 唤醒 App 加载页面
    const url = `http://localhost:${PORT}`;
    const cmd = `adb shell am broadcast -a org.eu.freex.LOAD_UI --es path "${url}"`;

    console.log(`📱 Waking up App...`);
    exec(cmd, (err) => {
      if (err) console.error(`❌ Wake up failed: ${err.message}`);
      else console.log(`✅ App should be loading ${url} now.`);
    });
  });
}, 2000); // 延迟2秒等待Vite启动

// 退出清理
process.on('SIGINT', () => {
  vite.kill();
  process.exit();
});
