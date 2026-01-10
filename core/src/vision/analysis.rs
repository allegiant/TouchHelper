use crate::vision::types::{Rect, SegmentationConfig, SegmentationMode};
use image::{DynamicImage, GenericImageView};

/// 核心入口：执行分割分析
/// 注意：输入的 img 应该是已经经过滤镜处理（二值化/反色）后的图像
pub fn perform_segmentation(img: &DynamicImage, config: &SegmentationConfig) -> Vec<Rect> {
    // 1. 根据模式分发算法
    let raw_rects = match config.mode {
        SegmentationMode::FixedGrid => segment_grid(img, config),
        SegmentationMode::Projection => segment_projection(img, config),
        SegmentationMode::ConnectedComp => segment_connected_components(img, config),
    };

    // 2. 统一后期处理（Padding + 边界安全修正）
    apply_padding_and_clamp(raw_rects, img.width(), img.height(), config.padding)
}

// ==========================================
// 算法 1: 固定网格切割 (Fixed Grid)
// ==========================================
fn segment_grid(img: &DynamicImage, config: &SegmentationConfig) -> Vec<Rect> {
    let mut rects = Vec::new();
    let (w, h) = img.dimensions();

    for r in 0..config.row_count {
        for c in 0..config.col_count {
            // 计算每个格子的左上角坐标
            let x = config.start_x + (c as i32 * (config.cell_width as i32 + config.col_gap));
            let y = config.start_y + (r as i32 * (config.cell_height as i32 + config.row_gap));

            // 基础检查：起点要在图内 (允许部分越界，后续由 clamp 处理)
            if x < w as i32 && y < h as i32 {
                rects.push(Rect {
                    left: x,
                    top: y,
                    width: config.cell_width,
                    height: config.cell_height,
                });
            }
        }
    }
    rects
}

// ==========================================
// 算法 2: 投影切割 (XY Projection)
// ==========================================
fn segment_projection(img: &DynamicImage, config: &SegmentationConfig) -> Vec<Rect> {
    let gray = img.to_luma8();
    let (w, h) = gray.dimensions();
    let thresh = config.projection_threshold;

    // 内部辅助函数：扫描轴线，返回内容区间 [(start, end), ...]
    // is_row_scan: true=扫描行(Y轴), false=扫描列(X轴)
    // range_start/end: 当前扫描轴的范围
    // cross_min/max: 垂直方向的检查范围
    let scan_axis = |is_row_scan: bool,
                     range_start: u32,
                     range_end: u32,
                     cross_min: u32,
                     cross_max: u32|
     -> Vec<(u32, u32)> {
        let mut intervals = Vec::new();
        let mut in_block = false;
        let mut start_pos = 0;

        for i in range_start..range_end {
            let mut has_pixel = false;
            // 遍历垂直轴向，寻找是否有有效像素
            for j in cross_min..cross_max {
                let pixel_val = if is_row_scan {
                    gray.get_pixel(j, i)[0] // (x, y) = (j, i)
                } else {
                    gray.get_pixel(i, j)[0] // (x, y) = (i, j)
                };

                // 假设由二值化滤镜处理过，前景通常是亮色(255)
                // 如果 config 指定了阈值，则大于阈值视为前景
                if pixel_val > thresh {
                    has_pixel = true;
                    break;
                }
            }

            // 状态机：进入或离开内容块
            if has_pixel {
                if !in_block {
                    in_block = true;
                    start_pos = i;
                }
            } else {
                if in_block {
                    in_block = false;
                    intervals.push((start_pos, i));
                }
            }
        }
        // 结束时若还在块内，闭合它
        if in_block {
            intervals.push((start_pos, range_end));
        }
        intervals
    };

    let mut rects = Vec::new();

    // A. 垂直投影 (切行)：得到每一行的 Y 区间
    let row_intervals = if config.split_rows {
        scan_axis(true, 0, h, 0, w)
    } else {
        vec![(0, h)] // 不切行，视作一整行
    };

    // B. 水平投影 (每一行内切列)：得到 X 区间
    for (r_start, r_end) in row_intervals {
        if config.split_cols {
            let col_intervals = scan_axis(false, 0, w, r_start, r_end);
            for (c_start, c_end) in col_intervals {
                let rect_w = c_end - c_start;
                let rect_h = r_end - r_start;

                // 过滤微小噪点
                if rect_w >= config.min_width && rect_h >= config.min_height {
                    rects.push(Rect {
                        left: c_start as i32,
                        top: r_start as i32,
                        width: rect_w,
                        height: rect_h,
                    });
                }
            }
        } else {
            // 只切行，不切列
            let rect_h = r_end - r_start;
            if rect_h >= config.min_height {
                rects.push(Rect {
                    left: 0,
                    top: r_start as i32,
                    width: w,
                    height: rect_h,
                });
            }
        }
    }

    rects
}

