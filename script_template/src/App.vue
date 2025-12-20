<template>
  <div class="app-container">
    <header>
      <h2>TouchHelper Pro</h2>
      <span class="status-badge">Root/Access模式</span>
    </header>

    <div class="card config-card">
      <div class="card-header">
        <h3>⚙️ 参数配置 (Configuration)</h3>
        <button class="btn-small" @click="syncAllConfigs">保存配置</button>
      </div>

      <div class="form-grid">
        <div class="form-item">
          <label>循环次数 (Loop):</label>
          <input type="number" v-model="loopCount" @change="syncConfig('loop_times', loopCount)">
        </div>

        <div class="form-item">
          <label>启用 Boss 模式:</label>
          <label class="switch">
            <input type="checkbox" v-model="enableBoss" @change="syncConfig('enable_boss', enableBoss)">
            <span class="slider round"></span>
          </label>
        </div>

        <div class="form-item">
          <label>目标颜色 (Hex):</label>
          <div class="color-picker-wrapper">
            <input type="color" v-model="targetColor" @change="syncConfig('target_color', targetColor)">
            <span>{{ targetColor }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="card script-card">
      <div class="card-header">
        <h3>📜 脚本逻辑 (JavaScript / QuickJS)</h3>
        <button class="btn-small btn-secondary" @click="resetScript">重置默认</button>
      </div>

      <div class="editor-container">
        <textarea v-model="scriptContent" spellcheck="false" placeholder="在此编写自动化逻辑..."></textarea>
      </div>

      <div class="tips">
        <p>💡 提示: 支持 Device, Colors, Config, Thread 类。使用 log() 打印日志。</p>
      </div>
    </div>

    <div class="actions">
      <button class="btn-primary" @click="run">
        ▶️ 运行脚本 (Run Script)
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
// 🔥 引用更新后的 Bridge
import { Bridge } from './utils/native-bridge';

// --- 响应式数据 ---
const loopCount = ref(5);
const enableBoss = ref(true);
const targetColor = ref("#ff0000");

// --- 默认脚本模板 (适配 Rust 0.10.0 新 API) ---
const defaultScript = `
// ============================================
// 🛠️ 脚本配置区 (请根据游戏实际情况修改坐标)
// ============================================

// 1. 全局配置
var MONSTER_COLOR = Config.get("target_color") || "#FF0000"; // 怪物颜色
var BOSS_INTERVAL = 5 * 60 * 1000; // BOSS刷新间隔 (这里设为 5分钟)
var MAX_FIGHT_ROUNDS = 20; // 单个地图最大找怪次数 (防止卡死)

// 2. 常规地图配置 (10个)
// x, y 是进入该地图的按钮坐标 (或者是传送员列表里的坐标)
var NORMAL_MAPS = [
    { name: "常规-猪洞一层", x: 100, y: 200 },
    { name: "常规-猪洞二层", x: 100, y: 250 },
    { name: "常规-猪洞三层", x: 100, y: 300 },
    { name: "常规-蜈蚣洞口", x: 100, y: 350 },
    { name: "常规-死亡棺材", x: 100, y: 400 },
    { name: "常规-石墓阵",   x: 100, y: 450 },
    { name: "常规-祖玛大厅", x: 100, y: 500 },
    { name: "常规-赤月峡谷", x: 100, y: 550 },
    { name: "常规-牛魔大厅", x: 100, y: 600 },
    { name: "常规-魔龙血域", x: 100, y: 650 }
];

// 3. BOSS 地图配置 (2个)
var BOSS_MAPS = [
    { name: "🔥 BOSS-火龙巢穴", x: 800, y: 200 },
    { name: "🔥 BOSS-冰雪大殿", x: 800, y: 300 }
];

// 4. UI 按钮坐标 (回城/传送)
var BTN_TOWN = { x: 900, y: 100 }; // “回城”或“传送员”按钮
var BTN_EXIT = { x: 950, y: 50 };  // 退出地图/返回按钮

// ============================================
// 🧠 核心逻辑区
// ============================================

var nextBossTime = Date.now(); // 立即执行一次，或者设为 Date.now() + BOSS_INTERVAL
var currentMapIndex = 0;

log("🚀 脚本启动！目标颜色: " + MONSTER_COLOR);

while (true) {
    // --- 1. 检查是否到达 BOSS 时间 ---
    if (Date.now() >= nextBossTime) {
        log("⏰ BOSS 时间到！准备前往挑战...");
        
        for (var i = 0; i < BOSS_MAPS.length; i++) {
            var bossMap = BOSS_MAPS[i];
            runMapLogic(bossMap, true); // true 表示是 BOSS 图，可能需要打久一点
        }

        // 重置下一次时间
        nextBossTime = Date.now() + BOSS_INTERVAL;
        log("✅ BOSS 轮次结束，下次挑战时间: " + new Date(nextBossTime).toLocaleTimeString());
    }

    // --- 2. 常规地图循环 ---
    var map = NORMAL_MAPS[currentMapIndex];
    runMapLogic(map, false);

    // 切换到下一个地图
    currentMapIndex++;
    if (currentMapIndex >= NORMAL_MAPS.length) {
        currentMapIndex = 0; // 循环回到第一个
        log("🔄 10个常规图已刷完，从头开始...");
    }

    await Thread.sleep(1000);
}

// ============================================
// 🔧 功能函数封装
// ============================================

/**
 * 执行单个地图的完整流程：进图 -> 打怪 -> 退图
 */
async function runMapLogic(mapInfo, isBoss) {
    log(">>> 准备进入地图: [" + mapInfo.name + "]");

    // 1. 回城/打开传送界面
    goHomeAndOpenTeleport();

    // 2. 点击进入地图
    Device.click(mapInfo.x, mapInfo.y);
    await Thread.sleep(3000); // 等待过图加载

    // 3. 开始打怪循环
    var noMonsterCount = 0; // 连续没找到怪的次数
    var round = 0;
    
    // BOSS图打久一点(30轮)，普通图少打点(20轮)
    var maxRounds = isBoss ? 30 : MAX_FIGHT_ROUNDS; 

    while (round < maxRounds) {
        // 找怪
        var point = Colors.findColorPoint(MONSTER_COLOR);

        if (point) {
            // 找到怪了
            log("⚔️ [" + mapInfo.name + "] 发现怪物 (" + point[0] + "," + point[1] + ")");
            Device.click(point[0], point[1]); // 点击攻击
            
            // 模拟战斗耗时 (根据游戏攻速调整)
            await Thread.sleep(2000); 
            
            noMonsterCount = 0; // 重置计数器
        } else {
            // 没找到怪
            noMonsterCount++;
            log("👀 [" + mapInfo.name + "] 未发现怪物... (" + noMonsterCount + "/3)");
            
            // 稍微随机动一下，防止发呆 (点击屏幕中心附近)
            Device.click(500 + Math.random()*50, 500 + Math.random()*50);
            await Thread.sleep(1500);
        }

        // 连续 3 次没找到怪，或者怪物死完了 -> 换图
        if (noMonsterCount >= 3) {
            log("👋 [" + mapInfo.name + "]以此地无怪，撤退！");
            break; 
        }

        round++;
    }

    // 4. 退出地图 (如果不回城，就在这里写退出逻辑)
    log("🏁 [" + mapInfo.name + "] 探索结束");
}

/**
 * 回城并打开传送菜单
 */
async function goHomeAndOpenTeleport() {
    // 点击回城石 / 传送员
    Device.click(BTN_TOWN.x, BTN_TOWN.y);
    await Thread.sleep(1500); 
    
    // 如果有二级菜单，在这里加逻辑
    // Device.click(..., ...);
    // await Thread.sleep(1000);
}
`;

const scriptContent = ref(defaultScript);

// --- 方法定义 ---

// 同步单个配置到 Rust (存入 HashMap)
function syncConfig(key: string, val: any) {
  // 注意：Rust 端只接收 String
  Bridge.setConfig(key, String(val));
}

// 批量同步所有配置 (防止漏掉)
function syncAllConfigs() {
  syncConfig('loop_times', loopCount.value);
  syncConfig('enable_boss', enableBoss.value);
  syncConfig('target_color', targetColor.value);
  Bridge.log("配置已手动保存");
}

function resetScript() {
  if (confirm("确定要重置脚本代码吗？")) {
    scriptContent.value = defaultScript;
  }
}

function run() {
  // 1. 运行前强制同步一次配置，确保 Rust 端拿到的是最新的 UI 值
  syncAllConfigs();

  // 2. 发送脚本给 Rust 执行
  Bridge.runScript(scriptContent.value);
}

// 初始化
onMounted(() => {
  syncAllConfigs();
});
</script>

<style scoped>
/* 样式美化 */
.app-container {
  max-width: 600px;
  margin: 0 auto;
  padding: 16px;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  color: #333;
  background-color: #f0f2f5;
  min-height: 100vh;
}

header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

h2 {
  margin: 0;
  font-size: 1.2rem;
  color: #1a1a1a;
}

.status-badge {
  background: #e6f7ff;
  color: #1890ff;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 0.8rem;
  border: 1px solid #91d5ff;
}

.card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  margin-bottom: 16px;
  overflow: hidden;
}

