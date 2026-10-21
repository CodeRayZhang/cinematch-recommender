import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Presentation, PresentationFile } from '@oai/artifact-tool';
const HERE=path.dirname(fileURLToPath(import.meta.url));
const ROOT=process.env.DEFENSE_WORKSPACE || path.resolve(HERE,'..');
const SKILL=process.env.PRESENTATIONS_SKILL_DIR;
if(!SKILL) throw new Error('Set PRESENTATIONS_SKILL_DIR to the installed presentations skill directory.');
const {applyPresentationChartFont}=await import(path.join(SKILL,'container_tools/artifact_tool_utils.mjs'));
const A=path.join(ROOT,'assets'), B=path.join(ROOT,'.build');
const FONT='Arial Unicode MS';
const C={bg:'#F4F4EE',ink:'#183128',green:'#426955',pale:'#DEE7D8',muted:'#5D6C63',orange:'#D86F50',dark:'#141B18',white:'#F7F7F0',line:'#B8C6B7'};
const REPO='https://github.com/RayXBuilds/cinematch-recommender';
const SRC=REPO+'/blob/868059a/';
const doc=n=>SRC+'docs/'+n;
const screenshotNote='页面截图来自独立临时 H2 演示实例，使用仓库 923 部片库与真实 MovieLens 1M 训练模型。演示账号及 6 条个人评分专为答辩建立，不代表真实线上用户或流量。截图日期：2026-10-14。电影图片来源见 '+doc('artwork.md')+' 与 '+SRC+'data/catalog/NOTICE.md';
const p=Presentation.create({slideSize:{width:1280,height:720}});
const agenda=[], tableSlides=[], chartSlides=[];
function text(s,str,x,y,w,h,size=28,color=C.ink,bold=false,align='left'){
  const a=s.shapes.add({geometry:'textbox',name:str.slice(0,45),position:{left:x,top:y,width:w,height:h},fill:'none',line:{fill:'none',width:0}});
  a.text=str;a.text.style={typeface:FONT,fontSize:size,bold,color,alignment:align,verticalAlignment:'top',insets:0,autoFit:'none',wrap:'square'};return a;
}
function rule(s,x,y,w,color=C.line){s.shapes.add({geometry:'connector',position:{left:x,top:y,width:w,height:0},line:{fill:color,width:1}});}
function slide(title,section,seconds,narrative,sources=[],dark=false){
  const s=p.slides.add();s.background.fill=dark?C.dark:C.bg;const n=p.slides.items.length;
  text(s,section.toUpperCase(),56,28,1080,28,17,dark?C.pale:C.green);
  text(s,title,56,78,1168,70,44,dark?C.white:C.ink,true);
  rule(s,56,662,1168,dark?'#39473E':C.line);
  text(s,'CineMatch  /  Movie Recommendation System',56,677,960,25,16,dark?C.pale:C.muted);
  text(s,String(n).padStart(2,'0'),1155,674,68,29,20,dark?C.pale:C.muted,false,'right');
  if(n>18)s.hidden=true;
  s.speakerNotes.text=(seconds?'建议讲述 '+seconds+' 秒。\n\n':'附录，按提问选讲。\n\n')+narrative+'\n\n资料来源\n'+sources.join('\n');
  agenda.push({n,title,section,seconds,narrative,sources});return s;
}
function table(s,values,x,y,width,height,widths,size=25){
  const t=s.tables.add({rows:values.length,columns:values[0].length,left:x,top:y,width,height,values,columnWidths:widths});
  t.borders.assign({fill:C.line,width:.7,style:'solid'});
  for(let r=0;r<values.length;r++){
    t.rows[r].height=height/values.length;
    for(let c=0;c<values[0].length;c++){
      const cell=t.getCell(r,c);cell.fill=r===0?C.green:(r%2?C.bg:'#E8EDE3');
      cell.text.style={typeface:FONT,fontSize:size,color:r===0?C.white:C.ink,bold:r===0,verticalAlignment:'middle',autoFit:'none'};
    }
  }
  t.cells.block({row:0,column:0,rowCount:values.length,columnCount:values[0].length}).assign({margins:{left:18,right:18,top:12,bottom:10},anchor:'center'});
  tableSlides.push(p.slides.items.indexOf(s)+1);return t;
}
function node(s,label,x,y,w,h,fill=C.pale,size=27){
  const a=s.shapes.add({geometry:'rect',name:label,position:{left:x,top:y,width:w,height:h},fill,line:{fill:C.green,width:1.3}});
  a.text=label;a.text.style={typeface:FONT,fontSize:size,bold:false,color:C.ink,alignment:'center',verticalAlignment:'middle',insets:12,autoFit:'none'};return a;
}
function link(s,a,b,from='right',to='left',color=C.green){s.shapes.connect(a,b,{kind:'straight',fromSide:from,toSide:to,line:{fill:color,width:2},tail:{type:'triangle',width:'med',length:'med'}});}
async function pic(s,file,x,y,w,h,fit='contain'){
  const bytes=await fs.readFile(path.join(A,file));s.images.add({blob:bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength),contentType:file.endsWith('.jpg')?'image/jpeg':'image/png',alt:file,fit,position:{left:x,top:y,width:w,height:h}});
}
function pair(s,top,label,body,x=56,w=1120,dark=false){text(s,label,x,top,w,40,29,dark?C.white:C.ink,true);text(s,body,x,top+50,w,90,26,dark?C.pale:C.muted);}
function note(s,str,y=614,color=C.muted){text(s,str,56,y,1168,38,20,color);}

