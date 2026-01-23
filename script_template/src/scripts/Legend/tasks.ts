import { SegmentationConfig } from '@/types/touch-helper';
import { type GameConfig } from './config';

export async function fightLoop(config: GameConfig) {
  Thread.sleep(2000);
  const color = config.monsterColor;
  log(`[Task] 开始找怪，颜色: ${color}`);


  // 1. 截图
  const img = Device.capture();

  // 2. 应用滤镜处理
  // Step 1: GrayscaleFilter
  img.applyFilter({
    "Grayscale": {
      "mode": "Weighted"
    }
  });
  // Step 2: BinarizationFilter
  img.applyFilter({
    "Binarization": {
      "mode": 'Manual',
      threshold_min: 0.0,
      threshold_max: 72.0,
      is_rgb_avg: true,
      sauvola_k: 0.2,
      window_size: 15.0,
    }
  });

  // 3. 配置切割参数
  const segConfig: SegmentationConfig = {
    "mode": "Projection",
    padding: 0,
    min_width: 10,
    min_height: 10,
    max_width: 0,
    max_height: 0,
    merge_distance: 0,
    start_x: 0,
    start_y: 0,
    cell_width: 32,
    cell_height: 32,
    col_count: 1,
    row_count: 1,
    col_gap: 0,
    row_gap: 0,
    split_rows: true,
    split_cols: true,
    projection_threshold: 128
  };

  // 4. 字库数据 (Hex压缩)
  const fontLibrary = {
    "本": "0001E0000000780000001E0000000780000001E0000000780000001E0000000780007FFFFFFF9FFFFFFFE7FFFFFFF9FFFFFFFE003DEF00000F79E000079E780001E79F0000F1E3C0007C78F8001E1E1E000F8787C007C1E0F801E0781F00F81E07E07C0780FC3F01E03F9FFFFFFFFFDFFFFEF9E7FFFF9C31FFFFE30000780000001E0000000780000001E0000000780000001E0000",
    "次": "0000000000C000000C000400C000700C0003C1C0000E1FFFE041FFFE0030006003000C0060C0C0060C0C00C0C1800C0C180000C000000E000600E000600E000C01F001C01B001803180300318070060C0600E060C01C070403803800F001C01C00070180002",
    "数": "010000021083002108300311820011302000100600FFFE600FFFE7FF078060C07E0E0C0D30E08391CE087101A18C1012180003318020011006001307FF81B07FF80E008180E018100C030300E01C601E007C01B003C0318077060C0C11C0E7803803C006001"
  };

  // 5. 执行识别
  const result = img.ocrGrid(segConfig, fontLibrary, 0.8);
  log(`识别结果: ${result}`);

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

