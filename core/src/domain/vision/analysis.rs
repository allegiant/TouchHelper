use image::{DynamicImage, GenericImageView};
use std::cmp::{max, min};

use super::types::{Rect, SegmentationConfig, SegmentationMode};

/// 核心入口：执行分割分析
pub fn perform_segmentation(img: &DynamicImage, config: &SegmentationConfig) -> Vec<Rect> {
    // 1. 执行原始分割 (Raw Segmentation)
    // 注意：这里的子算法不再执行严格的 config.min_width 过滤，
    // 而是仅执行基本的噪点过滤 (如 < 2px)，保留尽可能多的碎片以供后续合并。
    let raw_rects = match config.mode {
        SegmentationMode::FixedGrid => segment_grid(img, config),
        SegmentationMode::Projection => segment_projection(img, config),
        SegmentationMode::ConnectedComp => segment_connected_components(img, config),
    };

    // 2. [新增] 策略 A: 合并邻近矩形 (Merge Strategy)
    // 如果碎片之间距离很近，说明它们属于同一个视觉元素（如一整行字）
    let merged_rects = if config.merge_distance > 0 && !raw_rects.is_empty() {
        merge_nearby_rects(raw_rects, config.merge_distance)
    } else {
        raw_rects
    };

    // 3. [新增] 尺寸过滤 (Size Filter)
    // 此时的 rects 已经是合并后的完整形态，可以严格应用用户的 min/max 限制了
    let filtered_rects = merged_rects
        .into_iter()
        .filter(|r| {
            // 检查最小尺寸
            if r.width < config.min_width || r.height < config.min_height {
                return false;
            }
            // 检查最大尺寸 (0 代表不限制)
            if config.max_width > 0 && r.width > config.max_width {
                return false;
            }
            if config.max_height > 0 && r.height > config.max_height {
                return false;
            }
            true
        })
        .collect();

    // 4. 后期处理（Padding + 边界安全修正）
    apply_padding_and_clamp(filtered_rects, img.width(), img.height(), config.padding)
}

// ==========================================
// [新增算法] 合并邻近矩形
// ==========================================
fn merge_nearby_rects(rects: Vec<Rect>, max_dist: u32) -> Vec<Rect> {
    if rects.is_empty() {
        return rects;
    }

    // 简单的迭代合并算法：
    // 1. 将所有矩形视为独立的集合
    // 2. 两两检查，如果距离 < max_dist，则合并它们
    // 3. 重复直到没有可以合并的为止
    // (为了性能，这里使用简化的并查集思路，或者直接多轮迭代)

    let mut current_rects = rects;
    let mut changed = true;

    // 循环直到稳定（不再发生合并）
    while changed {
        changed = false;
        let mut next_rects = Vec::new();
        let mut merged_indices = vec![false; current_rects.len()];

        for i in 0..current_rects.len() {
            if merged_indices[i] {
                continue;
            }

            let mut base = current_rects[i];
            merged_indices[i] = true; // 标记自己已处理

            // 尝试将 base 与后续所有未处理的矩形合并
            for j in (i + 1)..current_rects.len() {
                if merged_indices[j] {
                    continue;
                }

                let target = current_rects[j];
                if is_nearby(&base, &target, max_dist) {
                    // 合并两个矩形
                    base = union_rect(&base, &target);
                    merged_indices[j] = true; // 标记 target 已被吸纳
                    changed = true; // 发生了变更，需要再跑一轮以防连锁反应
                }
            }
            next_rects.push(base);
        }
        current_rects = next_rects;
    }

    current_rects
}

// 判断两个矩形是否足够靠近
fn is_nearby(r1: &Rect, r2: &Rect, dist: u32) -> bool {
    // 扩展 r1 的边界 dist 大小，看是否与 r2 相交
    let r1_left = r1.left - dist as i32;
    let r1_top = r1.top - dist as i32;
    let r1_right = r1.left + r1.width as i32 + dist as i32;
    let r1_bottom = r1.top + r1.height as i32 + dist as i32;

    let r2_right = r2.left + r2.width as i32;
    let r2_bottom = r2.top + r2.height as i32;

    // 矩形相交判定：!(r2在r1左边 || r2在r1右边 || r2在r1上边 || r2在r1下边)
    !(r2.left > r1_right || r2_right < r1_left || r2.top > r1_bottom || r2_bottom < r1_top)
}

// 合并两个矩形为包围盒
fn union_rect(r1: &Rect, r2: &Rect) -> Rect {
    let left = min(r1.left, r2.left);
    let top = min(r1.top, r2.top);
    let right = max(r1.left + r1.width as i32, r2.left + r2.width as i32);
    let bottom = max(r1.top + r1.height as i32, r2.top + r2.height as i32);

    Rect {
        left,
        top,
        width: (right - left) as u32,
        height: (bottom - top) as u32,
    }
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
// 算法 3: 连通域分析 (已修改)
// ==========================================
fn segment_connected_components(img: &DynamicImage, _config: &SegmentationConfig) -> Vec<Rect> {
    let width = img.width();
    let height = img.height();
    let gray = img.to_luma8();

    let mut visited = vec![false; (width * height) as usize];
    let mut rects = Vec::new();

    // [变更] 这里不再使用 config.min_width，而是使用硬编码的最小值 (比如 2)
    // 目的：即使是碎裂的字符笔画(如 'i' 的点)也要先切出来，交给 Merge 步骤去组装。
    // 如果这里直接过滤掉，Merge 步骤就拿不到数据了。
    let noise_min_w = 2;
    let noise_min_h = 2;

    for y in 0..height {
        for x in 0..width {
            let idx = (y * width + x) as usize;
            let is_foreground = gray.get_pixel(x, y)[0] > 128;

            if is_foreground && !visited[idx] {
                let mut min_x = x;
                let mut max_x = x;
                let mut min_y = y;
                let mut max_y = y;

                let mut stack = vec![(x, y)];
                visited[idx] = true;

                while let Some((cx, cy)) = stack.pop() {
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

                    let neighbors = [
                        (cx.wrapping_sub(1), cy),
                        (cx + 1, cy),
                        (cx, cy.wrapping_sub(1)),
                        (cx, cy + 1),
                        (cx.wrapping_sub(1), cy.wrapping_sub(1)),
                        (cx + 1, cy + 1),
                        (cx.wrapping_sub(1), cy + 1),
                        (cx + 1, cy.wrapping_sub(1)),
                    ];

                    for &(nx, ny) in &neighbors {
                        if nx < width && ny < height {
                            let n_idx = (ny * width + nx) as usize;
                            if !visited[n_idx] && gray.get_pixel(nx, ny)[0] > 128 {
                                visited[n_idx] = true;
                                stack.push((nx, ny));
                            }
                        }
                    }
                }

                let w_rect = max_x - min_x + 1;
                let h_rect = max_y - min_y + 1;

                // 仅过滤极小的噪点
                if w_rect >= noise_min_w && h_rect >= noise_min_h {
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
