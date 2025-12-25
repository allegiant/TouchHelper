use crate::vision::colors;
use crate::vision::types::{ColorRule, Rect};
use image::{DynamicImage};

/// 扫描连通区域
/// 返回一组 Rect，代表每个连通块的包围盒
pub fn scan_connected_components(
    img: &DynamicImage,
    rules: Vec<ColorRule>,
    min_w: u32,
    min_h: u32,
) -> Vec<Rect> {
    let width = img.width();
    let height = img.height();

    // 1. 先根据颜色规则生成二值图 (0: 背景, 1: 前景)
    // 这一步在 Rust 做非常快
    let mut binary_map = vec![0u32; (width * height) as usize];
    let rgb = img.to_rgb8();

    // 预处理规则，避免循环内重复解析
    let parsed_rules: Vec<([u8; 3], [u8; 3])> = rules
        .iter()
        .filter(|r| r.is_enabled)
        .map(|r| {
            (
                colors::parse_hex(&r.target_hex),
                colors::parse_hex(&r.bias_hex),
            )
        })
        .collect();

    for (x, y, pixel) in rgb.enumerate_pixels() {
        let p = pixel.0;
        let mut matched = false;

        // 针对二值化图的特殊优化：如果规则是找白色
        // 也可以直接复用 filters::binarize 的结果
        for (target, bias) in &parsed_rules {
            if colors::is_match(p, *target, *bias) {
                matched = true;
                break;
            }
        }

        if matched {
            binary_map[(y * width + x) as usize] = 1;
        }
    }

    // 2. 使用 imageproc 的连通分量算法
    // imageproc::region_labelling 要求输入是 ImageBuffer<Luma<u32>, ...>
    // 这里我们手动实现一个简单的包围盒提取，或者转换格式调用库函数
    // 下面是一个简化的手动 BFS 实现，避免额外的图像转换开销：

    let mut visited = vec![false; (width * height) as usize];
    let mut rects = Vec::new();

    for y in 0..height {
        for x in 0..width {
            let idx = (y * width + x) as usize;
            if binary_map[idx] == 1 && !visited[idx] {
                // 发现新区域，开始 BFS
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

                    // 8-邻域搜索
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
                            if binary_map[n_idx] == 1 && !visited[n_idx] {
                                visited[n_idx] = true;
                                stack.push((nx, ny));
                            }
                        }
                    }
                }

                let w = max_x - min_x + 1;
                let h = max_y - min_y + 1;

                if w >= min_w && h >= min_h {
                    rects.push(Rect {
                        left: min_x as i32,
                        top: min_y as i32,
                        width: w,
                        height: h,
                    });
                }
            }
        }
    }

    rects
}