.card-header {
  padding: 12px 16px;
  background: #fafafa;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
}

.form-grid {
  padding: 16px;
  display: grid;
  gap: 12px;
}

.form-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

input[type="number"],
input[type="text"] {
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 6px;
  width: 100px;
  text-align: right;
}

/* 颜色选择器美化 */
.color-picker-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-picker-wrapper input[type="color"] {
  border: none;
  padding: 0;
  width: 32px;
  height: 32px;
  background: none;
  cursor: pointer;
}

/* 开关样式 */
.switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
}

.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: #ccc;
  transition: .4s;
}

.slider:before {
  position: absolute;
  content: "";
  height: 18px;
  width: 18px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  transition: .4s;
}

input:checked+.slider {
  background-color: #2196F3;
}

input:checked+.slider:before {
  transform: translateX(20px);
}

.slider.round {
  border-radius: 34px;
}

.slider.round:before {
  border-radius: 50%;
}

/* 编辑器区域 */
.editor-container {
  height: 300px;
  background: #1e1e1e;
}

textarea {
  width: 100%;
  height: 100%;
  background: #1e1e1e;
  color: #d4d4d4;
  border: none;
  padding: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  outline: none;
}

.tips {
  padding: 8px 16px;
  font-size: 0.85rem;
  color: #666;
  background: #fff;
  border-top: 1px solid #eee;
}

/* 按钮样式 */
.btn-primary {
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #1890ff 0%, #096dd9 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.3);
  transition: all 0.2s;
}

.btn-primary:active {
  transform: scale(0.98);
}

.btn-small {
  padding: 6px 12px;
  font-size: 12px;
  border: 1px solid #d9d9d9;
  background: white;
  border-radius: 4px;
  cursor: pointer;
}

.btn-secondary {
  color: #666;
}
</style>
