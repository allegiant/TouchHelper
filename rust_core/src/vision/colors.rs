use crate::vision::types::ColorRule;

/// 解析 Hex 颜色字符串 "#RRGGBB" -> [u8; 3]
pub fn parse_hex(hex: &str) -> [u8; 3] {
    let hex = hex.trim_start_matches('#');
    if hex.len() >= 6 {
        let r = u8::from_str_radix(&hex[0..2], 16).unwrap_or(0);
        let g = u8::from_str_radix(&hex[2..4], 16).unwrap_or(0);
        let b = u8::from_str_radix(&hex[4..6], 16).unwrap_or(0);
        [r, g, b]
    } else {
        [0, 0, 0]
    }
}

/// 判断单个像素是否匹配目标颜色（带偏色）
#[inline(always)]
pub fn is_match(pixel: [u8; 3], target: [u8; 3], bias: [u8; 3]) -> bool {
    let r_diff = (pixel[0] as i16 - target[0] as i16).abs() as u8;
    let g_diff = (pixel[1] as i16 - target[1] as i16).abs() as u8;
    let b_diff = (pixel[2] as i16 - target[2] as i16).abs() as u8;

    r_diff <= bias[0] && g_diff <= bias[1] && b_diff <= bias[2]
}

/// 检查像素是否匹配规则列表中的任意一条
pub fn is_match_any(pixel: [u8; 3], rules: &[ColorRule]) -> bool {
    for rule in rules {
        if !rule.is_enabled {
            continue;
        }
        let target = parse_hex(&rule.target_hex);
        let bias = parse_hex(&rule.bias_hex);
        if is_match(pixel, target, bias) {
            return true;
        }
    }
    false
}