// 01 Cover: original project artwork, native editable title.
{
const s=slide('','PROJECT DEFENSE',25,'各位老师好，今天介绍 CineMatch 电影推荐系统的设计与实现。本次重构保留电影推荐这一核心，建立可独立部署的现代网站，并将离线 ALS 训练、实时个人向量计算和融合排序接入真实页面。接下来先介绍系统与数据，再重点解释推荐算法、评估结果，最后演示评分如何影响推荐。',[SRC+'README.md',doc('recommendation-models.md'),'封面原图 https://image.tmdb.org/t/p/original/rAiYTfKGqDCRIIqo664sY9XZIvQ.jpg'],true);
await pic(s,'cover.jpg',690,0,590,652,'cover');
text(s,'CineMatch',56,197,620,100,78,C.white,true);
text(s,'Movie Recommendation System',60,308,605,45,27,C.pale);
text(s,'电影推荐系统设计与实现',56,401,605,70,39,C.white,true);
text(s,'可训练的 ALS-WR 与多信号融合推荐',58,489,605,85,27,C.pale);
}
// 02 Legacy comparison.
{
const s=slide('项目背景与重构目标','01  SYSTEM',40,'旧项目已经包含电影浏览、用户管理和基于 ALS 的推荐探索，也采用了 SSM 与大数据组件。本次目标是将毕业设计变成可以长期维护的个人项目，因此选用单进程 Java 服务、独立前端源码和可选 MySQL，降低部署负担。这里不是将旧系统的架构图换一个名称，而是重新实现业务和训练链路。原仓库完整保留，新系统不自动导入旧账号、旧评分或旧模型。',['https://github.com/RayXBuilds/Movie_Recommend',SRC+'README.md']);
table(s,[['维度','原毕业设计','CineMatch'],['应用架构','SSM、JSP、MySQL','Vue 3、Spring Boot 4、Java 21'],['推荐与运行','Spark ALS 与大数据组件','Java ALS-WR、请求时个人化'],['项目维护','历史模块与环境耦合','独立仓库、迁移脚本、自动验收'],['数据继承','原系统账号与模型','新账号、新数据库、新训练链路']],56,181,1168,355,[170,450,548],25);
note(s,'原项目完整保留。新版无需启动旧项目及 Spark / Hadoop。',587);
}
// 03 Scope.
{
const s=slide('系统角色与功能范围','01  SYSTEM',35,'游客无需登录即可搜索电影、查看详情和使用初步推荐。登录后，用户可以评分、记录想看和看过、建立私人片单，并给推荐反馈。管理员维护电影和专题，也能够训练和切换模型。这里特意说明用户概览是只读的，系统尚未做播放、社交关系或多级管理员，避免将原项目的设想误认为新版已经具备的功能。',[SRC+'README.md',doc('usage.md')]);
table(s,[['角色','主要入口','已实现的操作'],['游客','发现、详情、推荐','搜索筛选、电影资料、相似电影、冷启动推荐'],['登录用户','个人中心、片单、推荐实验室','评分短评、私人合集、偏好、隐藏与恢复'],['管理员','管理后台','电影与专题维护、模型训练与版本管理\n用户概览只读、推荐观察']],56,183,1168,355,[180,310,678],26);
note(s,'推荐目标是帮助用户选片。当前不提供电影播放和社交关系功能。',587);
}
// 04 Architecture native diagram.
{
const s=slide('当前系统技术架构','01  SYSTEM',55,'前端使用 Vue、TypeScript 和 Vite，开发时页面与后端分开运行，生产时将静态页面打入同一 JAR。请求经过 Spring Security，由 Spring Boot 服务处理。电影、片单、账号和推荐各有模块边界，MyBatis 访问数据库，Flyway 管理结构演进。H2 用于本地持久化，MySQL 是可选部署方式。ALS 训练在单个后台线程执行，网站继续使用当前活动模型。模型参数和活动指针也进入数据库，而原始评分与训练快照放在数据目录。',[doc('architecture.md'),SRC+'backend/pom.xml',SRC+'frontend/package.json',doc('recommendation-models.md')]);
const a=node(s,'浏览器\nVue 3 + TypeScript + Vite',56,186,340,104);
const b=node(s,'Spring Security\nSession / CSRF / 角色控制',457,186,385,104);
const c=node(s,'Spring Boot 4 + Java 21\n电影、账号、片单、推荐',457,345,385,115);
const d=node(s,'ALS 训练任务\n单线程、版本切换',902,345,322,115);
const e=node(s,'MyBatis + Flyway\nH2 文件库 / MySQL 8.4',457,515,385,105);
const f=node(s,'本地文件\n海报、评分、训练快照',902,515,322,105);
link(s,a,b);link(s,b,c,'bottom','top');link(s,c,d);link(s,c,e,'bottom','top');link(s,d,f,'bottom','top');
text(s,'生产构建\n静态页面与 API\n打包为同一个 JAR',64,367,325,151,27,C.muted);
}
// 05 Data boundaries.
{
const s=slide('片库与训练评分的两条数据链路','02  DATA',50,'电影元数据负责页面展示，历史评分负责学习用户与电影之间的关系，这两者不能混为一谈。仓库已经收录 923 部电影，克隆后可以直接导入。MovieLens 的一百万条匿名评分需要按文档另外获取，不会创建本站用户。模型可以学习 3706 部历史电影，但当前片库只能严格对应其中 90 部。匹配依据是来源 ID、IMDb 对照与标题年份校验，不是把两个数据库相同的数字 ID 直接连接。未映射电影仍能通过内容与邻域推荐展示。',[doc('data-sources.md'),doc('recommendation-models.md'),SRC+'data/catalog/manifest.json']);
text(s,'网站片库',56,180,550,42,29,C.green,true);text(s,'模型训练源',668,180,550,42,29,C.green,true);
text(s,'923 部',56,237,550,94,66,C.ink,true);text(s,'1,000,209 条',668,237,550,94,61,C.ink,true);
text(s,'电影资料、来源与海报随仓库提供\n导入后用于搜索、详情和内容特征',56,351,550,110,27,C.muted);
text(s,'6,040 位匿名用户，3,706 部电影\n评分源独立下载，不创建本站账号',668,351,550,110,27,C.muted);
rule(s,56,494,1168);text(s,'严格映射：90 / 923',56,527,570,56,38,C.orange,true);
text(s,'约 9.75% 片库可参与当前 ALS\n其余电影继续使用基础混合推荐',668,521,550,95,27,C.muted);
}
// 06 Catalog screenshot.
{
const s=slide('电影发现与组合筛选','03  PRODUCT',40,'网站首先解决电影信息的组织与发现。搜索覆盖电影和导演，类型、年份、时长可以组合筛选，配合排序与分页。首页专题由后台配置。电影资料和海报随仓库分发，因此拿到代码的使用者可以直接复现完整片库。页面的参考分属于外部历史快照，个人评分单独记录，不能将二者当成实时的统一评分。',[doc('usage.md'),doc('data-sources.md'),screenshotNote]);
await pic(s,'catalog.png',56,170,830,466,'contain');
pair(s,198,'组合筛选','搜索 / 类型 / 年份\n时长 / 排序 / 分页',922,302);
pair(s,397,'完整片库','923 部电影\n来源与历史参考分可追溯',922,302);
}
// 07 Collection.
{
const s=slide('私人片单与观影记录','03  PRODUCT',40,'个人中心将长期类型偏好、观影统计和评分记录放在一起。想看、看过和评分有独立语义，移出片单不删除评分。私人合集支持名称、介绍、封面、逐片备注、排序和导出。图中使用专门的演示账号，备注和选择都是实际保存的内容。这样的用户操作既具有产品价值，也为推荐产生可解释的行为信号。',[doc('usage.md'),screenshotNote]);
await pic(s,'collection.png',56,170,830,466,'contain');
pair(s,190,'个人记录','想看与看过\n评分、短评、类型偏好',922,302);
pair(s,391,'自定义片单','备注、封面、排序\n批量操作与 JSON 导出',922,302);
}
// 08 Lab.
{
const s=slide('推荐实验室：评分如何形成个人口味','04  RECOMMENDATION',60,'推荐实验室把计算过程的一部分直接呈现给用户。图中这个账号为 6 部电影打过分，模型状态来自真实训练结果。右侧的观影指纹是已评分电影的类型计数，按最大值归一化，它不代表准确率，也不是潜在向量的语义解释。下方分别显示评分数量、16 维协同模型和本站覆盖范围。至少为 3 部已映射电影评分后，系统才会计算个人 ALS 向量。推荐结果还会显示理由，并支持隐藏或换一组，反馈影响下一次请求。',[doc('recommendation-models.md'),doc('recommendation.md'),screenshotNote],true);
await pic(s,'lab-detail.png',56,166,1168,458,'contain');
note(s,'真实演示账号：6 部已评分电影。观影指纹展示类型分布，不代表模型准确率。',625,C.pale);
}
// 09 Pipeline.
{
const s=slide('推荐请求的计算流程','04  RECOMMENDATION',60,'每次推荐先读取当前用户的偏好、最新评分和活动模型，再过滤已评分、已收藏、已隐藏的电影与本次已看过的批次。随后同时计算内容、口碑、相似用户和可用 ALS 预测分。融合后不会直接机械地取前十二，而是进行多样性重排，降低高度相似电影重复出现的程度。探索模式在相关性约束内优先安排部分较少被关注的电影。最后输出最多十二部电影与依据。这意味着体验上的反馈和算法中的排序是连接在一起的。',[doc('recommendation.md'),SRC+'backend/src/main/java/com/cinematch/movie/RecommendationService.java']);
const a=node(s,'读取用户信号\n最新评分与偏好',56,194,320,100);
const b=node(s,'候选过滤\n已看 / 已藏 / 隐藏',480,194,320,100);
const c=node(s,'多信号评分\n内容 + 邻域 + ALS',904,194,320,100);
const d=node(s,'返回最多 12 部\n逐片推荐依据',56,397,320,100);
const e=node(s,'多样性重排\n精选 / 探索模式',480,397,320,100);
const f=node(s,'加权融合\n缺失信号自动回退',904,397,320,100);
link(s,a,b);link(s,b,c);link(s,c,f,'bottom','top');link(s,f,e,'left','right');link(s,e,d,'left','right');
text(s,'探索模式保留相关性门槛；相似度惩罚减少重复；同一导演通常最多 2 部。',56,552,1168,70,26,C.muted);
}
// 10 ALS concept.
{
const s=slide('ALS-WR：离线学习与请求时个人化','04  RECOMMENDATION',60,'ALS 通过低维用户向量和电影向量的内积逼近已观察评分。交替固定一边、求解另一边，迭代得到电影表示。WR 指正则强度按观测数量缩放。当前默认使用 16 维、15 轮、正则系数 0.1。离线训练完成后持久化电影向量。面对新注册用户，不需要重新训练整个模型，而是用这个用户最新的已映射评分，固定电影向量，求解一个个人向量。下一次请求可以立即利用新的评分，但全局模型仍通过离线任务更新，这与流式全局重训有区别。',[doc('recommendation-models.md'),doc('recommendation.md'),SRC+'backend/src/main/java/com/cinematch/recommender/AlsModel.java']);
text(s,'评分预测 ≈ 用户向量 × 电影向量',56,174,1168,65,40,C.green,true);
const a=node(s,'历史评分矩阵\n只学习已观察评分',56,286,320,100);
const b=node(s,'交替最小二乘\n16 维 / 15 轮 / λ = 0.1',480,286,330,100);
const c=node(s,'电影向量 Y\n模型版本持久化',914,286,310,100);
link(s,a,b);link(s,b,c);
const d=node(s,'本站用户最新评分\n至少 3 部映射电影',56,480,320,104);
const e=node(s,'固定 Y，求解 xᵤ\n不重训全部用户',480,480,330,104);
const f=node(s,'预测评分\n用于融合排序',914,480,310,104);
link(s,d,e);link(s,e,f);link(s,c,e,'bottom','top');
}
// 11 score.
{
const s=slide('融合得分与冷启动回退','04  RECOMMENDATION',50,'基础分由平滑口碑、内容偏好和邻域协同组成。内容向量包括类型、导演、国家与年代，负向评价会产生惩罚。邻域部分使用带收缩的 Pearson 相关性，限制少量共同评分带来的偶然高相关。ALS 可用时，将预测星级归一化后乘 0.45 加入基础分。这个权重只在验证集选择。没有模型、映射不足或新用户评分不足时，已有基础算法继续工作，游客也能按偏好获取推荐。',[doc('recommendation.md'),doc('recommendation-model-evaluation.md')]);
table(s,[['信号','作用','系数'],['平滑口碑 Q','为低信息用户提供质量基线','0.30'],['内容偏好 C','类型、导演、国家、年代及负反馈','0.70'],['邻域协同 N','相似用户的评分偏差预测','0.65'],['ALS 预测 A','(预测星级 − 3) / 2，须有有效映射','0.45']],56,175,1168,350,[275,713,180],25);
text(s,'基础分 = 0.30 Q + 0.70 C + 0.65 N',56,547,1168,45,30,C.green,true);
note(s,'ALS 可用时再加 0.45 A。无历史且有明确偏好时，冷启动使用 0.85 Q + 0.15 C。',606);
}
// 12 lifecycle screenshot.
{
const s=slide('训练、启用与版本回退','04  RECOMMENDATION',45,'管理员选择训练源与参数后，任务在后台执行，界面显示迭代进度与状态。训练完成只代表计算成功，系统明确记录 evaluated 为 false，不自动声称效果提升。检查离线评估后，由管理员启用版本，下一次请求读取活动指针。旧模型保留时可以随时切回，也能停用 ALS 恢复基础混合推荐。图片中的一百万条评分和九十部映射来自本次真实训练，页面上的训练时长只是这次运行，不能作为通用性能承诺。',[doc('recommendation-models.md'),screenshotNote]);
await pic(s,'models-detail.png',56,175,1168,360,'contain');
text(s,'QUEUED  /  RUNNING  /  READY 或 FAILED',56,553,1168,46,30,C.green,true);
note(s,'READY 表示训练成功。效果评估与是否启用由独立流程决定。',609);
}
// 13 Evaluation protocol.
{
const s=slide('按时间隔离训练、验证与测试','05  EVALUATION',55,'为了减少未来信息泄漏，按全局时间分成七成训练、一成验证、两成测试，相同时间戳保留在较早分区。验证阶段只选择融合权重，候选是 0.45、1.5 和 3.0。测试阶段冻结选择，使用前八成重新训练，评估最后两成。每个阶段取一百位满足历史和未来正样本数量条件的用户，正反馈定义为四星及以上。候选是训练阶段的完整电影集合减去用户已看电影，没有使用容易高估效果的少量随机负样本。',[doc('recommendation-model-evaluation.md'),doc('recommendation-model-test.json'),doc('recommendation-model-validation.json')]);
table(s,[['阶段','历史评分','未来评分','用途'],['验证','700,148','100,020','从 0.45 / 1.5 / 3.0 选择融合权重'],['测试','800,168','200,041','冻结参数，以前 80% 重新训练']],56,179,1168,219,[160,225,225,558],25);
text(s,'Top 12，全候选排序',56,439,1168,48,36,C.green,true);
text(s,'100 位合格用户；每人至少 20 条历史评分、5 个未来正样本\n测试训练目录 3,662 部电影，再排除各用户已看电影\n正样本：未来评分 ≥ 4 星',56,503,1168,132,26,C.muted);
}
// 14 Native evidence chart.
{
const s=slide('融合改善原方案，热门基线仍然更强','05  EVALUATION',65,'这一页展示主要排序指标 NDCG@12，它更重视将用户未来喜欢的电影排在靠前位置。融合方案从基础混合的 0.1826 提升到 0.2005，相对提升约百分之九点八。Recall 也从百分之四点四九提升到五点一一。但是热门基线达到 0.2353，依然高于融合方案，单独使用 ALS 的结果也较弱。因此目前合理的结论是 ALS 能改善现有混合推荐，不能声称已经全面优于简单基线。附录保留了完整指标及配对抽样区间。',[doc('recommendation-model-test.json'),doc('recommendation-model-evaluation.md')]);
const ch=s.charts.add('bar',{position:{left:56,top:196,width:790,height:387},categories:['热门基线','基础混合','ALS 16 维','融合 0.45'],series:[{name:'NDCG@12',values:[.23534688,.18255221,.08117434,.20051378],fill:C.green,points:[{idx:0,fill:'#A7B7A1'},{idx:1,fill:'#829987'},{idx:2,fill:'#B9C4B2'},{idx:3,fill:C.orange}]}],barOptions:{direction:'column',grouping:'clustered',gapWidth:75},hasLegend:false,dataLabels:{showValue:true,position:'outEnd',numberFormatCode:'0.0000',textStyle:{fontSize:23,fill:C.ink,bold:true}},yAxis:{min:0,max:.27,majorUnit:.05,numberFormatCode:'0.00',textStyle:{fontSize:20,fill:C.muted},majorGridlines:{fill:C.line,width:.5}},xAxis:{textStyle:{fontSize:23,fill:C.ink},line:{fill:C.line,width:1}},chartFill:'none',plotAreaFill:'none'});applyPresentationChartFont(ch,{fontFamily:FONT});chartSlides.push(14);
text(s,'NDCG@12',56,157,790,38,22,C.muted);
text(s,'+9.84%',907,225,317,81,56,C.orange,true);
text(s,'相对基础混合\nNDCG 提升',907,319,317,90,29,C.ink);
text(s,'Recall@12\n4.49% → 5.11%',907,453,317,88,29,C.green);
note(s,'MovieLens 时间切分测试，100 位用户。该结果不代表 923 部本站片库的在线效果。',612);
}
// 15 Limitations.
{
const s=slide('效果边界与当前取舍','05  EVALUATION',45,'排序指标提升并不是所有指标都改善。融合后覆盖率从百分之五点六降到四点九四，说明推荐集中度有所增加。同时，历史 MovieLens 与当前中文片库的交集较小，九十部映射限制了线上 ALS 的参与范围。评估用户已有较多历史记录，所以不能用这组结果证明新用户三条评分就有同等效果。本站已有曝光、点击和后续收藏评分的观察能力，但还没有随机分流的在线 A/B 实验，不能作因果结论。',[doc('recommendation-model-evaluation.md'),doc('recommendation-observation.md')]);
table(s,[['边界','当前事实','后续改进'],['覆盖与集中度','目录覆盖 5.60% 降至 4.94%','校准多样性与热门惩罚'],['应用映射','90 / 923 部可用于当前 ALS','扩充可靠映射及本站评分'],['人群外推','每人至少 20 条历史记录','单独评估冷启动和少评分用户'],['线上证据','已有行为观察，尚无随机 A/B','增加曝光量后设计随机实验']],56,182,1168,354,[235,435,498],25);
note(s,'训练成功、离线指标提升和线上用户满意度，是三个需要分别验证的结果。',591);
}
// 16 Engineering.
{
const s=slide('工程验证与部署方式','06  ENGINEERING',40,'算法之外，系统还需要保证用户操作与数据持久化可靠。最近一次完整流水线通过了六十四项后端测试、十六项桌面与手机浏览器测试以及六项 Python 检查。后端包含真实 MySQL 的 HTTP 验证，两个可选离线评估测试另行运行。Compose 验收检查了模型启用状态和用户评分片单在数据库及应用重启后保留。部署既支持本地 H2，也支持 Docker 加 MySQL。这些验证说明功能与持久化可复现，不等同于高并发压测。',[doc('verification.md'),'https://github.com/RayXBuilds/cinematch-recommender/actions/runs/38586333537',doc('deployment.md')]);
text(s,'64',56,195,330,105,75,C.green,true);text(s,'后端测试通过',56,309,330,47,29,C.ink);text(s,'含真实 MySQL HTTP 验证\n另有 2 项可选评估跳过',56,375,330,95,24,C.muted);
text(s,'16',470,195,330,105,75,C.green,true);text(s,'浏览器测试通过',470,309,330,47,29,C.ink);text(s,'桌面与手机\n搜索、片单、推荐、后台',470,375,330,95,24,C.muted);
text(s,'6',884,195,330,105,75,C.green,true);text(s,'Python 检查通过',884,309,330,47,29,C.ink);text(s,'片库、数据准备与校验\n文件清单及身份映射',884,375,330,95,24,C.muted);
rule(s,56,513,1168);text(s,'部署：单 JAR + H2，或 Docker Compose + MySQL 8.4',56,547,1168,52,29,C.ink,true);
note(s,'流水线基准 c3fb14b。已验证重启持久化，尚未进行最大并发负载测试。',612);
}
// 17 Demo.
{
const s=slide('现场演示：评分、推荐与反馈','07  DEMO',90,'演示按四步进行。首先展示已有六条评分的演示账号和活动模型状态，说明观影指纹来自真实记录。然后进入评分引导，修改一部映射电影的评分，返回推荐页面，说明最新记录已用于个人向量求解，不保证每次都出现明显的名次变化。接着查看某部电影的推荐依据，选择不再推荐，验证它被排除，并尝试探索模式或下一组。最后查看后台活动版本，说明可以切换与回退。若现场服务异常，就以这组真实截图继续讲解，不现场重新下载数据或等待训练。',[doc('usage.md'),doc('recommendation-models.md'),screenshotNote]);
await pic(s,'results.png',56,169,790,468,'contain');
text(s,'01  查看口味与模型',890,181,334,45,27,C.green,true);text(s,'6 条评分，16 维活动模型',890,232,334,72,24,C.muted);
text(s,'02  修改一条评分',890,323,334,45,27,C.green,true);text(s,'重新请求推荐，说明即时求解',890,373,334,78,24,C.muted);
text(s,'03  隐藏与探索',890,464,334,45,27,C.green,true);text(s,'观察排除规则\n再查看后台版本',890,514,334,88,24,C.muted);
}
// 18 conclusion.
{
const s=slide('项目贡献与下一步','08  DISCUSSION',30,'本项目完成了现代化电影网站、可训练并管理版本的 ALS 推荐，以及能够复现的时间切分评估。当前最主要的限制是映射覆盖与热门基线优势。下一步会先扩充可验证的数据和本站评分，再改善偏差校准及冷启动评估，最后在有足够行为数据后做在线实验。我的介绍到这里，欢迎老师提问。',[SRC+'README.md',doc('recommendation-model-evaluation.md')],true);
text(s,'可运行的完整系统',56,197,1168,54,38,C.white,true);
text(s,'发现电影、私人片单、推荐实验室与后台管理',56,260,1168,60,28,C.pale);
text(s,'可训练、可切换、可解释的推荐链路',56,361,1168,58,38,C.white,true);
text(s,'ALS-WR + 内容与邻域融合，保留评估协议与真实边界',56,429,1168,65,28,C.pale);
text(s,'下一步：扩大可靠映射、校准热门偏差、验证冷启动',56,565,1168,51,29,'#E5AD92');
}
// 19 Appendix math.
{
const s=slide('附录 A  ALS-WR 目标与个人向量求解','APPENDIX',0,'ALS 只优化观察到的显式评分。目标包括平方误差和按每位用户、每部电影评分数量加权的 L2 正则。交替固定电影向量与用户向量求解。上线后保存电影向量，对当前用户最新映射评分作一次线性求解。预测星级限制在 0.5 到 5 之间，随后归一化参与排序。个人向量可能由 3 条评分求出，但这只是功能启用门槛，不是推荐质量保证。',[doc('recommendation.md'),SRC+'backend/src/main/java/com/cinematch/recommender/AlsModel.java',SRC+'backend/src/main/java/com/cinematch/recommender/ModelPersonalizer.java']);
text(s,'离线目标（Ω 为已观察评分）',56,177,1168,45,29,C.green,true);
text(s,'min  Σ(u,i)∈Ω (rᵤᵢ − xᵤᵀyᵢ)²',56,241,1168,62,39,C.ink);
text(s,'        + λ [ Σu nᵤ ‖xᵤ‖² + Σi nᵢ ‖yᵢ‖² ]',56,310,1168,62,39,C.ink);
rule(s,56,402,1168);text(s,'固定电影向量，使用当前用户最新评分',56,439,1168,45,29,C.green,true);
text(s,'xᵤ = solve( Σi yᵢyᵢᵀ + λ nᵤ I,  Σi rᵤᵢyᵢ )',56,504,1168,65,36,C.ink);
note(s,'默认 rank = 16，iterations = 15，λ = 0.1，seed = 42。预测星级限制为 0.5–5。',611);
}
// 20 complete metrics.
{
const s=slide('附录 B  完整测试指标','APPENDIX',0,'Precision 是十二个推荐中命中未来正样本的比例。Recall 是未来正样本被找回的比例。NDCG 考虑推荐位置。目录覆盖率是所有评估用户推荐的不同电影数除以候选目录大小，类型多样性来自推荐对之间的类型差异。配对 Bootstrap 的 NDCG 绝对增量区间高于零，但仅适用于这组用户和数据协议。RMSE 衡量评分预测误差，不等于推荐排序效果，且有十条评分涉及训练未见电影。',[doc('recommendation-model-test.json'),doc('recommendation-model-evaluation.md')]);
table(s,[['方法','P@12','R@12','NDCG@12','目录覆盖','类型多样性'],['热门基线','0.2167','0.0533','0.2353','3.17%','0.7847'],['基础混合','0.1650','0.0449','0.1826','5.60%','0.5604'],['ALS 1 维','0.0050','0.0005','0.0057','1.39%','0.3784'],['ALS 16 维','0.0950','0.0280','0.0812','5.79%','0.7342'],['融合 0.45','0.1850','0.0511','0.2005','4.94%','0.6029']],56,177,1168,330,[255,165,165,190,188,205],23);
text(s,'NDCG 绝对增量 95% 区间：[0.0080, 0.0282]',56,533,1168,43,28,C.green);
text(s,'ALS RMSE = 0.8694（11,438 条可预测评分，10 条未见电影评分）',56,586,1168,48,25,C.muted);
}
// 21 Entity ownership.
{
const s=slide('附录 C  数据关系与权限边界','APPENDIX',0,'账号拥有自己的评分、片单和推荐反馈。收藏与评分是独立关系，私人合集还有逐片备注和排序。电影是共享实体，修改由管理员控制。模型版本保存参数、摘要与电影向量，活动指针决定当前请求使用哪一个版本。图表示逻辑实体关系而非数据库全字段。MovieLens 用户只存在于训练数据命名空间，不是网站账号。观察数据可以暂停和清除。',[doc('architecture.md'),doc('recommendation-models.md'),doc('recommendation-observation.md')]);
const u=node(s,'账号 USER / ADMIN',56,290,310,85);
const r=node(s,'个人评分 / 观影片单',480,181,310,85);
const col=node(s,'私人合集与备注',480,344,310,85);
const m=node(s,'共享电影资料',914,290,310,85);
const mod=node(s,'模型版本\n参数、摘要、电影向量',914,493,310,108);
const ptr=node(s,'当前活动模型指针',480,505,310,85);
const obs=node(s,'曝光与归因记录\n用户可暂停、清除',56,499,310,96);
link(s,u,r);link(s,u,col);link(s,r,m);link(s,col,m);link(s,ptr,mod);link(s,mod,m,'top','bottom');link(s,u,obs,'bottom','top');
note(s,'逻辑关系图。MovieLens 匿名用户与本站账号使用独立命名空间。',621);
}
// 22 Deployment.
{
const s=slide('附录 D  克隆后的运行与训练顺序','APPENDIX',0,'首次部署先安装 Java21、Node22.12以上、Maven3.9以上和 Python3.9以上。管理员通过环境变量初始化，不存在公开默认账号。导入仓库片库后，另外下载官方 MovieLens1M 和链接表，准备 Wikidata 对照，运行 prepare_recommender 生成训练目录。后台选择数据集训练，完成后检查评估再启用。首次导入片库和训练应顺序执行，避免映射身份尚未建立。原始评分和已训练模型不随 Git 发布，但文档提供完整重建步骤。',[doc('deployment.md'),doc('recommendation-models.md')]);
pair(s,171,'1  启动完整片库','Java 21、Node ≥ 22.12、Maven ≥ 3.9、Python ≥ 3.9\n仓库根目录执行：bash scripts/dev.sh --catalog');
pair(s,331,'2  准备独立训练源','下载官方 MovieLens 1M、links 与 Wikidata 对照\n运行 scripts/prepare_recommender.py，输出映射与 SHA-256 清单');
pair(s,491,'3  训练并启用','/admin 选择 movielens-1m，训练完成后检查评估并启用\n/picks 为至少 3 部已映射电影评分，参与个人 ALS 计算');
}
// 23 Q&A.
{
const s=slide('附录 E  常见追问','APPENDIX',0,'如果被问为什么不使用 Spark，回答当前数据与单用户项目维护成本更适合单进程实现，需要更大规模时再拆分训练服务。如果问为什么热门更好，回答历史数据、时间分布、候选与采样协议可能使热门强势，这只是待验证解释，不能武断归因。若问九十部覆盖，解释严格身份映射和年代交集限制。若问是不是大模型，明确不是，当前使用矩阵分解和结构化特征。若问推荐是否实时，区分用户个人向量按请求更新与全局模型离线重训。',[doc('recommendation-model-evaluation.md'),doc('recommendation-models.md'),doc('recommendation.md')]);
table(s,[['问题','回答要点'],['为什么不用 Spark？','当前规模采用单进程 CPU ALS，降低部署与维护成本。'],['热门基线更高，融合有什么意义？','相对原方案有提升，但仍需校准偏差并扩展验证。'],['为什么只映射 90 部？','数据年代与片库重叠有限，且需严格身份和年份校验。'],['是否实时训练，是否使用大模型？','个人向量按最新评分求解，全局模型离线训练。未使用 LLM。'],['3 条评分就能保证准确？','这是参与个人 ALS 的门槛，冷启动效果需要另行评估。']],56,180,1168,423,[465,703],24);
}
// 24 references.
{
const s=slide('附录 F  资料与复现入口','APPENDIX',0,'本演示依据 CineMatch 仓库 868059a 的实现与文档。完整评估、测试 JSON、数据来源和部署命令均在仓库中。旧项目仅用于说明历史背景，不作为新版已经实现功能的证据。截图来自隔离演示环境，电影海报和封面属于第三方内容，其来源和使用说明见项目 attribution 文件。',['https://github.com/RayXBuilds/Movie_Recommend',REPO,doc('recommendation-model-evaluation.md'),doc('recommendation-model-test.json'),doc('deployment.md'),doc('recommendation-models.md'),'https://grouplens.org/datasets/movielens/1m/']);
text(s,'项目代码',56,176,1168,43,29,C.green,true);text(s,'github.com/RayXBuilds/cinematch-recommender',56,232,1168,48,32,C.ink);
text(s,'算法与评估',56,324,1168,43,29,C.green,true);text(s,'docs/recommendation.md\ndocs/recommendation-model-evaluation.md\ndocs/recommendation-model-test.json',56,378,1168,126,26,C.ink);
text(s,'部署与训练',56,538,1168,40,29,C.green,true);text(s,'docs/deployment.md  ·  docs/recommendation-models.md',56,591,1168,43,26,C.ink);
}

await fs.mkdir(B,{recursive:true});
await (await PresentationFile.exportPptx(p)).save(path.join(B,'candidate.pptx'));
await fs.writeFile(path.join(B,'requirements.json'),JSON.stringify({slideCount:24,tableSlides,chartSlides,fontPolicy:{basis:'design',families:[FONT]},baseCommit:'868059a'},null,2));
await fs.writeFile(path.join(B,'agenda.json'),JSON.stringify(agenda,null,2));
await fs.mkdir(path.join(B,'preview'),{recursive:true});
for(let i=0;i<p.slides.items.length;i++){
 const blob=await p.export({slide:p.slides.items[i],format:'png',scale:1});
 await fs.writeFile(path.join(B,'preview',`slide-${String(i+1).padStart(2,'0')}.png`),new Uint8Array(await blob.arrayBuffer()));
}
console.log('Built',p.slides.items.length,'slides. Main time:',agenda.reduce((a,b)=>a+b.seconds,0),'seconds');
