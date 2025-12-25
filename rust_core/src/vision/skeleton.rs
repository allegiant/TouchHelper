use image::{GrayImage, Luma};

/// Zhang-Suen 骨架化/细化算法
/// 输入必须是二值化的 GrayImage (前景为白色 255，背景为黑色 0)
pub fn apply_skeleton(img: &mut GrayImage) {
    let (width, height) = img.dimensions();
    let mut to_delete = Vec::new();

    // 算法需要反复迭代直到图像不再变化
    loop {
        let mut changed = false;

        // --- 步骤 1 ---
        to_delete.clear();
        for y in 1..height - 1 {
            for x in 1..width - 1 {
                // 仅处理前景点 (白色)
                if img.get_pixel(x, y)[0] == 0 {
                    continue;
                }

                let (p2, p3, p4, p5, p6, p7, p8, p9) = get_neighbors(img, x, y);

                let a = count_transitions(p2, p3, p4, p5, p6, p7, p8, p9);
                let b = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9;

                // Zhang-Suen 核心条件
                let c1 = b >= 2 && b <= 6;
                let c2 = a == 1;
                let c3 = p2 * p4 * p6 == 0;
                let c4 = p4 * p6 * p8 == 0;

                if c1 && c2 && c3 && c4 {
                    to_delete.push((x, y));
                }
            }
        }

        if !to_delete.is_empty() {
            changed = true;
            for &(dx, dy) in &to_delete {
                img.put_pixel(dx, dy, Luma([0]));
            }
        }

        // --- 步骤 2 ---
        to_delete.clear();
        for y in 1..height - 1 {
            for x in 1..width - 1 {
                if img.get_pixel(x, y)[0] == 0 {
                    continue;
                }

                let (p2, p3, p4, p5, p6, p7, p8, p9) = get_neighbors(img, x, y);

                let a = count_transitions(p2, p3, p4, p5, p6, p7, p8, p9);
                let b = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9;

                // 步骤 2 的条件略有不同 (c3 和 c4)
                let c1 = b >= 2 && b <= 6;
                let c2 = a == 1;
                let c3 = p2 * p4 * p8 == 0;
                let c4 = p2 * p6 * p8 == 0;

                if c1 && c2 && c3 && c4 {
                    to_delete.push((x, y));
                }
            }
        }

        if !to_delete.is_empty() {
            changed = true;
            for &(dx, dy) in &to_delete {
                img.put_pixel(dx, dy, Luma([0]));
            }
        }

        // 如果两步都没有删除任何像素，说明已收敛
        if !changed {
            break;
        }
    }
}

// 获取 P1 周围的 8 个邻居 (P2..P9)，归一化为 0 或 1
// P9 P2 P3
// P8 P1 P4
// P7 P6 P5
#[inline(always)]
fn get_neighbors(img: &GrayImage, x: u32, y: u32) -> (u8, u8, u8, u8, u8, u8, u8, u8) {
    let p2 = if img.get_pixel(x, y - 1)[0] > 0 { 1 } else { 0 };
    let p3 = if img.get_pixel(x + 1, y - 1)[0] > 0 {
        1
    } else {
        0
    };
    let p4 = if img.get_pixel(x + 1, y)[0] > 0 { 1 } else { 0 };
    let p5 = if img.get_pixel(x + 1, y + 1)[0] > 0 {
        1
    } else {
        0
    };
    let p6 = if img.get_pixel(x, y + 1)[0] > 0 { 1 } else { 0 };
    let p7 = if img.get_pixel(x - 1, y + 1)[0] > 0 {
        1
    } else {
        0
    };
    let p8 = if img.get_pixel(x - 1, y)[0] > 0 { 1 } else { 0 };
    let p9 = if img.get_pixel(x - 1, y - 1)[0] > 0 {
        1
    } else {
        0
    };
    (p2, p3, p4, p5, p6, p7, p8, p9)
}

// 计算 0->1 的跳变次数
#[inline(always)]
fn count_transitions(p2: u8, p3: u8, p4: u8, p5: u8, p6: u8, p7: u8, p8: u8, p9: u8) -> u8 {
    let mut transitions = 0;
    if p2 == 0 && p3 == 1 {
        transitions += 1;
    }
    if p3 == 0 && p4 == 1 {
        transitions += 1;
    }
    if p4 == 0 && p5 == 1 {
        transitions += 1;
    }
    if p5 == 0 && p6 == 1 {
        transitions += 1;
    }
    if p6 == 0 && p7 == 1 {
        transitions += 1;
    }
    if p7 == 0 && p8 == 1 {
        transitions += 1;
    }
    if p8 == 0 && p9 == 1 {
        transitions += 1;
    }
    if p9 == 0 && p2 == 1 {
        transitions += 1;
    }
    transitions
}