// ==========================================
// 算法 3: 连通域分析 (Connected Components)
// 重构自你原有的 scan_connected_components
// ==========================================
fn segment_connected_components(img: &DynamicImage, config: &SegmentationConfig) -> Vec<Rect> {
    let width = img.width();
    let height = img.height();
    let gray = img.to_luma8(); // 确保拿到灰度数据

    // 访问标记数组
    let mut visited = vec![false; (width * height) as usize];
    let mut rects = Vec::new();

    // 使用配置中的最小宽高
    let min_w = config.min_width;
    let min_h = config.min_height;

    // 遍历所有像素
    for y in 0..height {
        for x in 0..width {
            let idx = (y * width + x) as usize;

            // 核心判定：直接根据灰度值判断前景 (大于128视为亮色前景)
            // 这样不再依赖 ColorRule，而是依赖上游滤镜的结果
            let is_foreground = gray.get_pixel(x, y)[0] > 128;

            if is_foreground && !visited[idx] {
                // 发现新区域，开始 BFS 搜索
                let mut min_x = x;
                let mut max_x = x;
                let mut min_y = y;
                let mut max_y = y;

                let mut stack = vec![(x, y)];
                visited[idx] = true;

                while let Some((cx, cy)) = stack.pop() {
                    // 更新包围盒
                    if cx < min_x {
                        min_x = cx;
                    }
                    if cx > max_x {
                        max_x = cx;
                    }
                    if cy < min_y {
                        min_y = cy;
                    }
                    if cy > max_y {
                        max_y = cy;
                    }

                    // 8-邻域搜索 (上下左右 + 对角线)
                    // 使用 wrapping_sub 防止 usize 下溢出，但在比较时需注意范围
                    let neighbors = [
                        (cx.wrapping_sub(1), cy),                 // 左
                        (cx + 1, cy),                             // 右
                        (cx, cy.wrapping_sub(1)),                 // 上
                        (cx, cy + 1),                             // 下
                        (cx.wrapping_sub(1), cy.wrapping_sub(1)), // 左上
                        (cx + 1, cy + 1),                         // 右下
                        (cx.wrapping_sub(1), cy + 1),             // 左下
                        (cx + 1, cy.wrapping_sub(1)),             // 右上
                    ];

                    for &(nx, ny) in &neighbors {
                        if nx < width && ny < height {
                            let n_idx = (ny * width + nx) as usize;
                            // 检查邻居：必须也是前景且未访问过
                            if !visited[n_idx] && gray.get_pixel(nx, ny)[0] > 128 {
                                visited[n_idx] = true;
                                stack.push((nx, ny));
                            }
                        }
                    }
                }

                // 连通块搜索结束，计算尺寸
                let w_rect = max_x - min_x + 1;
                let h_rect = max_y - min_y + 1;

                if w_rect >= min_w && h_rect >= min_h {
                    rects.push(Rect {
                        left: min_x as i32,
                        top: min_y as i32,
                        width: w_rect,
                        height: h_rect,
                    });
                }
            }
        }
    }

    rects
}

// ==========================================
// 辅助函数: Padding 处理与边界修正
// ==========================================
fn apply_padding_and_clamp(mut rects: Vec<Rect>, w: u32, h: u32, padding: i32) -> Vec<Rect> {
    if rects.is_empty() {
        return rects;
    }

    rects.iter_mut().for_each(|r| {
        // 1. 应用 Padding (正数扩大，负数缩小)
        if padding != 0 {
            r.left -= padding;
            r.top -= padding;
            // 宽高的变化是 2 倍的 padding (左右各加，或上下各加)
            // 使用 max(1) 防止缩小成负数或0导致 panic 或逻辑错误
            r.width = (r.width as i32 + padding * 2).max(1) as u32;
            r.height = (r.height as i32 + padding * 2).max(1) as u32;
        }

        // 2. 边界修正 (Clamp)
        // 修正左边
        if r.left < 0 {
            // 如果左边出界，宽度要减去出界的部分
            let overflow = r.left.abs();
            r.width = r.width.saturating_sub(overflow as u32);
            r.left = 0;
        }
        // 修正上边
        if r.top < 0 {
            let overflow = r.top.abs();
            r.height = r.height.saturating_sub(overflow as u32);
            r.top = 0;
        }

        // 修正右边 (left + width <= w)
        if (r.left as u32 + r.width) > w {
            r.width = w.saturating_sub(r.left as u32);
        }
        // 修正下边 (top + height <= h)
        if (r.top as u32 + r.height) > h {
            r.height = h.saturating_sub(r.top as u32);
        }
    });

    // 3. 过滤掉因为 Padding 或 Clamp 导致无效的 Rect
    rects
        .into_iter()
        .filter(|r| r.width > 0 && r.height > 0)
        .collect()
}
