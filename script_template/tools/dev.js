import * as esbuild from 'esbuild';
import { spawn, exec } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

// ==========================================
// 1. 配置区域
// ==========================================
const __dirname = path.dirname(fileURLToPath(import.meta.url));

// 游戏脚本配置
const ACTIVE_GAME = 'Legend'; // 对应 src/scripts/Legend
const SRC_ENTRY = path.join(__dirname, `../src/scripts/${ACTIVE_GAME}/index.ts`);
const OUT_FILE = path.join(__dirname, '../public/script.js');

// ADB 与 Vite 配置
const PORT = 5173;
const PACKAGE_NAME = "org.eu.freex.app";

// ==========================================
// 2. 核心逻辑
// ==========================================

async function startDev() {
  console.log(`🚀 [TouchHelper] Starting Dev Environment for [${ACTIVE_GAME}]...`);

  // --- A. 启动 esbuild 监听模式 ---
  if (!fs.existsSync(SRC_ENTRY)) {
    console.error(`❌ 找不到入口文件: ${SRC_ENTRY}`);
    process.exit(1);
  }

  try {
    // 创建构建上下文
    const ctx = await esbuild.context({
      entryPoints: [SRC_ENTRY],
      outfile: OUT_FILE,
      bundle: true,
      format: 'iife',
      globalName: 'GameScript',
      platform: 'browser',
      target: ['es2020'],
      charset: 'utf8',
      minify: false, // 开发模式不压缩，方便调试
      sourcemap: true, // 开发模式开启 sourcemap
      plugins: [{
        name: 'rebuild-notify',
        setup(build) {
          build.onEnd(result => {
            if (result.errors.length > 0) {
              console.error(`❌ 脚本编译失败`);
            } else {
              console.log(`⚡ 脚本更新成功: public/script.js`);
            }
          });
        },
      }],
    });

    // 开启监听：文件变动自动重新打包
    await ctx.watch();
    console.log(`👀 正在监听脚本文件变动...`);

  } catch (e) {
    console.error("❌ esbuild 初始化失败:", e);
    process.exit(1);
  }

  // --- B. 启动 Vite ---
  // 直接调用 vite 命令，而不是 npm run dev，防止循环调用
  // Windows下需要用 vite.cmd，Linux/Mac 用 vite
  const viteCmd = process.platform === 'win32' ? 'vite.cmd' : 'vite';

  // 使用 npx 确保使用项目内安装的 vite
  const vite = spawn('npx', ['vite'], {
    stdio: 'inherit',
    shell: true
  });

  // --- C. 配置 ADB ---
  setTimeout(() => {
    console.log(`\n🔌 [TouchHelper] Configuring ADB...`);

    exec(`adb reverse tcp:${PORT} tcp:${PORT}`, (err) => {
      if (err) {
        console.error(`❌ ADB Reverse Failed: ${err.message}`);
        console.log("👉 请连接手机并开启 USB 调试");
        // 这里不 return，因为即连不上手机，网页版也可以调试
      } else {
        console.log(`✅ 端口映射成功: PC:${PORT} <-> Phone:${PORT}`);
        wakeUpApp();
      }
    });
  }, 2000);

  // 退出清理
  process.on('SIGINT', () => {
    vite.kill();
    esbuild.stop(); // 停止 esbuild
    process.exit();
  });
}

function wakeUpApp() {
  const url = `http://localhost:${PORT}`;
  const cmd = `adb shell am broadcast -a org.eu.freex.LOAD_UI --es path "${url}"`;

  console.log(`📱 正在唤醒 App 加载: ${url}`);
  exec(cmd, (err) => {
    if (err) console.error(`❌ 唤醒失败: ${err.message}`);
    else console.log(`✅ 广播发送成功`);
  });
}

startDev();
