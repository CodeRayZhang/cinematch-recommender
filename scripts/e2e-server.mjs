import { spawn } from "node:child_process";
import os from "node:os";
import { mkdtempSync, rmSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
const root = fileURLToPath(new URL("../", import.meta.url));
const jar = path.join(root,"backend/target/cinematch-2.0.0-SNAPSHOT.jar");
if(!existsSync(jar)) throw new Error("先执行前端构建和 Maven verify，生成 E2E 使用的 JAR。");
const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME,"bin/java") : "java";
const modelData = mkdtempSync(path.join(os.tmpdir(), "cinematch-e2e-models-"));
const child = spawn(java,["-jar",jar,
  "--server.port=18081","--server.address=127.0.0.1",
  "--spring.datasource.url=jdbc:h2:mem:browser_e2e;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
  "--spring.datasource.username=sa","--spring.datasource.password=",
  "--server.servlet.session.cookie.secure=false",
  "--app.admin.username=e2e_admin","--app.admin.password=E2e-only-password-842!",
  "--app.catalog.import-file=",
  `--app.recommender.data-directory=${modelData}`,
  `--app.catalog.poster-directory=${path.join(root,"frontend/e2e/fixtures/catalog")}`,
],{cwd:path.join(root,"backend"),stdio:"inherit"});
child.on("error",error=>{console.error(error.message);process.exit(1);});
child.on("exit",code=>{rmSync(modelData,{recursive:true,force:true});process.exit(code||0);});
for(const signal of ["SIGINT","SIGTERM"]) process.on(signal,()=>child.kill(signal));
