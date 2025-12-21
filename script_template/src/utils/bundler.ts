import * as esbuild from 'esbuild-wasm';

let isInitialized = false;

export async function initBundler() {
  if (isInitialized) return;

  console.log("Bundler: 开始初始化...");
  try {
    await esbuild.initialize({
      worker: false, // 1. 禁用 Worker，修复 WebView 兼容性
      wasmURL: './esbuild.wasm'
    });
    isInitialized = true;
    console.log("Bundler: 初始化成功");
  } catch (e) {
    console.error("Bundler: 初始化失败", e);
    throw e;
  }
}

export async function bundleScript(fileMap: Record<string, string>) {
  await initBundler();

  try {
    console.log("Bundler: 准备打包文件:", Object.keys(fileMap));

    if (!fileMap['/index.ts']) {
      throw new Error("Missing entry point: /index.ts");
    }

    const result = await esbuild.build({
      entryPoints: ['index.ts'],
      bundle: true,
      write: false,
      format: 'iife',
      target: ['es2020'],
      globalName: 'GameScript', // 2. 关键：导出为全局变量，供 Rust 调用
      charset: 'utf8',          // 3. 关键：强制使用 UTF-8，不转义中文
      plugins: [
        {
          name: 'virtual-fs',
          setup(build) {
            build.onResolve({ filter: /.*/ }, args => {
              if (args.path === 'index.ts') return { path: '/index.ts', namespace: 'v' };

              let path = args.path;
              if (path.startsWith('./')) path = path.slice(1);
              if (!path.startsWith('/')) path = '/' + path;
              if (!path.endsWith('.ts')) path += '.ts';

              return { path, namespace: 'v' };
            });

            build.onLoad({ filter: /.*/, namespace: 'v' }, args => {
              const content = fileMap[args.path];
              if (content === undefined) {
                throw new Error(`File not found: ${args.path}`);
              }
              return { contents: content, loader: 'ts' };
            });
          }
        }
      ]
    });

    if (!result.outputFiles || result.outputFiles.length === 0) {
      throw new Error("esbuild produced no output files");
    }

    const code = result.outputFiles[0].text;
    // console.log(code); // 现在你可以看到清爽的中文了
    return code;

  } catch (e: any) {
    console.error("Bundle failed:", e);
    throw new Error(`编译失败: ${e.message}`);
  }
}
