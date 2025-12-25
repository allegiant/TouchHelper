<template>
  <div class="font-maker">
    <h3>🛠️ 自制字库工具</h3>

    <div class="controls">
      <div class="step">
        <label>1. 选择图片:</label>
        <input type="file" @change="handleFile" accept="image/*" />
      </div>

      <div class="step">
        <label>2. 偏色 (Hex - 容差):</label>
        <div class="color-row">
          <input type="color" v-model="targetColorHex">
          <input type="number" v-model="offset" style="width:60px" title="偏色容差">
        </div>
      </div>

      <div class="step">
        <label>3. 字符名称:</label>
        <div class="gen-row">
          <input type="text" v-model="charName" placeholder="如: 回" style="width: 60px;">
          <button @click="generateCode" class="btn-gen">生成</button>
        </div>
      </div>
    </div>

    <div class="workspace">
      <div class="canvas-group">
        <h4>原图 (点击取色)</h4>
        <canvas ref="canvasOriginal" @mousedown="pickColor"></canvas>
      </div>

      <div class="canvas-group">
        <h4>二值化预览</h4>
        <canvas ref="canvasBinary"></canvas>
      </div>
    </div>

    <div class="output" v-if="resultCode">
      <h4>生成结果:</h4>
      <textarea v-model="resultCode" rows="4" readonly @click="$event.target.select()"></textarea>
      <p class="tip">复制上面的代码，在脚本中使用 TextFinder.setDict(...) 加载</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

const canvasOriginal = ref<HTMLCanvasElement | null>(null);
const canvasBinary = ref<HTMLCanvasElement | null>(null);
const targetColorHex = ref("#FFFFFF");
const offset = ref(10);
const charName = ref("");
const resultCode = ref("");

let imgObj: HTMLImageElement | null = null;

function handleFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = (evt) => {
    imgObj = new Image();
    imgObj.onload = () => draw();
    imgObj.src = evt.target?.result as string;
  };
  reader.readAsDataURL(file);
}

function draw() {
  if (!imgObj || !canvasOriginal.value || !canvasBinary.value) return;

  // 限制一下最大显示宽度，防止图片太大撑破布局
  // 实际处理还是用原图尺寸，只是显示缩放 (或者你也可以不做缩放)
  const w = imgObj.width;
  const h = imgObj.height;

  canvasOriginal.value.width = w;
  canvasOriginal.value.height = h;
  canvasBinary.value.width = w;
  canvasBinary.value.height = h;

  const ctxOrig = canvasOriginal.value.getContext('2d', { willReadFrequently: true })!;
  const ctxBin = canvasBinary.value.getContext('2d')!;

  ctxOrig.drawImage(imgObj, 0, 0);

  const imgData = ctxOrig.getImageData(0, 0, w, h);
  const data = imgData.data;

  const tr = parseInt(targetColorHex.value.slice(1, 3), 16);
  const tg = parseInt(targetColorHex.value.slice(3, 5), 16);
  const tb = parseInt(targetColorHex.value.slice(5, 7), 16);
  const off = offset.value;

  for (let i = 0; i < data.length; i += 4) {
    const r = data[i], g = data[i + 1], b = data[i + 2];

    if (Math.abs(r - tr) <= off && Math.abs(g - tg) <= off && Math.abs(b - tb) <= off) {
      // 匹配：设为红色 (前景)
      data[i] = 255; data[i + 1] = 0; data[i + 2] = 0;
    } else {
      // 不匹配：设为白色 (背景)
      data[i] = 255; data[i + 1] = 255; data[i + 2] = 255;
    }
  }

  ctxBin.putImageData(imgData, 0, 0);
}

function pickColor(e: MouseEvent) {
  if (!canvasOriginal.value) return;
  const ctx = canvasOriginal.value.getContext('2d')!;
  const rect = canvasOriginal.value.getBoundingClientRect();
  // 计算点击在 Canvas 内部的坐标（考虑缩放）
  const scaleX = canvasOriginal.value.width / rect.width;
  const scaleY = canvasOriginal.value.height / rect.height;
  const x = (e.clientX - rect.left) * scaleX;
  const y = (e.clientY - rect.top) * scaleY;

  const p = ctx.getImageData(x, y, 1, 1).data;
  const hex = "#" + [p[0], p[1], p[2]].map(x => x.toString(16).padStart(2, '0').toUpperCase()).join('');
  targetColorHex.value = hex;
}

function generateCode() {
  if (!canvasBinary.value || !charName.value) {
    alert("请先上传图片并输入字符名称");
    return;
  }

  const ctx = canvasBinary.value.getContext('2d')!;
  const w = canvasBinary.value.width;
  const h = canvasBinary.value.height;
  const data = ctx.getImageData(0, 0, w, h).data;

  let binaryStr = "";
  for (let i = 0; i < data.length; i += 4) {
    // 红色(255,0,0)是前景
    const isMatch = (data[i] === 255 && data[i + 1] === 0);
    binaryStr += isMatch ? "1" : "0";
  }

  // 生成格式：字符$宽$高$点阵
  resultCode.value = `${charName.value}$${w}$${h}$${binaryStr}`;
}

watch([targetColorHex, offset], draw);
</script>

<style scoped>
.font-maker {
  border-top: 2px solid #eee;
  padding-top: 20px;
  margin-top: 20px;
}

h3 {
  margin-bottom: 15px;
  color: #333;
}

.controls {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 15px;
}

.step {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: space-between;
}

.color-row,
.gen-row {
  display: flex;
  align-items: center;
  gap: 5px;
}

.workspace {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  background: #f5f5f5;
  padding: 10px;
  border-radius: 4px;
  justify-content: center;
}

.canvas-group h4 {
  margin: 5px 0;
  font-size: 12px;
  text-align: center;
}

canvas {
  border: 1px solid #ccc;
  background: white;
  max-width: 100%;
  /* 响应式 */
  cursor: crosshair;
}

textarea {
  width: 100%;
  font-family: monospace;
  font-size: 12px;
  margin-top: 5px;
}

.btn-gen {
  background: #52c41a;
  color: white;
  border: none;
  padding: 4px 8px;
  border-radius: 4px;
}

.tip {
  font-size: 12px;
  color: #666;
  margin-top: 2px;
}
</style>
