import * as esbuild from 'esbuild';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// 🔥 配置：当前 App 对应哪个游戏脚本？
const ACTIVE_GAME = 'Legend'; // 这里对应 src/scripts/Legend 目录

const SRC_ENTRY = path.join(__dirname, `../src/scripts/${ACTIVE_GAME}/index.ts`);
const OUT_FILE = path.join(__dirname, '../public/script.js'); // 固定输出文件名

async function build() {
  console.log(`🔨 正在构建脚本: [${ACTIVE_GAME}] ...`);

  if (!fs.existsSync(SRC_ENTRY)) {
    console.error(`❌ 找不到入口文件: ${SRC_ENTRY}`);
    process.exit(1);
  }

  try {
    await esbuild.build({
      entryPoints: [SRC_ENTRY],
      outfile: OUT_FILE,
      bundle: true,
      format: 'iife',            // 立即执行函数
      globalName: 'GameScript',  // 暴露给 Rust 的全局变量
      platform: 'browser',
      target: ['es2020'],
      charset: 'utf8',           // 强制 UTF-8 (解决中文乱码)
      minify: true,              // 压缩代码 (减小体积)
      sourcemap: false,
    });
    console.log(`✅ 编译成功! 输出文件: public/script.js`);
  } catch (e) {
    console.error("❌ 编译失败:", e);
    process.exit(1);
  }
}

build();
