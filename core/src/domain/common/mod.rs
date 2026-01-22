// --- 辅助函数：Hex 转 Binary ---
pub fn hex_to_binary(hex: &str) -> String {
    let mut bin = String::with_capacity(hex.len() * 4);
    for c in hex.chars() {
        if let Some(digit) = c.to_digit(16) {
            // 格式化为 4 位二进制
            bin.push_str(&format!("{:04b}", digit));
        }
    }
    bin
}

// 判断是否为 Hex 压缩串 (简单启发式：不包含 0 或 1 以外的字符，或者长度特征)
// 这里我们假设如果包含 '2'-'9' 或 'A'-'F' 肯定是 Hex。
// 如果全是 0/1，但也可能是 Hex(比如 "01")，但在字库场景下 Binary 通常很长。
// 更安全的做法是看长度：Binary 长度通常是 CellWidth * CellHeight，Hex 是其 1/4。
pub fn is_likely_hex(s: &str) -> bool {
    s.chars().any(|c| c != '0' && c != '1')
}
