export const GLOBAL_CONFIG_VAR = "ConfigSettings";

/**
 * 注入配置到脚本头部
 */
export function injectScriptConfig(rawScript: string, configObj: any): string {
  const configJson = JSON.stringify(configObj, null, 2);
  // 生成：var ConfigSettings = { ... };
  const injectionCode = `var ${GLOBAL_CONFIG_VAR} = ${configJson};\n\n`;
  return injectionCode + rawScript;
}
