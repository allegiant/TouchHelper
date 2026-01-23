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

  // 使用 npx 确保使用项目内安装的 vite
  const vite = spawn('npx', ['vite'], {
    stdio: 'inherit',
    shell: true
  });

  // --- C. 配置 ADB ---
  // --- C. 初始化连接并开始监听 ---
  // 首次启动先执行一次
  setTimeout(() => {
    setupAndroidConnection();
    startLogcatMonitor(); // 🔥 启动日志监听
    setupManualTrigger(); // 🔥 启动按键监听
  }, 2000);

  // 退出清理
  process.on('SIGINT', () => {
    vite.kill();
    esbuild.stop(); // 停止 esbuild
    process.exit();
  });
}

// 防抖计时器，防止日志重复触发
let debounceTimer = null;

/**
 * 执行 ADB 连接与唤醒
 */
function setupAndroidConnection() {
  console.log(`\n🔌 [TouchHelper] Configuring ADB connection...`);

  // 1. 端口映射
  exec(`adb reverse tcp:${PORT} tcp:${PORT}`, (err) => {
    if (err) {
      console.error(`❌ ADB Reverse Failed: ${err.message}`);
      console.log("👉 请连接手机并开启 USB 调试");
    } else {
      console.log(`✅ 端口映射成功: PC:${PORT} <-> Phone:${PORT}`);
      wakeUpApp();
    }
  });
}

function wakeUpApp() {
  const url = `http://localhost:${PORT}`;
  const cmd = `adb shell am broadcast -a org.eu.freex.LOAD_UI --es path "${url}"`;

  console.log(`📱 正在向 App 发送重载广播...,${url}`);
  exec(cmd, (err) => {
    if (err) console.error(`❌ 唤醒失败: ${err.message}`);
    else console.log(`✅ [Success] App 已被通知加载脚本`);
  });
}

/**
 * 监听 Logcat，检测 App 启动
 */
function startLogcatMonitor() {
  console.log(`🛰️  已启动 Logcat 监听 (App 重启时将自动重连)`);

  // 先清除旧日志，避免启动时读取历史记录导致误判
  exec('adb logcat -c');

  // 启动 logcat 进程
  // 我们只过滤 ActivityManager 的日志，减少处理量
  const logcat = spawn('adb', ['logcat', '-v', 'time', 'ActivityManager:I', '*:S'], {
    stdio: ['ignore', 'pipe', 'ignore']
  });

  logcat.stdout.on('data', (data) => {
    const logStr = data.toString();

    // 🎯 核心逻辑：检测 ActivityManager 启动了我们的包名
    // 日志示例: ActivityManager: Start proc 12345:org.eu.freex.app/u0a123 for activity ...
    if (logStr.includes('Start proc') && logStr.includes(PACKAGE_NAME)) {
      triggerReconnectWithDebounce();
    }
  });

  logcat.on('error', (err) => {
    console.error('Logcat 监听出错 (可能设备断开):', err.message);
  });
}

/**
 * 监听键盘 'r' 键，允许手动重连
 */
function setupManualTrigger() {
  console.log(`⌨️  提示: 在终端按 'r' 键可手动触发重连\n`);

  process.stdin.setRawMode(true);
  process.stdin.resume();
  process.stdin.setEncoding('utf8');

  process.stdin.on('data', (key) => {
    // ctrl+c 退出
    if (key === '\u0003') {
      process.emit('SIGINT');
      process.exit();
    }
    // 按 'r' 重连
    if (key === 'r') {
      console.log(`\n👉 手动触发重连...`);
      setupAndroidConnection();
    }
  });
}

/**
 * 防抖函数：防止一次启动触发多次
 */
function triggerReconnectWithDebounce() {
  if (debounceTimer) {
    clearTimeout(debounceTimer);
  }
  debounceTimer = setTimeout(() => {
    console.log(`\n♻️  检测到 App 启动，正在自动重连...`);
    setupAndroidConnection();
    debounceTimer = null;
  }, 1000); // 1秒防抖，等待 App 初始化完成
}

startDev();
