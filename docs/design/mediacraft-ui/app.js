'use strict';

// 仅用于静态原型：所有状态存于内存，不读写真实媒体或设备。
const $ = (selector, root = document) => root.querySelector(selector);
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const paths = {
  grid:'<rect x="3" y="3" width="7" height="7" rx="1.6"/><rect x="14" y="3" width="7" height="7" rx="1.6"/><rect x="3" y="14" width="7" height="7" rx="1.6"/><rect x="14" y="14" width="7" height="7" rx="1.6"/>',
  album:'<path d="M5 7V4h14v3M3 9h18v12H3z"/><path d="m7 17 3-3 3 3 2-2 3 3"/><circle cx="16" cy="12" r=".7"/>',
  compass:'<circle cx="12" cy="12" r="9"/><path d="m16 8-2.5 5.5L8 16l2.5-5.5z"/>',
  tools:'<path d="M14 5a5 5 0 0 0-6 6L3 16a3 3 0 0 0 5 5l5-6a5 5 0 0 0 6-6l-4 3-3-3 3-4z"/>',
  search:'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
  settings:'<path d="m10 3-1 3-3 1-3 3 2 3 1 4 3 1 1 3h4l1-3 3-1 3-3-2-3-1-4-3-1-1-3z"/><circle cx="12" cy="12" r="3"/>',
  plus:'<path d="M12 5v14M5 12h14"/>',
  chevron:'<path d="m9 5 7 7-7 7"/>',
  down:'<path d="m6 9 6 6 6-6"/>',
  back:'<path d="m14 5-7 7 7 7"/>',
  close:'<path d="m6 6 12 12M6 18 18 6"/>',
  more:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
  filter:'<path d="M4 6h16M7 12h10M10 18h4"/><circle cx="8" cy="6" r="2"/><circle cx="16" cy="12" r="2"/>',
  heart:'<path d="M20.8 4.8a5.5 5.5 0 0 0-7.8 0L12 6l-1.1-1.2a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.4a5.5 5.5 0 0 0 0-7.8z"/>',
  image:'<rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8" cy="8" r="1.5"/><path d="m3 17 6-6 5 5 3-3 4 4"/>',
  video:'<rect x="3" y="5" width="13" height="14" rx="3"/><path d="m16 10 5-3v10l-5-3z"/>',
  play:'<path d="m9 5 11 7-11 7z"/>',
  pause:'<path d="M8 5v14M16 5v14"/>',
  map:'<path d="m3 5 6-2 6 2 6-2v16l-6 2-6-2-6 2zM9 3v16M15 5v16"/>',
  pin:'<path d="M19 10c0 6-7 12-7 12S5 16 5 10a7 7 0 0 1 14 0z"/><circle cx="12" cy="10" r="2.5"/>',
  folder:'<path d="M3 7V5a2 2 0 0 1 2-2h5l2 3h7a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
  tag:'<path d="M3 3h8l10 10-8 8L3 11z"/><circle cx="7.5" cy="7.5" r="1"/>',
  star:'<path d="m12 2 3 6 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1z"/>',
  clock:'<circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 2"/>',
  calendar:'<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4M17 3v4M3 10h18M7 14h3M14 14h3M7 17h3"/>',
  layers:'<path d="m12 3 10 6-10 6L2 9zM3 13l9 5 9-5M3 17l9 5 9-5"/>',
  trash:'<path d="M3 6h18M8 6V3h8v3M5 6l1 15h12l1-15M10 10v7M14 10v7"/>',
  restore:'<path d="M3 4v6h6M3 10a9 9 0 1 1 1 8M12 7v5l3 2"/>',
  copy:'<rect x="8" y="8" width="13" height="13" rx="2"/><path d="M16 8V3H3v13h5"/>',
  move:'<path d="M3 4h8l2 3h8v14H3zM7 14h10m-3-3 3 3-3 3"/>',
  edit:'<path d="m16 3 5 5-12 12-6 1 1-6zM13 6l5 5"/>',
  share:'<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m9 10 6-3M9 14l6 3"/>',
  info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7h.01"/>',
  lock:'<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V6a4 4 0 0 1 8 0v4M12 14v3"/>',
  unlock:'<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V6a4 4 0 0 1 7-2M12 14v3"/>',
  eye:'<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/>',
  eyeoff:'<path d="m3 3 18 18M10 5h2c6 0 10 7 10 7a20 20 0 0 1-4 4M6 6a20 20 0 0 0-4 6s4 7 10 7c2 0 3-.5 4-1M10 10a3 3 0 0 0 4 4"/>',
  chart:'<path d="M3 3v18h18M7 17v-5M12 17V7M17 17v-8"/>',
  download:'<path d="M12 3v12m-5-5 5 5 5-5M4 17v4h16v-4"/>',
  upload:'<path d="M12 17V3m-5 5 5-5 5 5M4 17v4h16v-4"/>',
  check:'<path d="m5 12 4 4L20 5"/>',
  scan:'<path d="M8 3H3v5M16 3h5v5M21 16v5h-5M8 21H3v-5M7 9h10M7 13h10M7 17h6"/>',
  leaf:'<path d="M4 20C2 10 9 3 21 3c0 10-3 18-13 16M4 20 16 8"/>',
  globe:'<circle cx="12" cy="12" r="9"/><ellipse cx="12" cy="12" rx="4" ry="9"/><path d="M3 12h18"/>',
  crop:'<path d="M6 3v15h15M3 6h15v15"/>',
  rotate:'<path d="M3 4v6h6M3 10a9 9 0 1 1 1 8"/>',
  flip:'<path d="M12 2v20M3 5v14l6-7zM21 5v14l-6-7z"/>',
  volume:'<path d="M3 9h4l5-5v16l-5-5H3zM16 8a6 6 0 0 1 0 8M19 5a10 10 0 0 1 0 14"/>',
  mute:'<path d="M3 9h4l5-5v16l-5-5H3zM17 9l6 6M17 15l6-6"/>',
  cast:'<path d="M3 9V4h18v15h-8M3 15a6 6 0 0 1 6 6M3 10a11 11 0 0 1 11 11"/><circle cx="3" cy="21" r=".7"/>',
  screen:'<rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4"/>',
  arrow:'<path d="M3 12h18m-6-6 6 6-6 6"/>',
  minus:'<path d="M5 12h14"/>',
  fingerprint:'<path d="M5 9a7 7 0 0 1 14 0v5M2 13V9a10 10 0 0 1 20 0M8 17v-7a4 4 0 0 1 8 0v8M12 10v8M4 16v4M8 21v-2M12 22v-2M16 21h1M20 17v3"/>',
  shield:'<path d="m12 2 9 4v6c0 5-9 10-9 10S3 17 3 12V6zM8 12l3 3 5-6"/>',
  sun:'<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2"/>',
  language:'<path d="M2 5h12M8 2v3M4 8c1 5 6 8 10 9M12 5c-1 6-4 10-9 13M14 21l4-11 4 11M15 18h6"/>',
  accessibility:'<circle cx="12" cy="3" r="2"/><path d="M3 8h18M12 6v7M8 22l4-9 4 9"/>',
  database:'<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 4 16 4 16 0V5M4 12c0 4 16 4 16 0"/>',
  code:'<path d="m8 6-6 6 6 6M16 6l6 6-6 6M14 3l-4 18"/>',
  external:'<path d="M14 3h7v7M21 3 10 14M10 3H3v18h18v-7"/>',
  repeat:'<path d="m17 2 4 4-4 4M21 6H6a3 3 0 0 0-3 3v2M7 22l-4-4 4-4M3 18h15a3 3 0 0 0 3-3v-2"/>',
  camera:'<path d="M3 6h4l2-3h6l2 3h4v15H3z"/><circle cx="12" cy="13" r="4"/>',
  bell:'<path d="M5 17V9a7 7 0 0 1 14 0v8l2 2H3zM10 22h4"/>',
};
function icon(name, cls=''){return `<svg class="icon ${cls}" viewBox="0 0 24 24" aria-hidden="true">${paths[name] || paths.grid}</svg>`;}
const image = (name, alt='', cls='') => `<img src="assets/${name}.jpg" alt="${escapeHtml(alt)}" class="${cls}" loading="lazy" draggable="false">`;
const photos = [
  {id:'lake',title:'山湖之间',date:'2026.11.15',type:'photo',tag:'',size:'4.8 MB'},
  {id:'peaks',title:'群山的轮廓',date:'2026.11.15',type:'photo',tag:'RAW',size:'26.4 MB'},
  {id:'forest',title:'走进森林',date:'2026.11.15',type:'video',tag:'02:34',size:'128 MB'},
  {id:'coast',title:'海风经过',date:'2026.11.15',type:'photo',tag:'',size:'5.1 MB'},
  {id:'road',title:'去往远方',date:'2026.11.15',type:'photo',tag:'动态',size:'7.2 MB'},
  {id:'desert',title:'沙丘与光',date:'2026.11.15',type:'photo',tag:'',size:'5.7 MB'},
  {id:'city',title:'城市晚灯',date:'2026.11.12',type:'video',tag:'00:48',size:'42.8 MB'},
  {id:'river',title:'水岸日常',date:'2026.11.12',type:'photo',tag:'',size:'3.9 MB'},
  {id:'snow',title:'冬日来信',date:'2026.11.12',type:'photo',tag:'HDR',size:'5.4 MB'},
  {id:'fern',title:'绿色呼吸',date:'2026.11.12',type:'photo',tag:'',size:'3.6 MB'},
  {id:'cloud',title:'云与山谷',date:'2026.11.12',type:'photo',tag:'全景',size:'9.2 MB'},
  {id:'valley',title:'星空之下',date:'2026.11.12',type:'photo',tag:'',size:'4.9 MB'},
];
const screenGroups = [
  ['日常浏览',['library','albums','tags','explore','search','map']],
  ['查看与创作',['viewer','video','details','editor','special','slideshow','cast','wallpaper']],
  ['整理与维护',['tools','files','stats','duplicates','scan','results','bin','vault']],
  ['设置与系统',['settings','privacy','about','picker','welcome','states']],
];
const screenInfo = {
  library:['媒体库','内容先行的时间线','让照片回到视觉中心，用紧凑的时间分组与稳定导航承接日常浏览。',['全局入口清晰','搜索、筛选、排序与选择分层，减少工具栏堆叠。','操作随场景变化','浏览态、批量态、回收站态与系统选择态分别表达。'],['search','viewer','results']],
  albums:['相册','每一类影像，都有归属','设备相册、动态相册与相册组沿用同一套卡片语言，保持实体与规则的区别。',['分层组织','置顶、相册组与设备目录独立展示。','创建入口统一','普通相册、动态相册、分组与保险库由同一入口创建。'],['tags','vault','search']],
  tags:['标签与分组','用自己的语言整理','标签支持分组与多级导航，保留置顶、重命名、隐藏和集合浏览操作。',['标签直达内容','点击标签形成筛选图库，不增加新的分类业务。','组织操作收纳','分组、移动和封面等操作放入上下文菜单。'],['albums','search','privacy']],
  explore:['探索','换一个角度，看见影像','把地点、媒体类型和结构化筛选汇合为发现入口，不添加不存在的 AI 识别能力。',['地点先于工具','地图卡片直接进入带照片点位的地图。','高级格式可发现','RAW、全景、动态照片和凭证都保留可见入口。'],['map','special','search']],
  search:['搜索与筛选','从关键词，到精确找到','文字查询与可组合条件共存，条件清晰、可移除，并能保存为动态相册。',['条件可读','日期、相册、类型、评分、位置、元数据和凭证保留独立条件。','复用筛选结果','查看结果、保存动态相册与设为主页形成闭环。'],['library','albums','stats']],
  map:['照片地图','影像，也是走过的地方','地图、聚合点与缩略图联动；切换样式或拒绝百度授权时，其他操作仍有入口。',['区域即筛选','地图区域可以回到对应图库。','隐私在使用时说明','百度授权仅在选择百度样式时出现。'],['explore','library','privacy']],
  viewer:['照片查看器','让画面占据主角','沉浸画布与信息面板分离，底部保留高频动作，高级操作按业务归组。',['连续浏览','底部缩略带帮助切换，详情入口保持固定。','完整动作层级','文件、图像、输出与元数据操作放入独立面板。'],['details','editor','special']],
  video:['视频播放器','观看轻盈，控制完整','播放条在画面下方稳定布局，进阶能力通过一层面板展开。',['专业控制保留','音轨、字幕、倍速、AB 循环、逐帧与截帧均有入口。','状态明确','播放、静音和锁定有明确反馈；能力受真实播放器支持限制。'],['special','cast','settings']],
  details:['媒体详情','读懂一张照片','文件信息、拍摄数据、位置、标签和完整元数据分区，不让字段淹没画面。',['信息可操作','评分、标题、日期和位置可以进入编辑面板。','专业细节完整','保留色板、EXIF/XMP、内容凭证与导出入口。'],['viewer','map','search']],
  editor:['图像编辑','专注现有编辑能力','仅重新编排当前已有的裁剪、比例、旋转和翻转，不引入滤镜或调色。',['画布优先','裁剪范围以九宫格辅助表达。','明确的结束动作','取消与保存固定，外部编辑在查看器中保留。'],['viewer','details']],
  special:['特殊媒体','不同格式，同样被照顾','动态照片、全景、多页、矢量与地理影像按自身特性提供上下文动作。',['格式可辨认','类型徽标与专属动作保留原有媒体能力。','按支持条件出现','RAW、HDR、慢动作等不承诺任何文件都可处理。'],['viewer','video','details']],
  slideshow:['幻灯片与屏保','让回忆慢慢播放','自动播放、间隔、随机、重复和视频策略保留；屏保与幻灯片分别配置。',['沉浸呈现','可暂停、投屏并定位回集合。','配置入口稳定','过渡、动态缩放和填充都在演示设置中。'],['cast','wallpaper','settings']],
  cast:['设备投放','让影像走到更大屏幕','保留查看器级投屏，连接状态与当前媒体同时可见。',['设备状态可读','发现、连接、断开与失败有独立提示。','跟随当前内容','当前媒体变化后可继续投放新条目。'],['viewer','video','slideshow']],
  wallpaper:['设为壁纸','把喜欢的画面留在身边','沿用现有壁纸独立模式，保留系统确认与多页媒体选择。',['先预览再应用','界面表达裁切预览与系统应用入口。','尊重系统差异','实际目标位置由 Android 壁纸面板提供。'],['viewer','special']],
  tools:['工具','整理，也可以井井有条','重复清理、回收站、目录、统计与维护形成明确工具中心。',['清理不是首页负担','高频浏览保持专注，维护能力在这里完整汇合。','状态可追踪','空间、权限和数据维护拥有清晰入口。'],['duplicates','bin','stats']],
  files:['文件夹浏览','沿着真实路径，找到内容','按存储卷与目录浏览媒体，目录进入的是路径筛选图库。',['路径可追溯','面包屑和存储卷切换保留。','边界准确','不虚构通用文件管理器的目录编辑能力。'],['library','stats','privacy']],
  stats:['媒体统计','看见媒体库的全貌','容量、类型、时间和排行榜都能进一步筛选，并支持原有导出能力。',['数据可以继续探索','点击图表进入对应集合。','导出层级完整','区分排行 CSV 与字段可选的媒体 CSV / JSON。'],['library','map','tools']],
  duplicates:['重复媒体','给每一份文件，明确的去留','仅展示内容完全相同的重复文件清理，不引入相似照片评分或智能推荐。',['用户掌握选择','默认首份保留，待清理项始终从空选择开始。','清理前复核','数量、容量、回收站与字节复核都在处理流程中说明。'],['scan','results','bin']],
  scan:['扫描进度','把正在发生的事说明白','准备、读取、核对与完成分阶段呈现，并允许取消。',['范围保持可见','只处理当前授权且可访问的本机媒体。','有退出路径','取消、读取问题、空结果与失败都有后续入口。'],['duplicates','results','states']],
  results:['重复项复核','看清之后，再做决定','每组保留项与待清理勾选分开，底部持续汇总，默认不勾选。',['不能清理保留项','更换保留项会从待清理选择中移除它。','确认后重新验证','移入回收站前复核内容，状态变化时要求重扫。'],['duplicates','bin','privacy']],
  bin:['回收站','把误操作的余地留出来','恢复与永久删除清楚分离，避免与普通图库删除混淆。',['专属操作语义','选择后显示恢复、永久删除，而不是普通文件操作。','保留期限可见','沿用现有 30 天保留规则与清空确认。'],['library','privacy','duplicates']],
  vault:['保险库','私密内容，单独守护','与隐藏规则保持业务区别，保留认证方式和回收站依赖。',['安全边界明确','原型 PIN 仅为交互演示，不提供真实加密或认证。','锁定状态可见','PIN、密码、图案与系统认证保留配置入口。'],['albums','privacy','settings']],
  settings:['设置','复杂能力，清晰归类','保留原有八个设置分类与搜索；导航的默认结构可由用户调整。',['个性化仍然完整','导航、缩略图、查看器、视频、显示和语言各归其位。','系统形态保留','TV、屏保、小组件与工作资料按设备条件提供。'],['privacy','about','tools']],
  privacy:['隐私与权限','每一项访问，都看得见','系统权限、存储授权、隐藏规则、回收站与地图授权统一管理。',['隐藏不等于保险库','隐藏只控制可见条件，不替代认证。','依赖先于开关','关闭回收站需处理现有内容与保险库依赖。'],['vault','bin','map']],
  about:['关于与维护','状态透明，维护有据','更新检查、数据占用、缓存、源码与许可证入口均保留。',['不假装检测在线版本','更新状态是原型演示，不读取 GitHub 实时版本。','产品信息清晰','版本、源码、许可和隐私各自拥有明确入口。'],['settings','tools','states']],
  picker:['系统选择器','被其他应用调用时，同样清晰','区分普通浏览与选择任务，保留单选、多选与筛选条件返回。',['完成动作固定','选中数量与完成按钮始终可见。','能力按模式收敛','系统选择模式不显示编辑或删除业务。'],['library','albums']],
  welcome:['首次使用','先理解，再开始','条款接受、可选权限与媒体访问按顺序表达，不因拒绝媒体权限退出应用。',['请求说明具体','说明访问照片、视频和位置元数据的用途。','可选项保持可选','安装应用访问与错误报告按 flavor 条件提供。'],['states','privacy','library']],
  states:['空态与异常','每个状态，都有下一步','权限受限、空图库、初始化失败和清理异常都给出明确补救入口。',['失败不会静默','展示问题、范围与可执行下一步。','验证边界明确','所有状态来自模拟，未验证 Android 真机行为。'],['welcome','duplicates','about']],
};
const routes = Object.keys(screenInfo);
const state = {
  route:'library', previous:'library', routeStack:[], mediaTab:'all', selected:new Set(), selecting:false,
  albumTab:'device', query:'', filters:[], columns:3, layout:'网格', sort:'拍摄日期', section:'日期',
  photoIndex:0, favourite:true, playing:false, muted:false, locked:false, speed:'1.0×', ab:false,
  rotation:0, flip:false, crop:'自由', pin:'', vaultOpen:false, mapStyle:'OpenFreeMap 明亮', baiduConsent:false, mapOverlay:false,
  dupScope:'全部媒体', scanProgress:0, scanTimer:null, dupKeep:{0:0,1:0}, dupSelected:new Set(), dupCleared:false,
  binItems:new Set(['bin-lake','bin-coast','bin-desert']), binRecords:{}, binSelected:new Set(), pickerSelected:new Set(), pickerMode:'多选',
  toggles:{bin:true, history:true, hidden:true, notification:true, autoplay:true}, stateDemo:'permission', settingsQuery:'',
  settingValues:{theme:'明亮', accent:'森林绿', navigation:'媒体 · 相册 · 探索 · 工具', home:'媒体库', calendar:'公历', units:'公制'},
  mapZoom:1, castConnected:false, slidePlaying:true, slideIndex:0, tags:['旅行','山野'], rating:4, abStage:0,
  searchHistory:['山野','旅行','2026 年 11 月'], hiddenRules:[{label:'屏幕截图相册',active:true},{label:'标签：文档',active:true},{label:'媒体类型：动画',active:false}], welcomeFlavor:'libre',
};
const navItems = [['library','grid','媒体'],['albums','album','相册'],['explore','compass','探索'],['tools','tools','工具']];
const topLevel = new Set(['library','albums','tags','explore','tools']);
const darkRoutes = new Set(['viewer','video','editor','slideshow','wallpaper']);
function nav(){let active=state.route;if(active==='tags')active='albums';return `<nav class="bottom-nav" aria-label="应用主导航">${navItems.map(([route,ico,label])=>`<button data-route="${route}" class="${active===route?'active':''}" ${active===route?'aria-current="page"':''}><span>${icon(ico)}</span>${label}</button>`).join('')}</nav>`;}
function header(title='',subtitle='',more=''){
  if(!title)return `<header class="app-header"><button class="app-brand" data-route="library"><span class="mini-mark">mc</span>MediaCraft</button><div class="top-actions"><button class="icon-btn" data-route="settings" aria-label="设置">${icon('settings','sm')}</button><button class="avatar" data-action="profile" aria-label="设备与工作资料">林</button></div></header>`;
  return `<header class="app-header subpage"><div class="back-title"><button class="icon-btn" data-action="back" aria-label="返回">${icon('back')}</button><div><h2>${title}</h2>${subtitle?`<p>${subtitle}</p>`:''}</div></div><div class="top-actions">${more}</div></header>`;
}
const action = (ico,label,act,extra='')=>`<button class="icon-btn" data-action="${act}" aria-label="${label}" ${extra}>${icon(ico)}</button>`;
const heading = (title,subtitle,right='')=>`<div class="page-heading"><div><h2>${title}</h2><p class="subtle">${subtitle}</p></div>${right}</div>`;
const sectionHead = (title,right='')=>`<div class="section-head"><h3>${title}</h3>${right}</div>`;
const link = (text,route)=>`<button class="text-link" data-route="${route}">${text}</button>`;
function row(ico,title,desc='',attrs='',value=''){return `<button class="row" ${attrs}><span class="row-icon">${icon(ico)}</span><span class="row-text"><strong>${title}</strong>${desc?`<p>${desc}</p>`:''}</span>${value?`<span class="row-value">${value}</span>`:''}${icon('chevron','sm')}</button>`;}
function toggleRow(ico,title,desc,key){return `<button class="row" data-action="toggle" data-key="${key}" role="switch" aria-checked="${!!state.toggles[key]}"><span class="row-icon">${icon(ico)}</span><span class="row-text"><strong>${title}</strong><p>${desc}</p></span><span class="toggle ${state.toggles[key]?'on':''}"></span></button>`;}
function banner(text,type='info'){return `<div class="info-banner ${type==='warning'?'warning':type==='error'?'error':''}">${icon(type==='error'?'close':'info','sm')}<span>${text}</span></div>`;}
function searchBox(placeholder='搜索照片、视频、标签或地点',live=false){return `<${live?'div':'button'} class="search-box" ${live?'':'data-route="search"'}>${icon('search','sm')}${live?`<input id="media-search" value="${escapeHtml(state.query)}" placeholder="${placeholder}" aria-label="媒体搜索">`:`<span>${placeholder}</span><kbd>⌕</kbd>`}</${live?'div':'button'}>`;}
function grid(list=photos,mode='browse'){
  if(state.route==='library'&&mode==='browse'&&state.layout==='列表')return `<div class="media-list">${list.map(p=>`<button class="media-list-row" data-action="photo" data-id="${p.id}" ${state.selecting?`aria-pressed="${state.selected.has(p.id)}"`:''}>${image(p.id,p.title)}<span><strong>${p.title}</strong><small>${p.date} · ${p.size} · ${p.tag||'JPEG'}</small></span>${state.selecting?`<span class="check ${state.selected.has(p.id)?'checked':''}"></span>`:icon('chevron','sm')}</button>`).join('')}</div>`;
  return `<div class="media-grid ${state.columns===4?'cols4':''} ${state.route==='library'&&state.layout==='马赛克'?'mosaic':''}">${list.map(p=>{
    const chosen=mode==='picker'?state.pickerSelected.has(p.id):mode==='bin'?state.binSelected.has(p.id):state.selected.has(p.id);
    const selection=mode==='picker'||mode==='bin'||state.selecting;
    return `<button class="media-tile ${chosen?'selected':''}" data-action="${mode==='picker'?'pick-photo':mode==='bin'?'bin-select':'photo'}" data-id="${p.id}" aria-label="${escapeHtml((selection?'选择 ':'查看 ')+p.title)}" ${selection?`aria-pressed="${chosen}"`:''}>${image(p.asset||p.id,p.title)}${selection?`<span class="tile-select">${chosen?'✓':''}</span>`:p.id==='lake'&&state.favourite?`<span class="tile-heart">${icon('heart','sm')}</span>`:''}${p.tag?`<span class="tile-tag">${p.type==='video'?icon('play','sm'):''}${p.tag}</span>`:''}</button>`;
  }).join('')}</div>`;
}
function visiblePhotos(){
 let list=photos.filter(p=>state.mediaTab==='all'||(state.mediaTab==='photos'?p.type==='photo':p.type==='video'));
 const rules=state.filters.map(f=>p=>f==='收藏'?['lake','forest','road','valley'].includes(p.id):f==='视频'?p.type==='video':f==='照片'?p.type==='photo':f==='RAW'?p.tag==='RAW':true);
 if(rules.length)list=list.filter(p=>state.filterRelation?.includes('任一')?rules.some(test=>test(p)):rules.every(test=>test(p)));
 if(state.query.trim())list=list.filter(p=>(p.title+' IMG_'+p.id+' 旅行 山野').toLowerCase().includes(state.query.toLowerCase()));
 const sorters={'文件名称':(a,b)=>a.title.localeCompare(b.title,'zh-CN'),'路径':(a,b)=>a.id.localeCompare(b.id),'文件大小':(a,b)=>parseFloat(b.size)-parseFloat(a.size),'评分':(a,b)=>(b.id==='lake'?state.rating:3)-(a.id==='lake'?state.rating:3),'视频时长':(a,b)=>(b.type==='video'?1:0)-(a.type==='video'?1:0),'随机':(a,b)=>((a.id.charCodeAt(0)*7)%13)-((b.id.charCodeAt(0)*7)%13)};
 if(sorters[state.sort])list.sort(sorters[state.sort]);if(state.sortReverse)list.reverse();return list;
}
function libraryGroups(list){
 if(!list.length)return `<div class="empty-state"><div class="empty-illustration">${icon('search','lg')}</div><h3>没有匹配的媒体</h3><p>换一个关键词或减少筛选条件。</p><button class="secondary-btn" data-action="clear-filters">清除筛选</button></div>`;
 if(state.section==='不分段')return grid(list);
 const keys=state.section==='月份'?['2026 年 11 月']:state.section==='相册'?['相机','日常片段']:[...new Set(list.map(p=>p.date))];
 return keys.map(key=>{const group=state.section==='月份'?list:state.section==='相册'?list.filter(p=>(photos.indexOf(p)<6?'相机':'日常片段')===key):list.filter(p=>p.date===key);if(!group.length)return '';return `<div class="date-label"><div><h3>${key.replace('2026.11.','11 月 ')+(key.includes('2026.11.')?' 日':'')}</h3><p>当前集合 · ${group.length} 项</p></div><button data-route="stats">2026 ${icon('down','sm')}</button></div>${grid(group)}`;}).join('');
}
function calendarView(){
 const day=state.calendarDay||15;
 return `${header()}<div class="page-scroll">${heading('媒体库','从日期，回到那一天。',`<button class="tiny-btn" data-action="layout">${icon('grid','sm')}日历视图</button>`)}<div class="pad"><div class="calendar-heading"><button data-action="calendar-prev" aria-label="上一月">${icon('back','sm')}</button><h3>2026 年 11 月</h3><button data-action="calendar-next" aria-label="下一月">${icon('chevron','sm')}</button></div><div class="calendar-week">${['一','二','三','四','五','六','日'].map(d=>`<span>${d}</span>`).join('')}</div><div class="calendar-grid">${Array(6).fill('<span></span>').join('')}${Array.from({length:30},(_,n)=>`<button data-action="calendar-day" data-value="${n+1}" class="${[12,15].includes(n+1)?'has-media':''} ${day===n+1?'active':''}">${n+1}${[12,15].includes(n+1)?'<i></i>':''}</button>`).join('')}</div>${sectionHead(`${day} 日的媒体`)}${[12,15].includes(day)?grid(visiblePhotos().filter(p=>p.date.endsWith(String(day)))):'<div class="inline-empty">这一天没有示例媒体。</div>'}</div></div>${nav()}`;
}
function library(){
 const list=visiblePhotos();if(state.layout==='日历')return calendarView();
 const tool=`<button class="tiny-btn" data-action="select-mode">${state.selecting?'取消':'选择'}</button>`;
 return `${state.selecting?header(`已选择 ${state.selected.size} 项`,'点击缩略图选择媒体',`<button class="text-link" data-action="select-all">全选</button>`):header()}<div class="page-scroll">${heading('媒体库','2,048 项影像，属于你的生活。',tool)}<div class="pad">${searchBox()}<div class="segments">${[['all','全部'],['photos','照片'],['videos','视频']].map(([v,t])=>`<button data-action="media-tab" data-value="${v}" class="${state.mediaTab===v?'active':''}">${t}</button>`).join('')}</div><div class="chips nowrap"><button class="chip ${state.filters.includes('收藏')?'active':''}" data-action="filter-favourite">${icon('heart','sm')}收藏 <span>128</span></button><button class="chip" data-action="advanced-filter">${icon('filter','sm')}全部筛选</button>${state.filters.filter(x=>x!=='收藏').map(x=>`<button class="chip active removable" data-action="remove-filter" data-value="${escapeHtml(x)}">${escapeHtml(x)} ×</button>`).join('')}</div></div><div class="library-toolbar" style="margin-top:11px"><button data-action="layout">${state.layout} · ${state.sort}${icon('down','sm')}</button><div><button data-action="columns" aria-label="切换缩略图列数">${icon('grid','sm')}</button><button data-action="collection-more" aria-label="图库更多操作">${icon('more','sm')}</button></div></div>${libraryGroups(list)}${list.length?`<p class="subtle" style="text-align:center;margin:23px">${list.length} 项示例媒体 · 所有示例已载入</p>`:''}</div>${state.selecting?`<div class="selection-toolbar">${[['share','分享','share'],['move','移动','move'],['tag','标签','edit-tags'],['trash','删除','delete-media'],['more','更多','batch-more']].map(([i,t,a])=>`<button data-action="${a}" ${!state.selected.size?'disabled':''}>${icon(i)}${t}</button>`).join('')}</div>`:nav()}`;
}
const albumData=[['lake','山野拾光','128 项 · 动态相册','layers'],['coast','相机','1,386 项','camera'],['city','城市漫步','96 项','album'],['forest','日常片段','248 项','album'],['snow','下载','72 项','download'],['fern','屏幕截图','138 项','image']];
function albums(){return `${header()}<div class="page-scroll">${heading('相册','让喜欢的内容，自然归类。',action('plus','创建相册','create-album'))}<div class="pad">${searchBox('查找相册或目录')}<div class="segments">${[['device','设备相册'],['dynamic','动态相册'],['groups','相册组']].map(([v,t])=>`<button data-action="album-tab" data-value="${v}" class="${state.albumTab===v?'active':''}">${t}</button>`).join('')}</div>${state.albumTab==='groups'?`<div class="rows">${row('folder','旅行手记','3 个相册 · 可继续下钻','data-action="album-group"')}${row('folder','生活记录','2 个相册','data-action="album-group"')}</div>`:`${sectionHead(state.albumTab==='dynamic'?'动态相册':'置顶相册',`<button class="text-link" data-action="album-manage">管理 ${icon('more','sm')}</button>`)}<div class="album-grid">${(state.albumTab==='dynamic'?[albumData[0],['peaks','高分照片','42 项 · 评分 ≥ 4','star'],...(state.dynamicAlbums||[])]:albumData.slice(0,2)).map(albumCard).join('')}</div>${state.albumTab==='device'?`${sectionHead('设备中的相册','<small>4 个相册</small>')}<div class="album-grid">${albumData.slice(2).map(albumCard).join('')}</div>`:''}`}${sectionHead('更多组织方式')}<div class="rows">${row('tag','标签与标签组','用关键词连接不同相册','data-route="tags"')}${row('lock','保险库','已锁定 · 需要认证','data-route="vault"')}${row('eyeoff','隐藏规则','管理不可见的内容','data-action="hidden-rules"')}</div></div></div>${nav()}`;}
function albumCard([id,title,count,ico]){return `<div class="album-card"><button data-action="album-open" data-value="${title}" style="padding:0;text-align:left;width:100%"><div class="album-cover">${image(id,title)}<span class="album-badge">${icon(ico,'sm')}</span></div><h4>${title}</h4><small>${count}</small></button><button class="album-menu" data-action="album-context" data-value="${title}" aria-label="${title}的操作">${icon('more','sm')}</button></div>`;}
function tags(){return `${header()}<div class="page-scroll">${heading('标签','有序，不止一种方式。',action('plus','创建标签组','create-tag-group'))}<div class="pad">${searchBox('搜索标签与分组')}${sectionHead('置顶标签',`<button class="text-link" data-action="tag-context">管理</button>`)}<div class="tag-cloud">${[['旅行','128'],['山野','86'],['日常','248'],['家人','52'],['建筑','96'],['海岸','34']].map(([t,n])=>`<button class="chip ${t==='旅行'?'active':''}" data-action="tag-open" data-value="${t}">${icon('tag','sm')}${t}<span>${n}</span></button>`).join('')}</div>${sectionHead('标签组',link('查看全部','search'))}<div class="rows">${row('folder','生活','家人、日常、美食','data-action="tag-group"')}${row('folder','在路上','旅行、山野、海岸','data-action="tag-group"')}</div>${sectionHead('标签如何工作')}<p class="subtle">一张照片可以拥有多个标签。进入标签后，可继续按日期、评分和地点组合筛选。</p><div class="divider"></div><button class="secondary-btn wide" data-action="tag-context">标签与分组操作</button></div></div>${nav()}`;}
function explore(){return `${header()}<div class="page-scroll">${heading('探索','用不同视角，重访你的世界。')}<div class="pad"><div class="hero-card">${image('road','山间旅程')}<div class="hero-caption"><h3>照片里的足迹</h3><p>3 个国家 · 12 座城市 · 846 项带位置媒体</p><button class="tiny-btn" data-route="map">打开照片地图 ${icon('arrow','sm')}</button></div></div>${sectionHead('按地点浏览',link('全部地点','map'))}<div class="chips"><button class="chip active" data-action="places">${icon('globe','sm')}国家 / 地区</button><button class="chip" data-action="states-list">省 / 州</button><button class="chip" data-action="cities-list">地点</button></div>${sectionHead('媒体类型',link('更多格式','special'))}<div class="type-grid">${[['image','照片','1,764 项','照片'],['video','视频','284 项','视频'],['layers','动态照片','68 项','动态照片'],['compass','全景','24 项','全景'],['camera','RAW','48 项','RAW'],['shield','内容凭证','12 项','内容凭证']].map(([i,t,n,v])=>`<button class="type-card" data-action="type-open" data-value="${v}"><div><span>${t}</span><small>${n}</small></div>${icon(i)}</button>`).join('')}</div>${sectionHead('常用筛选',link('组合筛选','search'))}<div class="rows">${row('calendar','往年今日','回到同一天的不同年份','data-action="add-filter" data-value="往年今日"')}${row('star','高评分','四星及以上的照片','data-action="add-filter" data-value="评分 ≥ 4"')}${row('clock','最近添加','查看新进入媒体库的内容','data-action="add-filter" data-value="最近添加"')}</div></div></div>${nav()}`;}
function search(){return `${header('搜索与筛选','查找当前媒体库',`<button class="text-link" data-action="clear-search">重置</button>`)}<div class="page-scroll"><div class="pad">${searchBox('文件名、标题、描述或路径',true)}${sectionHead('当前筛选',`<button class="text-link" data-action="advanced-filter">添加条件 +</button>`)}<div class="chips">${(state.filters.length?state.filters:['全部媒体']).map(x=>`<button class="chip ${x==='全部媒体'?'':'active'}" data-action="remove-filter" data-value="${escapeHtml(x)}">${escapeHtml(x)}${x==='全部媒体'?'':' ×'}</button>`).join('')}</div>${sectionHead('筛选维度')}<div class="type-grid">${[['calendar','日期与时间','月份、星期、最近添加','日期'],['image','类型与格式','照片、视频、RAW…','格式'],['album','相册与分组','设备、动态、相册组','相册'],['pin','地点','国家、省 / 州、地点','地点'],['tag','标签','标签与标签组','标签'],['star','评分','按星级筛选','评分']].map(([i,t,d,v])=>`<button class="type-card" data-action="filter-category" data-value="${v}"><div><span>${t}</span><small>${d}</small></div>${icon(i,'sm')}</button>`).join('')}</div>${sectionHead('专业条件')}<div class="rows">${row('info','元数据存在 / 缺失','定位、拍摄日期、标题等','data-action="filter-category" data-value="元数据"')}${row('shield','内容凭证','按 C2PA 凭证条件筛选','data-action="filter-category" data-value="内容凭证"')}${row('filter','组合条件','匹配所有 / 匹配任一','data-action="advanced-filter"')}</div>${state.toggles.history?`${sectionHead('最近搜索',`<button class="text-link" data-action="clear-history">清空</button>`)}<div class="chips">${state.searchHistory.map(x=>`<button class="chip" data-action="history-query" data-value="${x}">${icon('clock','sm')}${x}</button>`).join('')}</div>`:''}<div style="height:20px"></div></div></div><div class="action-footer"><button class="primary-btn wide" data-action="apply-search">查看筛选结果 ${icon('arrow','sm')}</button><button class="text-link" style="display:block;text-align:center;margin:12px auto 0" data-action="save-dynamic">保存为动态相册</button></div>`;}
function mapSvg(){return `<svg viewBox="0 0 400 430" preserveAspectRatio="xMidYMid slice" aria-hidden="true"><rect width="400" height="430" fill="#edf0e3"/><g fill="#dbe6cc"><path d="M0 0h104l33 51-40 79L0 109zM256 0h144v101l-67 12-59-48zM0 243l76-39 56 32-39 112L0 321zM263 276l65-43 72 45v152H286l26-73z"/></g><g fill="none" stroke="#fffdf5" stroke-width="13"><path d="m-20 185 440 22M-20 331l440-181M164-20l-40 470M312-20 225 450M-30 57l430 310"/></g><g fill="none" stroke="#d1d9c2" stroke-width="1.1"><path d="m-20 185 440 22M-20 331l440-181M164-20l-40 470M312-20 225 450M-30 57l430 310M15 0l35 430M382 0l-18 430M0 120l400 14M0 380l400-48"/></g><path d="M245-20c-55 80 35 120 0 181s-86 63-62 132 20 83-52 157" fill="none" stroke="#c3d8db" stroke-width="25"/><path d="M245-20c-55 80 35 120 0 181s-86 63-62 132 20 83-52 157" fill="none" stroke="#dae7e4" stroke-width="14"/><g fill="none" stroke="#d8c49a" stroke-width="4"><path d="M0 185h400M0 331l400-164"/></g><path d="m73 124 72 89 140-21 31 96" fill="none" stroke="#6a977d" stroke-width="2" stroke-dasharray="5 5"/></svg>`;}
function mapPage(){const baidu=state.mapStyle.startsWith('百度');return `${header('照片地图','示例区域 · 杭州',action('more','地图操作','map-more'))}<div class="map-map-wrap"><div class="map-canvas">${mapSvg()}<div class="map-top-filter"><button data-action="map-region">此区域 · 128 项 ${icon('down','sm')}</button></div><span class="map-label" style="left:10%;top:17%">西湖区</span><span class="map-label" style="left:66%;top:42%">上城区</span><span class="map-label" style="left:20%;top:72%">滨江区</span><div class="map-controls">${action('layers','切换地图样式','map-styles')}${action('plus','放大地图','map-zoom-in')}${action('minus','缩小地图','map-zoom-out')}${action('pin','当前位置','current-location')}</div>${[['lake',20,28,48],['forest',40,48,16],['city',67,69,64]].map(([p,l,t,n])=>`<button class="map-pin" style="left:${l}%;top:${t}%" data-action="map-pin" data-value="${p}" aria-label="查看聚合 ${n} 项">${image(p,'媒体聚合点')}<span class="pin-count">${n}</span></button>`).join('')}<div class="map-current"></div><span style="position:absolute;bottom:12px;left:14px;font-size:8px;color:#9ca692">${state.mapStyle} · 示意底图 / 缩放 ${state.mapZoom}×</span>${baidu&&!state.baiduConsent?`<div class="map-consent"><div>${icon('shield','lg')}<h3>使用百度地图前</h3><p>百度地图需要按其隐私政策处理相关信息。你可以同意后显示，也可以使用其他地图样式。</p><button class="primary-btn" data-action="baidu-agree">同意并显示地图</button><button class="text-link" data-action="baidu-policy">查看百度地图隐私政策 ↗</button><button class="text-link" data-action="map-free">使用 OpenFreeMap 明亮</button></div></div>`:''}</div><div class="map-bottom"><div style="display:flex;justify-content:space-between;align-items:center"><h3>这里，留下过 128 次记录</h3>${action('filter','地图坐标筛选','map-filter')}</div><p>点击聚合点浏览 · 缩略图打开媒体</p><div class="photo-strip">${photos.slice(0,5).map(p=>`<button data-action="map-photo" data-id="${p.id}" aria-label="查看${p.title}">${image(p.id,p.title)}</button>`).join('')}</div><button class="text-link" style="margin-top:14px" data-action="map-region">在媒体库查看此区域 ${icon('arrow','sm')}</button></div></div>`;}
function currentPhoto(){return photos[state.photoIndex] || photos[0];}
function viewer(){const p=currentPhoto();return `<div class="viewer">${header(p.title,`${p.date} · 10:24`,action('more','当前媒体更多操作','entry-more'))}<div class="viewer-canvas">${image(p.id,p.title)}${p.tag?`<button class="floating-chip" data-route="special">${icon('layers','sm')}${p.tag}</button>`:''}</div><div class="viewer-description"><h3>${p.title}</h3><p>${icon('pin','sm')} 浙江 · 杭州 <span style="float:right">${state.photoIndex+1} / ${photos.length}</span></p></div><div class="viewer-filmstrip">${photos.slice(0,6).map((v,i)=>`<button class="${state.photoIndex===i?'active':''}" data-action="viewer-photo" data-id="${v.id}" aria-label="浏览${v.title}">${image(v.id,v.title)}</button>`).join('')}</div><div class="viewer-actions">${[['heart','收藏','favourite',state.favourite],['edit','编辑','open-editor',false],['share','分享','share',false],['info','详情','open-details',false],['trash','删除','delete-media',false]].map(([i,t,a,on])=>`<button data-action="${a}" class="${on?'active':''}">${icon(i)}${t}</button>`).join('')}</div></div>`;}
function video(){return `<div class="viewer">${header('走进森林','2026.11.15 · 02:34',action(state.locked?'lock':'more',state.locked?'解锁播放器':'视频更多操作',state.locked?'unlock-video':'video-more'))}<div class="viewer-canvas">${image('forest','森林视频封面')}<span class="floating-chip">${icon('video','sm')}4K · 30 fps</span>${!state.playing?`<button class="play-center" data-action="play-video" aria-label="播放视频">${icon('play','lg')}</button>`:''}${state.locked?`<div style="position:absolute;bottom:20px;font-size:11px;color:#e7eee1">${icon('lock','sm')} 控件已锁定 · 点击右上角解锁</div>`:''}</div><div class="viewer-description"><h3>走进森林</h3><p>森林深处的一个午后 <span style="float:right">MP4 · 128 MB</span></p></div>${state.locked?'':`<div class="video-timeline"><input type="range" min="0" max="154" value="42" aria-label="视频演示进度" data-action="video-progress"><div class="video-time"><span id="video-current">00:42</span><span>02:34</span></div></div><div class="video-controls">${action(state.muted?'mute':'volume','切换静音','mute-video')}<button data-action="seek-back" aria-label="后退10秒">↶10</button><button class="main-play" data-action="play-video" aria-label="${state.playing?'暂停':'播放'}">${icon(state.playing?'pause':'play')}</button><button data-action="seek-forward" aria-label="前进10秒">10↷</button>${action('cast','投屏','open-cast')}</div><div class="video-extra"><button data-action="video-speed">${state.speed} 倍速</button><button data-action="video-tracks">音轨 / 字幕</button><button data-action="video-advanced">逐帧与更多</button></div>`}<p style="text-align:center;color:#80927d;font-size:9px;margin:0 0 23px">静态播放交互演示 · 不包含真实视频流</p></div>`;}
function details(){const p=currentPhoto();return `${header('媒体详情','文件、拍摄与元数据',action('more','元数据操作','metadata-more'))}<div class="page-scroll"><div class="pad">${image(p.id,p.title,'details-image')}<div class="detail-title"><h3>${p.title}</h3>${action('edit','编辑标题与描述','edit-description')}</div><p class="subtle" style="margin-top:7px">山与湖之间，慢下来的一个瞬间。</p><div class="rating-stars" aria-label="评分">${[1,2,3,4,5].map(n=>`<button data-action="rating" data-value="${n}" aria-label="评为${n}星">${n<=state.rating?'★':'☆'}</button>`).join('')}</div><div class="chips" style="margin-top:16px"><button class="chip active" data-action="add-filter" data-value="JPEG">JPEG</button><button class="chip" data-action="add-filter" data-value="相机">${icon('album','sm')}相机</button>${state.tags.map(t=>`<button class="chip" data-action="tag-open" data-value="${escapeHtml(t)}"># ${escapeHtml(t)}</button>`).join('')}<button class="chip" data-action="edit-tags">+ 标签</button></div>${sectionHead('基本信息',`<button class="text-link" data-action="edit-date">编辑日期</button>`)}<table class="metadata-table"><tr><td>拍摄时间</td><td>2026 年 11 月 15 日 10:24:32<br><span class="subtle">UTC+08:00</span></td></tr><tr><td>尺寸</td><td>4,032 × 3,024 · 12.2 MP</td></tr><tr><td>大小</td><td>${p.size}</td></tr><tr><td>文件名</td><td>IMG_20261115_102432.jpg</td></tr><tr><td>路径</td><td>/storage/emulated/0/DCIM/Camera/</td></tr></table>${sectionHead('拍摄参数')}<div class="rows">${row('camera','Sony α7 IV','35 mm · ƒ/2.8 · 1/640 s · ISO 100','data-action="metadata-full"')}</div>${sectionHead('拍摄位置',`<button class="text-link" data-action="edit-location">编辑位置</button>`)}<button class="mini-map" style="width:100%;padding:0" data-route="map" aria-label="在地图中查看">${mapSvg()}<span class="map-pin">${image(p.id,p.title)}</span></button><p class="subtle" style="margin-top:9px">中国 · 浙江 · 杭州 <span style="float:right">30.25° N, 120.15° E</span></p>${sectionHead('画面色彩')}<div class="color-palette">${['#60745a','#9fab8c','#ced3a8','#e4e3c2','#3d5548'].map(c=>`<button style="background:${c}" data-toast="示例色值 ${c}" aria-label="色值${c}">${c}</button>`).join('')}</div>${sectionHead('专业信息')}<div class="rows">${row('info','完整元数据','EXIF / XMP / IPTC 与嵌入缩略图','data-action="metadata-full"')}${row('shield','内容凭证','查看 C2PA 来源与凭证状态','data-action="credentials"')}${row('download','导出元数据','导出元数据文本','data-action="export-metadata"')}</div><div style="height:15px"></div></div></div>`;}
function editor(){const p=currentPhoto();return `<div class="viewer">${header('裁剪与旋转','保持画面的本来质感',`<button class="text-link" style="color:#d7e4ce" data-action="save-edit">保存</button>`)}<div class="crop-canvas">${image(p.id,p.title)}<div class="crop-guide" id="crop-guide" style="${state.crop==='1:1'?'inset:75px 18px':state.crop==='16:9'?'inset:100px 8px':''}"></div></div><div class="editor-bottom"><div class="chips">${['自由','原始','1:1','4:3','16:9'].map(x=>`<button class="chip ${state.crop===x?'active':''}" data-action="crop-ratio" data-value="${x}">${x}</button>`).join('')}</div><div class="editor-tools"><button data-action="rotate-edit">${icon('rotate')}旋转 90°</button><button data-action="flip-edit">${icon('flip')}水平翻转</button><button data-action="reset-edit">${icon('restore')}重置</button></div><p style="font-size:10px;color:#94a68e;text-align:center">旋转与翻转为预览演示，保存后不写入文件。</p></div></div>`;}
function special(){return `${header('特殊媒体','不同格式的专属查看方式')}<div class="page-scroll"><div class="pad">${banner('以下为格式状态样例，实际可用动作取决于媒体与播放器能力。')}<div class="special-grid">${[['road','动态照片','播放嵌入视频、分享静态 / 视频','motion'],['cloud','360° 全景','拖拽、陀螺仪、嵌入音频','panorama'],['snow','多页 / 连拍','当前页操作与翻页浏览','multipage'],['valley','SVG 矢量','矢量查看与源码入口','svg'],['river','GeoTIFF','地理定位与地图覆盖层','geotiff'],['peaks','RAW / HDR','保留类型识别与详情','raw']].map(([p,t,d,v])=>`<button class="special-item" data-action="special-view" data-value="${v}">${image(p,t)}<div><strong>${t}</strong><p>${d}</p></div></button>`).join('')}</div>${sectionHead('更多视频类型')}<div class="rows">${row('video','慢动作 / 球面视频','以类型徽标与播放器状态辨识','data-route="video"')}${row('layers','动画图片','按媒体能力展示与播放','data-action="add-filter" data-value="动画"')}</div></div></div>`;}
function slideshow(){return `<div class="viewer">${header('山野拾光','幻灯片 · 128 项',action('settings','幻灯片设置','slideshow-settings'))}<div class="slideshow-stage">${image(photos[state.slideIndex%photos.length].id,'幻灯片示例')}<div class="slide-progress">${[0,1,2,3,4].map(n=>`<span class="${state.slideIndex%5===n?'active':''}"></span>`).join('')}</div><div class="slide-caption"><p>NOVEMBER, 2026</p><h3>走过的地方，<br>留在画面里。</h3></div></div><div class="video-controls"><button data-action="slide-prev" aria-label="上一张">${icon('back')}</button><button class="main-play" data-action="slide-play" aria-label="${state.slidePlaying?'暂停幻灯片':'继续幻灯片'}">${icon(state.slidePlaying?'pause':'play')}</button><button data-action="slide-next" aria-label="下一张">${icon('chevron')}</button>${action('cast','投放幻灯片','open-cast')}</div><div style="display:flex;justify-content:center;gap:20px;padding:3px 0 26px"><button class="text-link" style="color:#a5b59b" data-action="slide-locate">在媒体库中定位</button><button class="text-link" style="color:#a5b59b" data-action="screen-saver">屏保设置</button></div></div>`;}
function cast(){return `${header('设备投放','将当前媒体投放到兼容设备')}<div class="page-scroll"><div class="pad"><div class="empty-illustration" style="margin-top:25px">${icon('cast','lg')}</div><h3 style="text-align:center;font-size:21px;font-weight:500">${state.castConnected?'已连接客厅电视':'把画面带到大屏幕'}</h3><p class="subtle" style="text-align:center;margin:10px 0 26px">设备需处于可发现的本地网络中</p>${sectionHead('可用设备',`<button class="text-link" data-toast="原型演示：已刷新示例设备">重新查找</button>`)}<div class="rows">${row('screen','客厅电视','DLNA · 示例设备',`data-action="cast-connect"`,state.castConnected?'已连接':'连接')}${row('screen','书房显示器','DLNA · 示例设备','data-action="cast-failure"','连接')}</div>${sectionHead('当前内容')}<div class="mini-stat">${image(currentPhoto().id,currentPhoto().title)}<div><strong>${currentPhoto().title}</strong><p>${state.castConnected?'正在投放此媒体 · 模拟状态':'连接后投放当前媒体'}</p></div></div>${banner('设备列表与连接结果均为静态演示；此页面不会搜索本地网络。')}${state.castConnected?`<button class="secondary-btn wide" data-action="cast-disconnect">断开连接</button>`:''}</div></div>`;}
function wallpaper(){return `<div class="viewer">${header('壁纸预览','由系统完成最终应用')}<div class="slideshow-stage">${image(currentPhoto().id,'壁纸预览')}<div style="position:absolute;top:75px;left:0;right:0;text-align:center;z-index:1;text-shadow:0 1px 15px #0004"><div style="font-size:63px;font-weight:200;letter-spacing:-3px">09:41</div><p style="font-size:12px;letter-spacing:2px;margin-top:8px">11 月 17 日 · 星期二</p></div></div><div style="padding:20px 22px 28px"><button class="primary-btn wide" style="background:#d9e2cd;color:#213b33" data-action="apply-wallpaper">使用这张壁纸 ${icon('arrow','sm')}</button><p style="font-size:9px;text-align:center;color:#a4b39b;margin-top:13px">静态预览 · 不会更改系统壁纸</p></div></div>`;}
function tools(){return `${header()}<div class="page-scroll">${heading('工具','让媒体库，保持轻盈有序。')}<div class="pad"><div class="green-card"><div class="icon-art">${icon('copy','lg')}</div><div class="eyebrow" style="color:#8a9d7a;margin-bottom:10px">STORAGE CARE</div><h3>相同的文件，<br>留下一份就好。</h3><p>核对完全重复的照片与视频，<br>确认后移入回收站。</p><button class="primary-btn" data-route="duplicates">重复媒体清理 ${icon('arrow','sm')}</button></div>${sectionHead('浏览与整理')}<div class="rows">${row('folder','文件夹浏览','沿真实存储路径查看媒体','data-route="files"')}${row('chart','媒体统计','数量、容量与内容分布','data-route="stats"')}${row('trash','回收站','恢复或永久删除已移除的项目','data-route="bin"',`${state.binItems.size} 项`)}</div>${sectionHead('个性化与维护')}<div class="rows">${row('settings','设置','外观、播放、导航与语言','data-route="settings"')}${row('shield','隐私与权限','权限、存储访问与隐藏规则','data-route="privacy"')}${row('database','数据占用与缓存','查看空间占用、清理缓存','data-action="storage-usage"')}${row('info','关于 MediaCraft','更新、源码与开源许可证','data-route="about"')}</div><p class="subtle" style="font-size:9px;text-align:center;margin:22px 0 0">个人设备媒体管理 · 无需账号</p></div></div>${nav()}`;}
function files(){return `${header('文件夹浏览','查看真实存储目录',action('more','当前目录操作','folder-more'))}<div class="page-scroll"><div class="pad"><button class="search-box" data-action="volumes">${icon('database','sm')}内部存储<span style="margin-left:auto">${icon('down','sm')}</span></button><div class="file-path" style="margin-top:20px"><button data-action="folder-root">内部存储</button>${icon('chevron','sm')}<b>${state.folder||'DCIM'}</b></div><div class="rows">${row('folder','Camera','1,386 项媒体 · 8.4 GB','data-action="folder-enter" data-value="Camera"')}${row('folder','Screenshots','138 项媒体 · 246 MB','data-action="folder-enter" data-value="Screenshots"')}${row('folder','Video','284 项媒体 · 6.2 GB','data-action="folder-enter" data-value="Video"')}${row('folder','MediaCraft','48 项媒体 · 126 MB','data-action="folder-enter" data-value="MediaCraft"')}</div>${banner('进入目录可查看子目录，或打开该路径下的媒体集合。')}<button class="primary-btn wide" data-action="folder-gallery">查看当前目录媒体 ${icon('arrow','sm')}</button>${sectionHead('当前路径')}<p class="subtle" style="font-size:10px;overflow-wrap:anywhere">/storage/emulated/0/${state.folder||'DCIM'}/</p></div></div>`;}
function stats(){return `${header('媒体统计','当前媒体库 · 示例数据',action('download','导出媒体明细','export-stats'))}<div class="page-scroll"><div class="pad"><div class="stats-overview"><strong>2,048 <span style="font-size:12px;font-weight:400;letter-spacing:0">项媒体</span></strong><p>记录生活，也看见它的分量。</p><div class="stats-metrics"><div><strong>16.8 <span style="font-size:10px">GB</span></strong><small>媒体总容量</small></div><div><strong>12 <span style="font-size:10px">小时</span> 36 <span style="font-size:10px">分</span></strong><small>视频总时长</small></div></div></div>${sectionHead('媒体构成',`<button class="text-link" data-action="mime-stats">查看格式</button>`)}<div class="donut-section"><button class="donut" data-action="add-filter" data-value="照片" aria-label="查看照片"><span>照片 86%</span></button><div class="legend"><div><span><i style="background:#557a5b"></i>照片</span><b>1,764</b></div><div><span><i style="background:#d2be8b"></i>视频</span><b>284</b></div><button class="text-link" data-action="mime-stats">8 种格式 →</button></div></div>${sectionHead('拍摄时间',`<button class="text-link" data-action="chart-period">按月 ${icon('down','sm')}</button>`)}<div class="chart">${[36,63,49,78,58,92,45,71,60,98,79,40].map((n,i)=>`<button style="height:${n}%" class="${i===10?'active':''}" data-action="add-filter" data-value="2026 年 ${i+1} 月" aria-label="查看${i+1}月媒体"><span>${n*3}</span></button>`).join('')}</div><div class="chart-labels">${['1','2','3','4','5','6','7','8','9','10','11','12'].map(m=>`<span>${m}月</span>`).join('')}</div>${sectionHead('内容排行',`<button class="text-link" data-action="rankings">完整排行 →</button>`)}<div class="ranking">${[['01','coast','相机','1,386 项'],['02','forest','日常片段','248 项'],['03','lake','山野拾光','128 项']].map(([n,p,t,v])=>`<button class="ranking-item" data-action="album-open" data-value="${t}"><b>${n}</b>${image(p,t)}${t}<span>${v}</span></button>`).join('')}</div><div class="chips"><button class="chip" data-action="rankings" data-value="国家">国家</button><button class="chip" data-action="rankings" data-value="省 / 州">省 / 州</button><button class="chip" data-action="rankings" data-value="地点">地点</button><button class="chip" data-action="rankings" data-value="标签">标签</button><button class="chip" data-action="rankings" data-value="评分">评分</button></div></div></div>`;}
function duplicates(){return `${header('重复媒体清理','完全相同的内容，清楚地整理')}<div class="page-scroll"><div class="pad"><div class="empty-illustration" style="width:120px;height:120px;margin-top:25px">${icon('copy','lg')}</div><h3 style="font-size:24px;font-weight:500;text-align:center;line-height:1.5">重复的内容，<br>可以少占一点空间。</h3><p class="subtle" style="text-align:center;line-height:1.9;margin:13px 0 25px">核对照片与视频的文件内容，<br>将完全相同的文件分组，交由你确认。</p>${sectionHead('扫描范围')}<div class="segments">${['全部媒体','照片','视频'].map(x=>`<button class="${state.dupScope===x?'active':''}" data-action="scan-scope" data-value="${x}">${x}</button>`).join('')}</div><div class="rows">${row('scan','逐步核对文件内容','只扫描已授权、可访问的本机媒体','data-action="duplicate-help"')}${row('check','每组保留一份','默认首份保留，清理项由你手动勾选','data-action="duplicate-help"')}${row('trash','先移入回收站','处理前再次核对，不直接永久删除','data-route="bin"')}</div>${banner('这里演示完全重复文件的整理流程，不进行相似度推荐。')}</div></div><div class="action-footer"><button class="primary-btn wide" data-action="start-scan">${icon('scan','sm')}开始扫描</button></div>`;}
function scan(){return `${header('正在扫描','范围：'+state.dupScope,`<button class="text-link" data-action="cancel-scan">取消</button>`)}<div class="page-scroll"><div class="pad"><div class="scan-circle" style="--progress:${state.scanProgress}%"><div><strong id="scan-percent">${state.scanProgress}%</strong><span>扫描演示</span></div></div><h3 style="font-size:19px;font-weight:500;text-align:center;margin-bottom:12px" id="scan-title">${state.scanProgress<30?'准备与读取媒体':state.scanProgress<75?'读取文件，寻找候选项':'核对文件内容'}</h3><p class="subtle" style="text-align:center">处理示例 ${Math.round(state.scanProgress*20.48).toLocaleString()} / 2,048 项</p><div style="margin:35px 0">${[['准备扫描',0],['读取文件',25],['核对内容',70],['完成分组',100]].map(([t,p],i)=>`<div class="scan-step ${state.scanProgress>=p?'done':''}"><b>${state.scanProgress>p?'✓':i+1}</b>${t}</div>`).join('')}</div>${banner('这是静态进度演示，不会访问设备文件。你可以随时取消。')}<button class="secondary-btn wide" data-action="skip-scan">直接查看示例结果</button><button class="text-link" style="display:block;margin:18px auto" data-action="scan-errors">查看扫描异常状态</button></div></div>`;}
const duplicateGroups=[{name:'山湖之间',photo:'lake',size:4.8,files:['IMG_20261115_102432.jpg','IMG_20261115_102432_copy.jpg','旅行留存_01.jpg'],paths:['DCIM/Camera','Pictures/Import','Download']},{name:'森林片段',photo:'forest',size:128,files:['VID_20261115_143002.mp4','森林片段_副本.mp4'],paths:['DCIM/Camera','Movies/Export']}];
function dupSize(){return [...state.dupSelected].reduce((sum,key)=>sum+duplicateGroups[Number(key.split('-')[0])].size,0);}
function results(){return `${header('复核重复项','完全重复 · 待清理项需手动勾选',action('more','清理状态与操作','result-more'))}<div class="page-scroll"><div class="pad">${state.dupCleared?`<div class="empty-state"><div class="empty-illustration">${icon('check','lg')}</div><h3>示例整理已完成</h3><p>所选示例已移入模拟回收站。<br>真实业务会在处理后自动重新扫描。</p><button class="primary-btn" data-action="restart-scan">重新演示扫描</button><div style="margin-top:18px">${link('查看回收站','bin')}</div></div>`:`<div class="dup-summary"><div><strong>2 <span style="font-size:11px">组</span></strong><small>内容完全相同</small></div><div><strong>137.6 <span style="font-size:11px">MB</span></strong><small>最多可整理 · 示例</small></div></div>${banner('绿色行是保留项；右侧复选框是待清理项。保留项始终不会被勾选。')}${duplicateGroups.map((g,gi)=>`<section class="duplicate-group"><div class="dup-group-title"><strong>第 ${gi+1} 组 · ${g.files.length} 份</strong><small>每份 ${g.size} MB</small></div>${g.files.map((file,fi)=>{const kept=state.dupKeep[gi]===fi;const key=`${gi}-${fi}`;return `<div class="duplicate-item ${kept?'kept':''}"><button data-action="preview-duplicate" data-value="${g.photo}" aria-label="预览${g.name}" style="padding:0;width:54px;height:54px;flex-shrink:0;border-radius:6px;overflow:hidden">${image(g.photo,g.name)}</button><div><strong>${file}</strong><p>${g.paths[fi]}<br>${g.size} MB</p></div><button class="keep-btn" data-action="keep-duplicate" data-group="${gi}" data-file="${fi}">${kept?'✓ 保留':'保留这份'}</button><label class="checkline"><input class="check" type="checkbox" aria-label="待清理 ${file}" data-action="select-duplicate" data-key="${key}" ${state.dupSelected.has(key)?'checked':''} ${kept?'disabled':''}></label></div>`;}).join('')}</section>`).join('')}<button class="text-link" data-action="reading-issues">${icon('info','sm')} 1 个文件读取问题 · 查看说明</button>`}</div></div>${state.dupCleared?'':`<div class="action-footer"><div class="summary"><span>已选 ${state.dupSelected.size} 项 · 保留 2 份</span><strong>${dupSize().toFixed(1)} MB</strong></div><button class="primary-btn wide" data-action="review-clean" ${!state.dupSelected.size?'disabled':''}>${icon('trash','sm')}移入回收站</button></div>`}`;}
function bin(){const list=[...state.binItems].map(id=>state.binRecords[id]||{...photos.find(p=>p.id===id.replace('bin-','')),id,asset:id.replace('bin-','')});return `${header('回收站','移入后保留 30 天',`<button class="text-link" data-action="empty-bin" ${list.length?'':'disabled'}>清空</button>`)}<div class="page-scroll"><div class="pad">${banner('你可以恢复项目，也可以永久删除。超过保留期限的项目会由现有清理流程处理。')}${sectionHead(`已移除 ${list.length} 项`,`<button class="text-link" data-action="bin-select-all">${state.binSelected.size===list.length&&list.length?'取消全选':'全选'}</button>`)}</div>${list.length?`${grid(list,'bin')}<p class="subtle" style="text-align:center;margin:16px 0">示例项目 · 剩余 27 天</p>`:`<div class="empty-state"><div class="empty-illustration">${icon('trash','lg')}</div><h3>回收站是空的</h3><p>移除的媒体会在这里暂存。</p></div>`}</div>${state.binSelected.size?`<div class="action-footer"><div class="summary"><span>已选 ${state.binSelected.size} 项</span></div><div class="sheet-footer" style="margin-top:0"><button class="secondary-btn" data-action="restore-bin">恢复</button><button class="primary-btn danger" data-action="delete-bin">永久删除</button></div></div>`:`<div class="action-footer"><button class="secondary-btn wide" data-route="tools">返回工具</button></div>`}`;}
function vault(){return `${header('旅行私藏',state.vaultOpen?'已解锁 · 保险库相册':'已锁定 · 保险库相册',action('settings','配置保险库','vault-settings'))}<div class="page-scroll">${state.vaultOpen?`<div class="pad">${banner('原型解锁状态，仅演示认证流程。')}${sectionHead('私藏内容',`<button class="text-link" data-action="lock-vault">立即锁定</button>`)}</div>${grid(photos.slice(7,10))}<div class="pad" style="margin-top:20px"><button class="secondary-btn wide" data-action="vault-settings">配置认证与回收站</button></div>`:`<div class="pad"><div class="empty-illustration" style="margin-top:30px">${icon('lock','lg')}</div><h3 style="font-size:22px;font-weight:500;text-align:center">输入 PIN，打开私藏</h3><p class="subtle" style="text-align:center;margin-top:12px">演示 PIN：任意 4 位数字</p><div class="vault-dots">${[0,1,2,3].map(n=>`<i class="${state.pin.length>n?'filled':''}"></i>`).join('')}</div><div class="numpad">${[1,2,3,4,5,6,7,8,9,'bio',0,'delete'].map(n=>`<button data-action="pin-key" data-value="${n}" aria-label="${n==='bio'?'模拟系统认证':n==='delete'?'退格':n}">${n==='bio'?icon('fingerprint'):n==='delete'?icon('back'):n}</button>`).join('')}</div><div class="lock-methods"><button data-action="vault-password">使用密码</button><button data-action="vault-pattern">图案认证</button></div><p class="subtle" style="font-size:9px;text-align:center;margin-top:25px">保险库是需要认证的特殊相册，<br>与仅控制可见性的隐藏规则分别管理。</p></div>`}</div>`;}
const settingsSections=[
  ['navigation','compass','导航','主页、导航入口与确认方式'],
  ['thumbnails','grid','缩略图','快速操作、叠加层与连拍'],
  ['viewer','image','查看器','操作栏、信息层与幻灯片'],
  ['video','video','视频','播放、控件、音轨与字幕'],
  ['privacy','shield','隐私','权限、隐藏项与回收站'],
  ['accessibility','accessibility','无障碍','手势、动画与操作时限'],
  ['display','sun','显示','主题、色彩与屏幕行为'],
  ['language','language','语言与格式','语言、日历、单位与坐标'],
];
const settingDetails={
 navigation:[['默认主页','当前：媒体库','home-choice'],['底部导航','媒体 · 相册 · 探索 · 工具','nav-editor'],['侧栏与书签','页面、媒体类型和常用相册','bookmark-editor'],['保持屏幕常亮','关闭 / 查看时 / 始终','screen-on'],['双击返回退出','减少误退出','setting-toggle'],['确认对话框','按操作决定是否再次确认','confirm-settings']],
 thumbnails:[['图库快速操作','收藏、分享、标签和文件操作','quick-actions'],['缩略图叠加层','类型、评分、时长、标签等','thumb-overlay'],['连拍识别模式','按文件名与元数据识别','burst-mode']],
 viewer:[['查看器快速操作','调整底部快捷操作','viewer-actions'],['信息叠加层','详情、直方图、迷你地图','viewer-overlay'],['幻灯片','播放间隔、过渡、重复等','slideshow-settings'],['侧边点击翻页','可选的翻页交互','setting-toggle'],['延伸到刘海区域','利用屏幕可用空间','setting-toggle'],['动态照片自动播放','进入查看器后自动播放','setting-toggle'],['图片背景','纯色、棋盘格、动态背景','image-background']],
 video:[['播放行为','自动播放、循环与状态保留','playback-settings'],['播放器控件','按钮、跳转与手势','video-controls'],['字幕主题','字体、颜色、描边与位置','subtitle-settings'],['显示视频','在媒体库中展示视频','setting-toggle'],['视频缩略图','生成方式与解码策略','video-thumbnails']],
 accessibility:[['捏合手势替代操作','提供按钮调整缩放','setting-toggle'],['动画','关闭 / 系统 / 开启','animation-settings'],['操作生效时间','沿用系统或延长交互时限','timeout-settings']],
 display:[['主题','明亮 / 深色 / 跟随系统','theme-choice'],['强调色','森林绿 · 可自定义','accent-choice'],['动态配色','使用系统提供的颜色','setting-toggle'],['模糊效果','按设备性能与偏好决定','setting-toggle'],['最高亮度','浏览媒体时的亮度策略','brightness-settings'],['刷新率','系统默认 / 最高可用','refresh-settings'],['TV 界面','遥控器焦点与完整操作行','tv-preview']],
 language:[['界面语言','简体中文','locale-choice'],['日历','公历 / 波斯历 / 回历','calendar-choice'],['坐标格式','十进制度数','coordinate-choice'],['单位制','公制','units-choice'],['数字格式','跟随语言','number-choice']],
};
function settings(){return `${header('设置','按你的习惯，组织 MediaCraft',action('more','设置数据操作','settings-data'))}<div class="page-scroll"><div class="pad"><div class="search-box">${icon('search','sm')}<input id="settings-search" placeholder="搜索设置项" value="${escapeHtml(state.settingsQuery)}" aria-label="搜索设置"></div><div class="rows" style="margin-top:18px" id="settings-rows">${settingsRows(state.settingsQuery)}</div>${sectionHead('系统入口')}<div class="rows">${row('grid','桌面小组件','形状、内容来源与打开方式','data-action="widget-settings"')}${row('screen','屏保','选择集合与播放策略','data-action="screen-saver"')}${row('upload','设置导入 / 导出','封面、动态相册、收藏与设置','data-action="settings-data"')}</div>${sectionHead('应用')}<div class="rows">${row('info','关于与更新','版本、源码、许可证与隐私','data-route="about"')}</div></div></div>`;}
function settingsRows(query=''){const q=query.trim();if(!q)return settingsSections.map(([v,i,t,d])=>row(i,t,d,v==='privacy'?'data-route="privacy"':`data-action="settings-section" data-value="${v}"`)).join('');const matches=[];for(const [group,ico,title,desc] of settingsSections){if((title+desc).includes(q)){matches.push(row(ico,title,desc,group==='privacy'?'data-route="privacy"':`data-action="settings-section" data-value="${group}"`));}for(const [label,detail,act] of settingDetails[group]||[])if((label+detail).includes(q))matches.push(row(ico,label,title+' · '+detail,`data-action="${act}" data-value="${label}"`));}return matches.join('')||'<div class="inline-empty">没有找到设置项，请尝试其他关键词。</div>';}
function privacy(){return `${header('隐私与权限','访问范围，由你决定')}<div class="page-scroll"><div class="pad">${sectionHead('设备访问')}<div class="rows">${row('shield','系统权限','照片、视频、位置元数据与媒体管理','data-action="permissions"')}${row('folder','存储访问授权','查看和撤销已授权目录','data-action="storage-grants"')}${row('eyeoff','隐藏规则','控制类型、相册、标签等的可见性','data-action="hidden-rules"')}</div>${sectionHead('内容与历史')}<div class="rows">${toggleRow('trash','启用回收站','已移除的媒体保留 30 天','bin')}${toggleRow('clock','记录搜索历史','关闭后会清空已有历史','history')}${row('download','自动导出设置','需先选择保存目录','data-action="auto-export"')}${row('lock','保险库的回收站策略','随对应保险库独立配置','data-action="vault-settings"')}</div>${sectionHead('地图隐私')}<div class="rows">${row('map','百度地图授权',state.baiduConsent?'已同意 · 可撤回':'尚未同意 · 使用百度样式时再请求','data-action="baidu-privacy"')}</div>${banner('地图授权仅影响百度地图使用。系统权限与目录访问仍由 Android 管理。')}</div></div>`;}
function about(){return `${header('关于 MediaCraft','应用信息与维护')}<div class="page-scroll"><div class="pad"><div class="about-brand"><span class="brand-mark">m<span>c</span></span><h2>MediaCraft</h2><p>0.6.3 · 本地源码版本示例</p><p>你的影像，有自己的秩序。</p></div><div class="rows">${row('download','检查更新','GitHub Latest Release · 状态演示','data-action="check-update"')}${row('code','产品与源码','版本信息、项目源码与构建说明','data-action="derivation"')}${row('info','问题反馈','查看设备诊断与报告入口','data-action="bug-report"')}</div>${sectionHead('数据维护')}<div class="rows">${row('database','数据占用','回收站、数据库、缓存与私密内容','data-action="storage-usage"')}${row('restore','清理缓存','不删除媒体文件','data-action="clear-cache"')}</div>${sectionHead('致谢与政策')}<div class="rows">${row('heart','致谢与翻译者','开源贡献者、翻译者和第三方项目','data-action="credits"')}${row('code','开源许可证','原始许可与第三方通知','data-action="licenses"')}${row('shield','隐私政策与使用条款','个人学习、研究和本地测试','data-action="terms"')}</div></div></div>`;}
function picker(){
 const filterMode=state.pickerMode==='选择筛选条件',fixed=state.pickerMode==='固定范围内部选择';
 const selectedFilters=state.pickerFilters||[];
 const list=state.pickerFilter==='照片'?photos.filter(p=>p.type==='photo'):state.pickerFilter==='视频'?photos.filter(p=>p.type==='video'):photos;
 return `${header(filterMode?'选择筛选条件':'选择媒体','调用模式 · '+state.pickerMode,`<button class="text-link" data-action="picker-mode">切换模式</button>`)}<div class="page-scroll"><div class="pad">${filterMode?`${banner('通过相册、标签和地点组合条件，返回筛选引用，不返回媒体文件。')}<div class="chips">${selectedFilters.map(f=>`<button class="chip active" data-action="picker-remove-condition" data-value="${escapeHtml(f)}">${escapeHtml(f)} ×</button>`).join('')||'<span class="subtle">尚未选择条件</span>'}</div><div class="rows" style="margin-top:18px">${['相册','标签','地点','类型'].map(t=>row(t==='相册'?'album':t==='标签'?'tag':t==='地点'?'pin':'image',t,'浏览后加入返回条件',`data-action="picker-condition-type" data-value="${t}"`)).join('')}</div>`:`<div class="chips" style="margin-bottom:15px"><button class="chip active" data-action="picker-filter" ${fixed?'disabled':''}>${state.pickerFilter||'全部媒体'} ${icon('down','sm')}</button>${fixed?'':`<button class="chip" data-action="picker-album">选择相册</button>`}</div>${banner(fixed?'固定范围内部选择：既有条件不可移除，只能在给定媒体中单选。':'仅返回媒体引用，不提供编辑或删除操作。')}`}</div>${filterMode?'':grid(list,'picker')}</div><div class="system-footer"><button class="text-link" data-action="picker-cancel">取消</button><span class="subtle">${filterMode?`已选 ${selectedFilters.length} 个条件`:`已选 ${state.pickerSelected.size} 项`}</span><button class="primary-btn" data-action="${filterMode?'picker-return-filters':'picker-done'}" ${!(filterMode?selectedFilters.length:state.pickerSelected.size)?'disabled':''}>完成</button></div>`;
}
function welcome(){return `<div class="page-scroll">${header()}<div class="welcome-photo">${image('lake','山湖风景')}</div><div class="welcome-copy"><div class="eyebrow">WELCOME TO MEDIACRAFT</div><h2>好好收藏，<br>每一个值得的瞬间。</h2><p>浏览、整理与重访你的照片和视频。<br>媒体来自你的设备，无需创建账号。</p><label class="checkline"><input type="checkbox" class="check" id="accept-terms" ${state.acceptTerms?'checked':''} data-action="accept-terms"><span>我已阅读并接受 <button class="text-link" data-action="terms">使用条款</button> 与 <button class="text-link" data-action="terms">隐私政策</button></span></label><label class="checkline" style="margin-top:0"><input type="checkbox" class="check" data-action="optional-apps"><span>允许读取已安装应用名称（可选）</span></label>${state.welcomeFlavor==='play'?`<label class="checkline" style="margin-top:0"><input type="checkbox" class="check" data-action="optional-report"><span>允许发送错误报告（可选）</span></label>`:''}<button class="primary-btn wide" data-action="welcome-continue" ${state.acceptTerms?'':'disabled'}>开始使用 ${icon('arrow','sm')}</button><p style="font-size:9px;margin-top:14px">接下来可按需授予照片、视频与媒体位置权限。</p></div></div>`;}
const statesData={
 permission:['shield','尚未获得完整媒体访问','你可以授予照片与视频访问权限，<br>或继续浏览已授权的内容。','管理媒体权限','permissions'],
 empty:['image','这里还没有媒体','授权后仍为空时，可检查扫描目录，<br>或重新扫描设备中的媒体。','重新扫描','retry-library'],
 error:['database','媒体库初始化失败','无法完成本地数据初始化。<br>错误不会被忽略，你可以查看详情并导出数据库。','查看错误详情','error-details'],
 none:['copy','没有发现完全重复项','当前授权范围内，没有找到<br>内容完全相同的媒体文件。','返回扫描','scan-home'],
 changed:['restore','文件状态已发生变化','部分媒体已移动、进入回收站或不可访问。<br>请重新扫描后再决定清理。','重新扫描','restart-scan'],
 failed:['info','部分文件未能移入回收站','操作未全部完成。请查看失败项目，<br>检查存储权限后重新扫描。','查看失败项目','clean-failed-details'],
};
function states(){const [ico,title,desc,btn,act]=statesData[state.stateDemo];return `${header('状态设计','每个状态，都有下一步')}<div class="page-scroll"><div class="pad"><div class="chips nowrap" style="margin:5px 0 0">${[['permission','权限受限'],['empty','空图库'],['error','初始化失败'],['none','无重复'],['changed','文件变化'],['failed','清理失败']].map(([v,t])=>`<button class="chip ${state.stateDemo===v?'active':''}" data-action="state-demo" data-value="${v}">${t}</button>`).join('')}</div><div class="empty-state" style="padding-left:0;padding-right:0"><div class="empty-illustration">${icon(ico,'lg')}</div><h3>${title}</h3><p>${desc}</p><button class="primary-btn" data-action="${act}">${btn}</button>${state.stateDemo==='permission'?`<button class="text-link" style="display:block;text-align:center;margin:20px auto" data-route="library">继续浏览已授权内容</button>`:''}${state.stateDemo==='error'?`<div style="margin-top:18px">${link('报告问题','about')}<button class="text-link" style="margin-left:20px" data-action="export-database">导出数据库</button></div>`:''}</div>${banner('此页集中展示设计状态，可通过顶部标签切换。')}</div></div>`;}
const renderers={library,albums,tags,explore,search,map:mapPage,viewer,video,details,editor,special,slideshow,cast,wallpaper,tools,files,stats,duplicates,scan,results,bin,vault,settings,privacy,about,picker,welcome,states};
function render(){
  $('#app').innerHTML=renderers[state.route]();
  $('#phone').classList.toggle('dark',darkRoutes.has(state.route));
  $('#screen-counter').textContent=`${String(routes.indexOf(state.route)+1).padStart(2,'0')} / ${routes.length}`;
  document.title=`${screenInfo[state.route][0]} · MediaCraft 静态原型`;
  $('#screen-list').innerHTML=screenGroups.map(([group,list])=>`<div class="nav-group-title">${group}</div>${list.map(r=>`<button class="screen-link ${r===state.route?'active':''}" data-route="${r}" ${r===state.route?'aria-current="page"':''}><span>${String(routes.indexOf(r)+1).padStart(2,'0')}</span>${screenInfo[r][0]}</button>`).join('')}`).join('');
  const info=screenInfo[state.route];
  $('#page-notes').innerHTML=`<h2>${info[1]}</h2><p>${info[2]}</p><div class="note-count">Design rationale</div>${[0,2].map((n,i)=>`<div class="note-item"><span>0${i+1}</span><div><strong>${info[3][n]}</strong><p>${info[3][n+1]}</p></div></div>`).join('')}<div class="related-links">${info[4].map(r=>`<button data-route="${r}">${screenInfo[r][0]} ↗</button>`).join('')}</div>`;
  if(state.route==='editor')updateCrop();
  if(state.route==='welcome')$('#page-notes').insertAdjacentHTML('beforeend','<div class="note-divider"></div><div class="eyebrow">条件场景预览</div><div class="related-links"><button data-action="welcome-flavor" data-value="libre">libre / baidu</button><button data-action="welcome-flavor" data-value="play">Play 版本</button></div>');
}
function go(route,remember=true){
  if(!routes.includes(route))return;
  if(route!==state.route){if(remember)state.routeStack.push(state.route);state.previous=state.route;if(state.scanTimer){clearInterval(state.scanTimer);state.scanTimer=null;}state.route=route;}
  closeSheet();
  if(location.hash!==`#${route}`)history.pushState({route},'',`#${route}`);
  render();
}
function sheet(title,body,description=''){
  const layer=$('#sheet-layer');layer.hidden=false;
  layer.innerHTML=`<section class="sheet" role="dialog" aria-modal="true" aria-label="${escapeHtml(title)}"><div class="sheet-handle"></div><div class="sheet-title"><h3>${title}</h3><button data-action="close-sheet" aria-label="关闭面板">${icon('close','sm')}</button></div>${description?`<p class="sheet-description">${description}</p>`:''}${body}</section>`;
  state.sheetOpener=document.activeElement;
  const first=layer.querySelector('button,input,select,textarea');if(first)first.focus({preventScroll:true});
}
function closeSheet(){const layer=$('#sheet-layer');layer.hidden=true;layer.innerHTML='';if(state.sheetOpener?.isConnected)state.sheetOpener.focus({preventScroll:true});}
let toastTimer;
function toast(message){$('#toast').hidden=false;$('#toast').textContent=message;clearTimeout(toastTimer);toastTimer=setTimeout(()=>{$('#toast').hidden=true;},3200);}
function choices(title,items,act,desc=''){sheet(title,items.map((item,i)=>{const text=Array.isArray(item)?item[0]:item;const detail=Array.isArray(item)?item[1]:'';return row('check',text,detail,`data-action="${act}" data-value="${escapeHtml(text)}"`);}).join(''),desc);}
function confirm(title,text,act,label='确认',danger=false){sheet(title,`<p class="small-note">${text}</p><div class="sheet-footer"><button class="secondary-btn" data-action="close-sheet">取消</button><button class="primary-btn ${danger?'danger':''}" data-action="${act}">${label}</button></div>`);}
function formSheet(title,fields,act,desc=''){sheet(title,fields.map(([label,type,value,name])=>`<div class="field"><label for="field-${name}">${label}</label>${type==='textarea'?`<textarea id="field-${name}" name="${name}">${escapeHtml(value)}</textarea>`:`<input id="field-${name}" name="${name}" type="${type}" value="${escapeHtml(value)}">`}</div>`).join('')+`<button class="primary-btn wide" data-action="${act}">保存</button>`,desc);}
function updateCrop(){const el=$('.crop-canvas img');if(el)el.style.transform=`rotate(${state.rotation}deg) scaleX(${state.flip?-1:1})`;}
function rerender(){const scroll=$('.page-scroll')?.scrollTop||0;render();const el=$('.page-scroll');if(el)el.scrollTop=scroll;}
function checkboxes(items){return items.map(([label,checked=true])=>`<label class="checkline"><input class="check" type="checkbox" ${checked?'checked':''}>${label}</label>`).join('');}
function startScan(){state.scanProgress=0;state.dupCleared=false;state.dupSelected.clear();state.dupKeep={0:0,1:0};go('scan');state.scanTimer=setInterval(()=>{state.scanProgress=Math.min(100,state.scanProgress+13);if(state.scanProgress===100){clearInterval(state.scanTimer);state.scanTimer=null;go('results');}else if(state.route==='scan')render();},550);}
function openAdvancedFilter(){sheet('组合筛选',`<div class="field"><label>条件关系</label><select id="filter-relation"><option>匹配所有条件（AND）</option><option>匹配任一条件（OR）</option></select></div><div class="field-grid"><div class="field"><label>媒体类型</label><select id="filter-type"><option>不限</option><option>照片</option><option>视频</option><option>RAW</option><option>动态照片</option><option>全景</option><option>动画</option><option>慢动作</option><option>球面视频</option></select></div><div class="field"><label>评分</label><select id="filter-rating"><option>不限</option><option>评分 ≥ 4</option><option>评分 = 5</option><option>未评分</option></select></div></div><div class="field-grid"><div class="field"><label>开始日期</label><input id="filter-start" type="date" value="2026-11-01"></div><div class="field"><label>结束日期</label><input id="filter-end" type="date" value="2026-11-17"></div></div><div class="field"><label>位置与元数据</label><select id="filter-meta"><option>不限</option><option>包含位置信息</option><option>缺少位置信息</option><option>包含拍摄日期</option><option>缺少标题</option><option>含内容凭证</option><option>无内容凭证</option></select></div>${checkboxes([['应用日期范围',false]])}<button class="primary-btn wide" data-action="apply-advanced">应用条件</button>`,'保留结构化组合筛选能力；原型仅对部分示例条件演示筛选。');}
function openCollectionMore(batch=false){const entries=batch?[['heart','收藏 / 取消收藏','batch-favourite'],['copy','复制到相册','copy'],['edit','批量重命名','rename'],['image','格式转换','convert'],['rotate','旋转与翻转','batch-transform'],['calendar','编辑日期','edit-date'],['pin','编辑位置','edit-location'],['edit','标题与描述','edit-description'],['star','评分','edit-rating'],['tag','标签','edit-tags'],['info','移除元数据','remove-metadata'],['download','导出 GPX','export-gpx'],['copy','复制到剪贴板','clipboard']]:[['map','在地图中查看','open-map'],['play','播放幻灯片','open-slideshow'],['chart','查看统计','open-stats'],['layers','保存为动态相册','save-dynamic'],['external','添加桌面快捷方式','shortcut'],['grid','设为主页','set-home'],['restore','重新扫描媒体','rescan']];sheet(batch?'批量操作':'当前媒体集合',entries.map(([i,t,a])=>{const blocked=batch&&['convert','batch-transform'].includes(a)&&photos.some(p=>state.selected.has(p.id)&&p.type==='video');return row(i,t,blocked?'所选内容包含视频，此图像操作不可用':'',`data-action="${a}" ${blocked?'disabled':''}`);}).join(''),batch?`应用于已选 ${state.selected.size} 项。具体操作由真实媒体格式与存储权限决定。`:'操作针对当前筛选结果。');}
function openEntryMore(){sheet('媒体操作',`<div class="sheet-action-grid">${[['copy','复制','copy'],['move','移动','move'],['edit','重命名','rename'],['cast','投屏','open-cast']].map(([i,t,a])=>`<button data-action="${a}"><span>${icon(i)}</span>${t}</button>`).join('')}</div><div class="sheet-group">图像与文件</div>${row('rotate','旋转与翻转','','data-action="batch-transform"')}${row('image','格式转换','','data-action="convert"')}${row('edit','使用外部编辑器','','data-action="external-edit"')}${row('external','打开方式','','data-action="open-with"')}<div class="sheet-group">输出与其他</div>${row('screen','设为壁纸 / 其他用途','','data-action="set-as"')}${row('download','打印','','data-action="print"')}${row('copy','复制到剪贴板','','data-action="clipboard"')}${row('external','添加桌面快捷方式','','data-action="shortcut"')}${row('rotate','旋转屏幕','','data-action="rotate-screen"')}${row('settings','查看器设置','','data-action="viewer-settings"')}`, '示例为可写图片集合。快捷方式要求设备支持；旋转屏幕要求当前锁定旋转。只读、回收站或外部调用模式会隐藏写操作。');}
function openVideoAdvanced(){sheet('视频高级控制',`<div class="sheet-action-grid">${[['back','上一帧','prev-frame'],['chevron','下一帧','next-frame'],['camera','截取当前帧','capture-frame'],['lock','锁定控件','lock-video']].map(([i,t,a])=>`<button data-action="${a}"><span>${icon(i)}</span>${t}</button>`).join('')}</div>${row('repeat','AB 循环',state.ab?'已启用 · 00:42 — 01:12':'选择起点 A 与终点 B','data-action="ab-repeat"')}${row('video','音轨与字幕','选择视频、音频、字幕轨','data-action="video-tracks"')}${row('clock','播放速度',state.speed,'data-action="video-speed"')}${row('external','使用外部播放器','','data-action="external-player"')}${row('settings','播放器设置','','data-action="video-settings"')}`,'截帧、静音、轨道与倍速是否可用，取决于真实控制器能力。');}
function openSettingsSection(key){if(key==='privacy'){go('privacy');return;}const section=settingsSections.find(x=>x[0]===key);if(!section)return;sheet(section[2],(settingDetails[key]||[]).map(([t,d,a])=>row(section[1],t,d,`data-action="${a}" data-value="${t}"`)).join(''));}
function saveDemo(message='原型演示：设置已保存'){closeSheet();toast(message);}
function dispatch(act,d={}){
 switch(act){
  case 'close-sheet':closeSheet();break;
  case 'back':if(state.selecting){state.selecting=false;state.selected.clear();render();}else go(state.routeStack.pop()||'library',false);break;
  case 'profile':sheet('设备与资料',`${row('image','个人资料','当前设备媒体 · 无需登录','data-action="close-sheet"','当前')}${row('shield','切换到工作资料','仅在 Android 支持跨资料且授权时可用','data-action="work-profile"')}${banner('此处是设备资料切换，不是云端账号或登录功能。')}`);break;
  case 'work-profile':choices('工作资料访问',[['申请跨资料访问','实际需要系统提供支持与权限'],['取消','继续浏览个人资料']], 'work-profile-choice');break;
  case 'work-profile-choice':saveDemo('原型演示：系统跨资料流程占位，未调用系统权限。');break;
  case 'media-tab':state.mediaTab=d.value;render();break;
  case 'photo':if(state.selecting){state.selected.has(d.id)?state.selected.delete(d.id):state.selected.add(d.id);rerender();}else{state.photoIndex=photos.findIndex(p=>p.id===d.id);go(currentPhoto().type==='video'?'video':'viewer');}break;
  case 'viewer-photo':case 'map-photo':state.photoIndex=photos.findIndex(p=>p.id===d.id);go(currentPhoto().type==='video'?'video':'viewer');break;
  case 'select-mode':state.selecting=!state.selecting;state.selected.clear();rerender();break;
  case 'select-all':{const visible=visiblePhotos();if(visible.every(p=>state.selected.has(p.id)))state.selected.clear();else visible.forEach(p=>state.selected.add(p.id));}rerender();break;
  case 'filter-favourite':if(state.filters.includes('收藏'))state.filters=state.filters.filter(x=>x!=='收藏');else state.filters.push('收藏');render();break;
  case 'remove-filter':state.filters=state.filters.filter(x=>x!==d.value);rerender();break;
  case 'add-filter':case 'type-open':case 'tag-open':state.filters=[d.value];state.query='';state.mediaTab='all';go('library');break;
  case 'clear-filters':state.filters=[];state.query='';state.mediaTab='all';render();break;
  case 'advanced-filter':openAdvancedFilter();break;
  case 'apply-advanced':{
    const fields=['#filter-type','#filter-rating','#filter-meta'].map(s=>$(s).value).filter(v=>v!=='不限');
    if($('.sheet .check')?.checked)fields.push(`${$('#filter-start').value} — ${$('#filter-end').value}`);
    state.filters=[...new Set([...state.filters,...fields])];state.filterRelation=$('#filter-relation').value;
    closeSheet();if(state.route==='search')render();else go('library');toast('原型演示：已应用筛选条件');break;
  }
  case 'filter-category':{
    const values={日期:['往年今日','最近添加','2026 年 11 月','星期日'],格式:['照片','视频','RAW','HDR','动态照片','全景','动画','慢动作','球面视频'],相册:['相机','山野拾光','旅行手记（相册组）'],地点:['中国','浙江','杭州'],标签:['旅行','山野','日常','在路上（标签组）'],评分:['评分 = 5','评分 ≥ 4','未评分'],元数据:['包含位置信息','缺少位置信息','缺少拍摄日期','缺少标题'],内容凭证:['含内容凭证','无内容凭证']};choices(d.value,values[d.value]||[],'select-filter');break;
  }
  case 'select-filter':if(!state.filters.includes(d.value))state.filters.push(d.value);closeSheet();render();break;
  case 'history-query':state.query=d.value;render();break;
  case 'clear-history':state.searchHistory=[];render();toast('原型演示：历史已清空，后续仍会记录新搜索');break;
  case 'clear-search':state.query='';state.filters=[];render();break;
  case 'apply-search':state.query=$('#media-search')?.value||state.query;if(state.toggles.history&&state.query.trim())state.searchHistory=[state.query,...state.searchHistory.filter(x=>x!==state.query)].slice(0,10);go('library');break;
  case 'columns':state.columns=state.columns===3?4:3;rerender();break;
  case 'layout':sheet('视图与排序',`<div class="field"><label>布局</label><select id="layout-choice">${['网格','马赛克','列表','日历'].map(t=>`<option ${state.layout===t?'selected':''}>${t}</option>`).join('')}</select></div><div class="field"><label>排序</label><select id="sort-choice">${['拍摄日期','文件名称','路径','评分','文件大小','视频时长','随机'].map(t=>`<option ${state.sort===t?'selected':''}>${t}</option>`).join('')}</select></div><div class="field"><label>分段</label><select id="section-choice">${['日期','月份','相册','不分段'].map(t=>`<option ${state.section===t?'selected':''}>${t}</option>`).join('')}</select></div><div class="field"><label>缩略图列数</label><select id="columns-choice"><option ${state.columns===3?'selected':''}>3</option><option ${state.columns===4?'selected':''}>4</option></select></div><label class="checkline"><input id="sort-reverse" class="check" type="checkbox" ${state.sortReverse?'checked':''}>反向排序</label><button class="primary-btn wide" data-action="save-layout">应用视图</button>`);break;
  case 'save-layout':state.layout=$('#layout-choice').value;state.sort=$('#sort-choice').value;state.section=$('#section-choice').value;state.sortReverse=$('#sort-reverse').checked;state.columns=Number($('#columns-choice').value);closeSheet();render();toast('原型演示：已保存视图选项');break;
  case 'collection-more':openCollectionMore();break;
  case 'batch-more':openCollectionMore(true);break;
  case 'batch-favourite':choices('已选媒体的收藏状态',['加入收藏','取消收藏'],'save-choice');break;
  case 'open-map':go('map');break;case 'open-slideshow':go('slideshow');break;case 'open-stats':go('stats');break;
  case 'set-home':confirm('将当前集合设为主页？','保留当前筛选条件，今后从这个集合开始浏览。此原型仅模拟配置。','confirm-home');break;
  case 'confirm-home':state.settingValues.home='当前筛选图库';saveDemo('原型演示：当前集合已设为主页');break;
  case 'shortcut':formSheet('添加桌面快捷方式',[['快捷方式名称','text','山野拾光','shortcut']],'save-shortcut','实际创建需由 Android 确认；地图区域也可通过此方式保存。');break;
  case 'save-shortcut':saveDemo('原型演示：已进入系统快捷方式确认步骤');break;
  case 'rescan':confirm('重新扫描媒体？','重新读取媒体库与元数据，不删除设备内容。','rescan-confirm','开始扫描');break;
  case 'rescan-confirm':saveDemo('原型演示：媒体库扫描请求已提交');break;
  case 'album-tab':state.albumTab=d.value;render();break;
  case 'album-open':state.filters=[d.value];state.query='';go('library');break;
  case 'album-group':choices('旅行手记 · 相册组',[['杭州慢游','实体相册 · 48 项'],['山野拾光','动态相册 · 128 项'],['北方冬日','实体相册 · 36 项']],'album-open','组负责层级组织，不移动其中的媒体文件。');break;
  case 'create-album':sheet('创建与组织',`${row('album','普通相册','在存储中创建实体相册','data-action="new-physical"')}${row('layers','动态相册','保存筛选规则，自动收集符合条件的媒体','data-action="new-dynamic"')}${row('folder','相册组','组织现有相册，不移动媒体','data-action="new-album-group"')}${row('lock','保险库','带认证的特殊实体相册','data-action="new-vault"')}`);break;
  case 'new-physical':formSheet('新建普通相册',[['相册名称','text','','album'],['存储路径','text','Pictures/','path']],'save-album','实际创建前需检查目标目录写入权限。');break;
  case 'save-album':if(!$('#field-album').value.trim()){toast('请填写相册名称');break;}saveDemo('原型演示：相册创建表单已确认');break;
  case 'save-dynamic':formSheet('保存为动态相册',[['相册名称','text','山野拾光','album']],'save-dynamic-confirm',`规则：${escapeHtml(state.filters.join(' + ')||'当前媒体集合')}。动态相册存储规则，不复制媒体文件。`);break;
  case 'save-dynamic-confirm':if(!$('#field-album').value.trim()){toast('请填写动态相册名称');break;}state.dynamicAlbums??=[];state.dynamicAlbums.push(['lake',escapeHtml($('#field-album').value.trim()),escapeHtml(state.filters.join(' + ')||'全部媒体'),'layers']);saveDemo('原型演示：筛选规则已保存为动态相册');break;
  case 'new-album-group':case 'create-tag-group':formSheet(act==='create-tag-group'?'新建标签组':'新建相册组',[['分组名称','text','','group']],'save-group','组是组织容器，可以包含下级分组。');break;
  case 'new-dynamic':sheet('新建动态相册',`<div class="field"><label>相册名称</label><input id="new-dynamic-name" value="新的动态相册"></div><div class="field-grid"><div class="field"><label>媒体类型</label><select id="new-dynamic-type"><option>照片</option><option>视频</option><option>全部媒体</option></select></div><div class="field"><label>评分</label><select id="new-dynamic-rating"><option>不限评分</option><option>评分 ≥ 4</option><option>评分 = 5</option></select></div></div><div class="field"><label>标签条件</label><input id="new-dynamic-tag" placeholder="例如：旅行"></div><button class="primary-btn wide" data-action="save-new-dynamic">创建动态相册</button>`,'从相册页独立创建新规则，不复用其他页面遗留的筛选状态。');break;
  case 'save-new-dynamic':{const name=$('#new-dynamic-name').value.trim();if(!name){toast('请填写相册名称');break;}state.dynamicAlbums??=[];state.dynamicAlbums.push(['lake',escapeHtml(name),`${escapeHtml($('#new-dynamic-type').value)} · ${escapeHtml($('#new-dynamic-rating').value)}`,'layers']);state.albumTab='dynamic';closeSheet();go('albums');toast('原型演示：新规则相册已创建');break;}
  case 'save-group':if(!$('#field-group').value.trim()){toast('请填写分组名称');break;}saveDemo('原型演示：分组已创建');break;
  case 'new-vault':formSheet('创建保险库',[['名称','text','旅行私藏','vault'],['存储目录','text','Pictures/Private','path']],'new-vault-auth','保险库是需要认证的特殊实体相册。下一步配置锁类型。');break;
  case 'new-vault-auth':choices('选择认证方式',['系统生物认证','图案','PIN','密码'],'vault-auth-chosen');break;
  case 'vault-auth-chosen':saveDemo(`原型演示：已选择${d.value}，后续由真实应用设置凭据`);break;
  case 'album-manage':sheet('管理相册',`${row('folder','相册组的组织操作','','data-action="group-context"')}`+`${row('pin','置顶 / 取消置顶','','data-action="album-pin"')}${row('folder','移动到相册组','','data-action="move-to-group"')}${row('eyeoff','隐藏相册','','data-action="hide-item"')}${row('grid','列表与分区方式','重要性、媒体类型、存储卷','data-action="album-sections"')}`);break;
  case 'album-context':state.contextAlbum=d.value||'相机';state.contextAlbumKind=['山野拾光','高分照片',...(state.dynamicAlbums||[]).map(a=>a[1])].includes(state.contextAlbum)?'dynamic':'physical';sheet(state.contextAlbum,`${row('image','设置封面','','data-action="album-cover"')}${row('pin','置顶 / 取消置顶','','data-action="album-pin"')}${row('edit','重命名','','data-action="album-rename"')}${row('folder','移动到组 / 移出组','','data-action="move-to-group"')}${row('eyeoff','隐藏','','data-action="hide-item"')}${row('map','在地图中查看','','data-route="map"')}${row('play','播放幻灯片','','data-route="slideshow"')}${row('chart','统计','','data-route="stats"')}${row('trash',state.contextAlbumKind==='dynamic'?'移除动态相册规则':'删除实体相册',state.contextAlbumKind==='dynamic'?'保留媒体文件':'按当前回收站策略确认','data-action="delete-current-album"')}`);break;
  case 'album-cover':sheet('选择相册封面',`<div class="photo-strip">${photos.slice(0,5).map(p=>`<button data-action="save-cover" data-value="${p.title}" aria-label="使用${p.title}作为封面">${image(p.id,p.title)}</button>`).join('')}</div>`);break;
  case 'save-cover':saveDemo(`原型演示：已选择“${d.value}”作为封面`);break;
  case 'album-pin':saveDemo('原型演示：置顶状态已更新');break;
  case 'delete-current-album':confirm(state.contextAlbumKind==='dynamic'?'移除动态相册？':'删除实体相册？',state.contextAlbumKind==='dynamic'?'只移除这个动态相册的筛选规则，保留全部媒体文件。':'涉及实体目录内媒体，需按现有文件删除流程确认范围、写权限和回收站策略。原型不操作真实文件。','save-delete-demo','确认',state.contextAlbumKind!=='dynamic');break;
  case 'group-context':sheet('相册组操作',`${row('edit','重命名组','','data-action="rename-group"')}${row('folder','移动到上级组 / 移出组','只改变组织关系','data-action="move-to-group"')}${row('folder','解散当前组','保留子相册与全部媒体','data-action="dissolve-group"')}${row('image','设置组封面','','data-action="album-cover"')}`);break;
  case 'rename-group':formSheet('重命名相册组',[['分组名称','text','旅行手记','group']],'save-group');break;
  case 'dissolve-group':confirm('解散相册组？','只移除层级组织关系，保留其中的相册与所有媒体文件。','save-delete-demo','解散');break;
  case 'album-rename':formSheet('重命名相册',[['名称','text',state.contextAlbum||'相机','name']],'save-generic');break;
  case 'move-to-group':choices('移动到组',['旅行手记','生活记录','移出当前组'],'save-choice','调整组织关系，不移动媒体文件。');break;
  case 'album-sections':choices('相册分区',['按重要性','按媒体类型','按存储卷'],'save-choice');break;
  case 'delete-album-kind':choices('按对象类型确认',[['实体相册','涉及目录内媒体，进入媒体删除确认'],['动态相册','只移除筛选规则，保留媒体'],['相册组','只解散组织关系，保留相册和媒体']],'confirm-album-type');break;
  case 'confirm-album-type':confirm(`删除${d.value}？`,d.value==='实体相册'?'实体相册包含媒体文件，需逐项确认删除范围及回收站策略。原型不执行文件操作。':'只移除此对象的组织关系或规则，不删除媒体文件。','save-delete-demo','确认',true);break;
  case 'save-delete-demo':saveDemo('原型演示：删除确认完成，未操作真实文件');break;
  case 'tag-group':choices('在路上 · 标签组',['旅行','山野','海岸'],'tag-open');break;
  case 'tag-context':sheet('标签与分组',`${row('image','设置标签封面','','data-action="album-cover"')}`+`${row('folder','新建标签组','','data-action="create-tag-group"')}${row('pin','置顶标签','','data-action="album-pin"')}${row('edit','重命名标签','','data-action="rename-tag"')}${row('folder','移动到组 / 移出组','','data-action="move-to-group"')}${row('eyeoff','隐藏标签','','data-action="hide-item"')}${row('map','在地图中查看','','data-route="map"')}${row('play','播放幻灯片','','data-route="slideshow"')}${row('chart','查看统计','','data-route="stats"')}`);break;
  case 'rename-tag':formSheet('重命名标签',[['标签名称','text','旅行','tag']],'save-generic');break;
  case 'hide-item':confirm('隐藏这个项目？','隐藏会把对应的筛选条件加入隐藏规则。你仍可在“隐私 > 隐藏规则”中恢复显示。','hide-confirm');break;
  case 'hide-confirm':state.hiddenRules.push({label:state.contextAlbum||'当前筛选条件',active:true});saveDemo('原型演示：已添加隐藏规则');break;
  case 'places':choices('国家 / 地区',[['中国','点击下钻至省 / 州'],['日本','点击下钻至省 / 州'],['韩国','点击下钻至省 / 州']],'country-drill');break;
  case 'country-drill':choices(`${d.value} · 省 / 州`,d.value==='中国'?['浙江','北京','四川']:d.value==='日本'?['东京都','京都府']:['首尔','济州'],'state-drill','可继续按地点查看，或打开此范围的媒体集合。');break;
  case 'states-list':choices('省 / 州',['浙江','北京','四川'],'state-drill');break;
  case 'state-drill':choices(`${d.value} · 地点`,d.value==='浙江'?['杭州','宁波']:['查看此地区媒体','在地图中查看'],'place-result');break;
  case 'cities-list':choices('地点',['杭州','北京','京都'],'place-result');break;
  case 'place-result':if(d.value==='在地图中查看')go('map');else choices(d.value,[['查看图库','形成地点筛选集合'],['在地图中查看','显示对应媒体的地理分布']],'place-result-action');break;
  case 'place-result-action':if(d.value==='在地图中查看')go('map');else{state.filters=['地点：杭州'];go('library');}break;
  case 'map-styles':choices('地图样式',[['OpenFreeMap 明亮','libre / baidu 均可用'],['百度普通','仅 baidu flavor，首次使用需要同意隐私说明'],['百度卫星','仅 baidu flavor，首次使用需要同意隐私说明']],'map-style-choice','原型展示 baidu 版本的样式集合，默认仍为 OpenFreeMap 明亮。');break;
  case 'map-style-choice':state.mapStyle=d.value;closeSheet();render();break;
  case 'map-free':state.mapStyle='OpenFreeMap 明亮';render();break;
  case 'baidu-agree':state.baiduConsent=true;render();toast('原型演示：百度地图授权已同意');break;
  case 'baidu-policy':sheet('百度地图隐私说明',`<p class="small-note">真实使用前应展示并链接百度地图 SDK 隐私政策。此静态原型不加载 SDK，不发送位置信息。</p>${row('external','隐私政策链接占位','实现阶段使用项目现有政策地址','data-action="close-sheet"')}`);break;
  case 'map-zoom-in':state.mapZoom=Math.min(4,state.mapZoom+.5);render();break;
  case 'map-zoom-out':state.mapZoom=Math.max(.5,state.mapZoom-.5);render();break;
  case 'current-location':toast('原型演示：定位到地图中的蓝色示例点，未请求真实定位');break;
  case 'map-pin':state.photoIndex=photos.findIndex(p=>p.id===d.value);sheet('此处的媒体',`<div class="photo-strip">${photos.slice(0,5).map(p=>`<button data-action="map-photo" data-id="${p.id}" aria-label="查看${p.title}">${image(p.id,p.title)}</button>`).join('')}</div><div style="margin-top:15px">${row('grid','查看聚合图库','','data-action="map-region"')}${row('pin','编辑此组位置','','data-action="edit-location"')}${row('close','移除此组位置','','data-action="remove-location"')}</div>`);break;
  case 'map-region':state.filters=['地图区域：杭州'];state.query='';go('library');break;
  case 'map-more':sheet('地图操作',`${row('layers','地图样式','','data-action="map-styles"')}${row('external','在外部地图打开','','data-action="external-map"')}${row('repeat','显示 / 隐藏媒体轨迹','','data-action="map-trails"')}${row('external','保存区域桌面快捷方式','','data-action="shortcut"')}${state.mapOverlay?row('layers','覆盖层与透明度','当前调用方已传入覆盖层','data-action="map-overlay"'):''}${row('filter','按坐标筛选','','data-action="map-filter"')}`);break;
  case 'map-filter':formSheet('按坐标范围筛选',[['纬度范围','text','30.10 — 30.40','lat'],['经度范围','text','120.00 — 120.30','lon']],'map-region');break;
  case 'map-trails':saveDemo('原型演示：媒体轨迹显示状态已切换');break;
  case 'map-overlay':sheet('地图覆盖层',`${banner('仅在调用方传入 GeoTIFF 覆盖层或外部轨迹时显示，普通照片地图不提供导入入口。')}${checkboxes([['显示当前传入的覆盖层']])}<div class="field"><label>覆盖层透明度</label><input type="range" min="0" max="100" value="65" aria-label="覆盖层透明度"></div><button class="primary-btn wide" data-action="save-generic">应用</button>`);break;
  case 'external-map':case 'external-edit':case 'external-player':case 'open-with':choices('使用外部应用',[['打开系统应用选择器','静态原型不启动设备应用'],['取消','留在 MediaCraft']],'external-confirm');break;
  case 'external-confirm':saveDemo('原型演示：系统应用选择流程占位');break;
  case 'favourite':state.favourite=!state.favourite;render();toast(state.favourite?'已加入示例收藏':'已取消示例收藏');break;
  case 'entry-more':openEntryMore();break;case 'open-editor':go('editor');break;case 'open-details':go('details');break;case 'open-cast':go('cast');break;
  case 'share':sheet('分享媒体',`<div class="mini-stat">${image(currentPhoto().id,currentPhoto().title)}<div><strong>${state.selecting?`已选择 ${state.selected.size} 项`:currentPhoto().title}</strong><p>选择分享内容与方式</p></div></div>${row('share','系统分享面板','选择目标应用','data-action="share-system"')}${row('info','分享前查看元数据','如需编辑信息，可返回详情单独处理','data-action="open-details"')}${row('layers','动态照片分享方式','静态图片 / 嵌入视频 / 原文件','data-action="motion-share"')}`);break;
  case 'share-system':saveDemo('原型演示：进入系统分享确认步骤，未发送内容');break;
  case 'motion-share':choices('分享动态照片',['静态图片','嵌入视频','原始文件'],'share-system');break;
  case 'copy':case 'move':choices(act==='copy'?'复制到相册':'移动到相册',['相机','下载','旅行手记'],'file-destination','真实操作前会检查存储权限；文件不可写时不能执行。');break;
  case 'file-destination':confirm('确认文件操作',`目标相册：${escapeHtml(d.value)}。实际操作由已有存储权限与文件处理流程完成。`,'save-file-op');break;
  case 'save-file-op':saveDemo('原型演示：文件操作已确认，未读写真实媒体');break;
  case 'rename':formSheet(state.selecting?'批量重命名':'重命名文件',[['命名模式','text',state.selecting?'旅行_{序号}':'IMG_20261115_102432','name']],'save-file-op','实际命名规则沿用项目，原型展示操作入口。');break;
  case 'convert':sheet('格式与尺寸转换',`<div class="field"><label>目标格式</label><select><option>JPEG</option><option>PNG</option><option>WebP</option></select></div><div class="field-grid"><div class="field"><label>尺寸约束</label><select><option>宽与高</option><option>按宽度</option><option>按高度</option></select></div><div class="field"><label>单位</label><select><option>像素</option><option>百分比</option></select></div></div><div class="field-grid"><div class="field"><label>宽度</label><input type="number" value="1920" min="1"></div><div class="field"><label>高度</label><input type="number" value="1440" min="1"></div></div><div class="field"><label>质量</label><input type="range" min="0" max="100" value="90" aria-label="转换质量"></div>${checkboxes([['保留支持的元数据']])}<button class="primary-btn wide" data-action="save-file-op">确认转换</button>`,'只对已有转换能力支持的媒体开放。元数据与质量按目标格式能力显示。');break;
  case 'batch-transform':choices('旋转与翻转',['向左旋转 90°','向右旋转 90°','水平翻转'],'transform-file','批量或单项图像变换，实际写入前检查权限。');break;
  case 'transform-file':confirm(d.value,'对当前选中的图像应用变换。静态原型不会修改媒体文件。','save-file-op');break;
  case 'clipboard':saveDemo('原型演示：复制到剪贴板入口，未更改系统剪贴板');break;
  case 'set-as':choices('设为',['壁纸','其他系统用途'],'set-as-choice');break;
  case 'set-as-choice':d.value==='壁纸'?go('wallpaper'):saveDemo('原型演示：进入系统“设为”选择面板');break;
  case 'print':sheet('打印预览',`${image(currentPhoto().id,'打印示例','details-image')}<p class="small-note" style="margin:14px 0">应用按媒体宽高比生成打印布局；纸张配置交由系统打印面板。</p><button class="primary-btn wide" data-action="print-confirm">进入系统打印</button>`);break;
  case 'print-confirm':saveDemo('原型演示：系统打印面板占位，未提交打印任务');break;
  case 'rotate-screen':toast('原型演示：旋转屏幕入口，未改变浏览器设备方向');break;
  case 'delete-media':confirm(state.toggles.bin?'移入回收站？':'永久删除媒体？',`当前${state.selecting?`已选 ${state.selected.size} 项`:'媒体'}将${state.toggles.bin?'移入回收站，可在 30 天内恢复':'永久删除，无法从回收站恢复'}。原型只改变示例状态。`,'delete-media-confirm',state.toggles.bin?'移入回收站':'永久删除',!state.toggles.bin);break;
  case 'delete-media-confirm':if(state.toggles.bin){const ids=state.selecting?[...state.selected]:[currentPhoto().id];ids.forEach(id=>state.binItems.add(id));}state.selecting=false;state.selected.clear();closeSheet();go('bin');toast('原型演示：已完成示例删除操作');break;
  case 'metadata-more':sheet('元数据操作',`${row('calendar','编辑日期与时区','','data-action="edit-date"')}${row('pin','编辑位置','','data-action="edit-location"')}${row('edit','编辑标题与描述','','data-action="edit-description"')}${row('star','编辑评分','','data-action="edit-rating"')}${row('tag','编辑标签','','data-action="edit-tags"')}${row('eyeoff','移除元数据','','data-action="remove-metadata"')}${row('download','导出元数据文本','','data-action="export-metadata"')}<div class="sheet-group">按媒体格式出现的专属操作</div>${row('layers','Motion Photo · 嵌入视频 / 转静态','仅动态照片显示','data-action="special-view" data-value="motion"')}${row('map','GeoTIFF · 在地图显示','仅含可解析地理信息时显示','data-action="special-view" data-value="geotiff"')}`);break;
  case 'edit-date':formSheet('编辑日期与时区',[['拍摄日期','date','2026-11-15','date'],['时间','time','10:24','time'],['时区','text','UTC+08:00','timezone']],'save-metadata','可按现有日期来源与批量编辑规则处理。');break;
  case 'edit-location':formSheet('编辑位置',[['纬度','number','30.25','lat'],['经度','number','120.15','lon']],'save-metadata','可从地图选点，也可以输入经纬度。');break;
  case 'remove-location':confirm('移除位置信息？','从当前集合的目标媒体中移除位置元数据。','save-metadata','确认移除');break;
  case 'edit-description':formSheet('标题与描述',[['标题','text',currentPhoto().title,'title'],['描述','textarea','山与湖之间，慢下来的一个瞬间。','description']],'save-metadata');break;
  case 'edit-rating':choices('媒体评分',['未评分','★','★★','★★★','★★★★','★★★★★'],'rating-choice');break;
  case 'rating':state.rating=Number(d.value);rerender();toast('原型演示：评分已更新');break;
  case 'rating-choice':state.rating=d.value==='未评分'?0:d.value.length;closeSheet();rerender();toast('原型演示：评分已更新');break;
  case 'edit-tags':formSheet('编辑标签',[['标签（逗号分隔）','text',state.tags.join('，'),'tags']],'save-tags','保留现有标签编辑与批量应用入口。');break;
  case 'save-tags':state.tags=$('#field-tags').value.split(/[,，]/).map(x=>x.trim()).filter(Boolean);closeSheet();rerender();toast('原型演示：标签已更新');break;
  case 'save-metadata':saveDemo('原型演示：元数据修改已确认，未写入文件');break;
  case 'remove-metadata':sheet('移除元数据',`${checkboxes([['位置信息'],['拍摄设备'],['日期时间',false],['其他元数据',false]])}<button class="primary-btn wide" data-action="save-metadata">确认移除</button>`,'由现有元数据写入能力决定可移除字段；原型不写文件。');break;
  case 'metadata-full':sheet('完整元数据',`<div class="chips"><span class="chip active">EXIF</span><span class="chip">XMP</span><span class="chip">IPTC</span></div><table class="metadata-table"><tr><td>Make</td><td>SONY</td></tr><tr><td>Model</td><td>ILCE-7M4</td></tr><tr><td>LensModel</td><td>FE 35mm F1.8</td></tr><tr><td>ExposureTime</td><td>1/640</td></tr><tr><td>FNumber</td><td>2.8</td></tr><tr><td>ISO</td><td>100</td></tr><tr><td>DateTimeOriginal</td><td>2026:11:15 10:24:32</td></tr></table>${row('image','嵌入缩略图','查看文件中的预览图','data-action="embedded-thumb"')}<button class="secondary-btn wide" data-action="export-metadata">导出元数据文本</button>`,'字段为示例，不是样例照片的真实 EXIF。');break;
  case 'embedded-thumb':sheet('嵌入缩略图',image(currentPhoto().id,'嵌入缩略图示例','details-image'),'按真实文件是否包含嵌入预览显示。');break;
  case 'credentials':sheet('内容凭证 / C2PA',`${banner('此媒体未提供可验证的凭证。此处只演示“无凭证”状态。')}<p class="small-note">存在凭证时，展示来源、声明与验证结果；凭证缺失不代表内容不真实。</p><button class="secondary-btn wide" style="margin-top:20px" data-action="add-filter" data-value="含内容凭证">查看包含凭证的媒体</button>`);break;
  case 'export-metadata':formSheet('导出元数据文本',[['文件名称','text','IMG_20261115_metadata.txt','filename']],'export-demo','与统计 CSV / JSON、设置 JSON 导出分别处理。');break;
  case 'export-gpx':formSheet('导出 GPS 轨迹',[['文件名称','text','media-track.gpx','filename']],'export-demo','将符合条件媒体的坐标与时间导出为 GPX；无位置媒体不参与。');break;
  case 'export-demo':saveDemo('原型演示：导出配置已确认，未生成真实媒体数据文件');break;
  case 'rotate-edit':state.rotation=(state.rotation+90)%360;updateCrop();break;
  case 'flip-edit':state.flip=!state.flip;updateCrop();break;
  case 'reset-edit':state.rotation=0;state.flip=false;state.crop='自由';render();break;
  case 'crop-ratio':state.crop=d.value;render();break;
  case 'save-edit':confirm('保存图像修改？','按当前裁剪、旋转与翻转配置保存。原型仅结束编辑预览，不写入文件。','save-edit-confirm','保存');break;
  case 'save-edit-confirm':closeSheet();go('viewer');toast('原型演示：编辑配置已确认');break;
  case 'play-video':state.playing=!state.playing;render();break;
  case 'mute-video':state.muted=!state.muted;render();break;
  case 'seek-back':case 'seek-forward':toast(act==='seek-back'?'原型演示：后退 10 秒':'原型演示：前进 10 秒');break;
  case 'video-more':case 'video-advanced':openVideoAdvanced();break;
  case 'video-speed':choices('播放速度',['0.25×','0.5×','0.75×','1.0×','1.25×','1.5×','2.0×'],'set-speed');break;
  case 'set-speed':state.speed=d.value;closeSheet();render();break;
  case 'video-tracks':sheet('轨道与字幕',`<div class="field"><label>视频轨</label><select><option>视频 1 · H.264 · 3840 × 2160</option></select></div><div class="field"><label>音频轨</label><select><option>音频 1 · AAC · 立体声</option><option>关闭音频</option></select></div><div class="field"><label>字幕</label><select><option>关闭字幕</option><option>中文简体 · 内嵌轨</option><option>English · 内嵌轨</option></select></div><button class="primary-btn wide" data-action="save-generic">应用</button>`,'真实轨道按文件与播放器支持显示。');break;
  case 'prev-frame':case 'next-frame':saveDemo(`原型演示：${act==='prev-frame'?'上一帧':'下一帧'}操作`);break;
  case 'capture-frame':confirm('截取当前帧？','已有业务会保存当前帧到专用相册，并继承支持的时间、位置和旋转信息。','save-file-op','截取');break;
  case 'lock-video':state.locked=true;closeSheet();render();break;
  case 'unlock-video':state.locked=false;render();break;
  case 'ab-repeat':sheet('AB 循环',`${banner('以当前播放位置依次设置 A 点和 B 点，不输入时间。')}<div class="rows">${row('repeat',!state.ab?'启用 AB 循环':state.abStage===0?'设为起点 A':state.abStage===1?'设为终点 B':'重置 A / B 边界',!state.ab?'进入边界设置':state.abStage===0?'当前示例播放位置 00:42':state.abStage===1?'起点 A：00:42 · 当前示例位置 01:12':'循环片段：00:42 — 01:12','data-action="ab-step"')}</div>${state.ab?'<button class="secondary-btn wide" style="margin-top:18px" data-action="ab-disable">停止循环</button>':''}`);break;
  case 'ab-step':if(!state.ab){state.ab=true;state.abStage=0;}else state.abStage=(state.abStage+1)%3;dispatch('ab-repeat');break;
  case 'ab-disable':state.ab=false;state.abStage=0;saveDemo('原型演示：AB 循环已停止');break;
  case 'special-view':{
    const special={motion:['动态照片',`${row('play','查看嵌入视频','','data-route="video"')}${row('share','分别分享静态图或视频','','data-action="motion-share"')}${row('image','转换为静态图片','转换前需要确认','data-action="motion-still"')}`],panorama:['360° 全景',`${image('cloud','全景预览','details-image')}${row('compass','拖拽 / 陀螺仪','按当前设备支持切换','data-action="panorama-controls"')}${row('volume','嵌入音频','存在 XMP 音频时可静音','data-action="panorama-audio"')}`],multipage:['多页媒体',`<div class="photo-strip">${photos.slice(7,11).map((p,i)=>`<button data-action="multipage-select" data-id="${p.id}" data-value="${i+1}" aria-label="第${i+1}页">${image(p.id,`第${i+1}页`)}</button>`).join('')}</div>${banner('操作作用于当前页，页码与翻页入口持续显示。')}${row('more','当前页操作','','data-action="entry-more"')}`],svg:['SVG 矢量',`<div style="text-align:center;padding:20px;font-size:70px;color:#91a482">✳</div>${row('code','查看 SVG 源码','','data-action="svg-source"')}`],geotiff:['GeoTIFF',`${image('river','地理影像示例','details-image')}${row('map','在地图中叠加显示','需可解析地理信息且存在集合','data-action="geotiff-map"')}`],raw:['RAW / HDR',`${image('peaks','RAW 媒体示例','details-image')}${banner('格式徽标保留识别；实际解码与显示能力依文件、系统和插件决定。')}${row('info','查看媒体详情','','data-route="details"')}`]};sheet(...special[d.value]);break;
  }
  case 'motion-still':confirm('转换为静态图片？','此操作会移除动态照片中的视频部分。请在真实文件操作前确认。','save-file-op','确认转换');break;
  case 'panorama-controls':choices('全景控制',['拖拽浏览','启用陀螺仪','停用陀螺仪'],'save-choice');break;
  case 'panorama-audio':saveDemo('原型演示：嵌入音频静音状态已切换');break;
  case 'multipage-select':state.photoIndex=photos.findIndex(p=>p.id===d.id);go('viewer');toast(`原型演示：已定位多页媒体第 ${d.value} 页`);break;
  case 'svg-source':sheet('SVG 源码',`<pre style="font-size:10px;line-height:1.8;white-space:pre-wrap;color:#6b8263">&lt;svg viewBox="0 0 100 100"&gt;\n  &lt;path fill="#91a482" d="..." /&gt;\n&lt;/svg&gt;</pre>`,'静态格式示例；真实源码来自媒体文件。');break;
  case 'geotiff-map':state.mapOverlay=true;go('map');dispatch('map-overlay');break;
  case 'slide-prev':state.slideIndex=(state.slideIndex+photos.length-1)%photos.length;render();break;
  case 'slide-next':state.slideIndex++;render();break;
  case 'slide-play':state.slidePlaying=!state.slidePlaying;render();break;
  case 'slide-locate':state.filters=['山野拾光'];go('library');toast('原型演示：已定位当前媒体所属集合');break;
  case 'slideshow-settings':sheet('幻灯片设置',`<div class="field-grid"><div class="field"><label>间隔</label><select><option>5 秒</option><option>3 秒</option><option>10 秒</option></select></div><div class="field"><label>过渡</label><select><option>淡入淡出</option><option>无过渡</option><option>滑动</option></select></div></div><div class="field"><label>画面填充</label><select><option>适应画面</option><option>填满屏幕</option></select></div>${checkboxes([['循环播放'],['随机顺序',false],['动态缩放'],['跳过视频',false]])}<button class="primary-btn wide" data-action="save-generic">保存</button>`);break;
  case 'cast-connect':state.castConnected=true;render();toast('原型演示：已连接示例设备');break;
  case 'cast-disconnect':state.castConnected=false;render();break;
  case 'cast-failure':sheet('连接未完成',`${banner('无法连接示例设备，请确认网络与设备可发现状态。','error')}<button class="primary-btn wide" data-action="close-sheet">返回设备列表</button>`);break;
  case 'apply-wallpaper':choices('由系统应用壁纸',[['继续到 Android 壁纸确认','实际支持的应用位置由系统提供'],['取消','保留预览']],'wallpaper-confirm');break;
  case 'wallpaper-confirm':saveDemo('原型演示：系统壁纸确认占位，未更改壁纸');break;
  case 'calendar-day':state.calendarDay=Number(d.value);render();break;
  case 'calendar-prev':case 'calendar-next':toast('原型演示：本稿展示 2026 年 11 月日历布局');break;
  case 'volumes':choices('存储卷',[['内部存储','/storage/emulated/0'],['SD 卡（示例）','仅系统检测到时显示']],'volume-choice');break;
  case 'volume-choice':saveDemo('原型演示：已选择 '+d.value);break;
  case 'folder-enter':state.folder=(state.folder||'DCIM')+'/'+d.value;render();break;
  case 'folder-root':state.folder='DCIM';render();break;
  case 'folder-gallery':state.filters=['路径：/'+(state.folder||'DCIM')];state.query='';go('library');break;
  case 'folder-more':sheet('当前目录',`${row('external','添加桌面快捷方式','','data-action="shortcut"')}${row('grid','设为主页','','data-action="set-home"')}${row('eyeoff','隐藏目录','','data-action="hide-item"')}${row('chart','查看统计','','data-route="stats"')}`,'目录浏览沿用现有四项动作；媒体复制、移动等在图库选中后执行。');break;
  case 'export-stats':sheet('导出媒体明细',`<div class="field"><label>文件格式</label><select><option>CSV</option><option>JSON</option></select></div><div class="sheet-group">选择字段</div>${checkboxes([['文件名与路径'],['拍摄日期'],['类型与大小'],['尺寸与时长'],['位置',false],['评分与标签',false]])}<button class="primary-btn wide" data-action="export-demo">确认导出</button>`,'媒体明细支持 CSV / JSON；排行使用独立 CSV 导出。');break;
  case 'rankings':{const dimension=d.value||'相册';const items=dimension==='国家'?[['中国','1,386'],['日本','248'],['韩国','96']]:dimension==='省 / 州'?[['浙江','386'],['北京','248'],['四川','96']]:dimension==='地点'?[['杭州','386'],['北京','248'],['京都','96']]:dimension==='标签'?[['旅行','128'],['山野','86'],['日常','248']]:dimension==='评分'?[['5 星','128'],['4 星','386'],['未评分','248']]:[['相机','1,386'],['日常片段','248'],['山野拾光','128']];sheet(dimension+'完整排行',`${items.map(([t,v],n)=>row('chart',`${n+1}. ${t}`,`${v} 项`,`data-action="ranking-filter" data-dimension="${dimension}" data-value="${t}"`)).join('')}<button class="secondary-btn wide" style="margin-top:18px" data-action="export-ranking">导出排行 CSV</button>`,'点击项目按当前排行维度进入对应筛选集合。');break;}
  case 'ranking-filter':state.filters=[d.dimension+'：'+d.value];state.query='';go('library');break;
  case 'export-ranking':formSheet('导出排行 CSV',[['文件名称','text','media-ranking.csv','filename']],'export-demo');break;
  case 'mime-stats':choices('媒体格式分布',[['JPEG','1,240 项'],['HEIF / HEIC','356 项'],['RAW','48 项'],['MP4','268 项'],['其他','136 项']],'add-filter');break;
  case 'chart-period':choices('时间分布粒度',['按日','按月','按年'],'chart-period-choice');break;
  case 'chart-period-choice':saveDemo(`原型演示：已选择${d.value}统计`);break;
  case 'scan-scope':state.dupScope=d.value;render();break;
  case 'start-scan':case 'restart-scan':startScan();break;
  case 'skip-scan':go('results');break;
  case 'cancel-scan':confirm('取消扫描？','已经计算的中间结果不会自动用于清理。你可以稍后重新开始。','cancel-scan-confirm','取消扫描');break;
  case 'cancel-scan-confirm':go('duplicates');toast('原型演示：扫描已取消');break;
  case 'scan-home':go('duplicates');break;
  case 'duplicate-help':sheet('完全重复媒体的处理规则',`<p class="small-note">1. 只扫描已授权、可访问、未在回收站中的本机媒体。<br><br>2. 每组默认首项保留；可以更换保留项，待清理项必须逐个勾选。<br><br>3. 确认处理前，再次核对文件可访问性与字节内容。<br><br>4. 只移入回收站；未启用回收站时会阻止清理。<br><br>5. 文件发生变化时需要重扫；清理成功后自动重新扫描。</p>`);break;
  case 'keep-duplicate':state.dupKeep[d.group]=Number(d.file);state.dupSelected.delete(`${d.group}-${d.file}`);rerender();break;
  case 'preview-duplicate':sheet('重复文件预览',`${image(d.value,'重复文件预览','details-image')}<p class="small-note" style="margin-top:14px">该组成员内容完全相同。路径和文件名在复核列表中分别展示。</p>`);break;
  case 'review-clean':if(!state.toggles.bin){sheet('需要先启用回收站',`${banner('重复清理只能移入回收站。请先在隐私设置中启用回收站。','warning')}<button class="primary-btn wide" data-route="privacy">打开隐私设置</button>`);break;}confirm('将所选重复项移入回收站？',`待处理 ${state.dupSelected.size} 项，共 ${dupSize().toFixed(1)} MB。每组保留项不会被移除。继续后会再次做字节级复核；此原型只模拟步骤。`,'verify-clean','继续复核');break;
  case 'verify-clean':sheet('最终复核',`${banner('真实流程会重新读取并核对文件字节，防止扫描后文件变化。')}<div class="rows">${row('check','保留项已排除','2 组各保留 1 份','data-action="close-sheet"')}${row('trash',`${state.dupSelected.size} 项 · ${dupSize().toFixed(1)} MB`,'目标：回收站','data-action="close-sheet"')}</div><button class="primary-btn wide" style="margin-top:18px" data-action="confirm-clean">模拟复核成功并移入</button><button class="text-link" style="display:block;margin:15px auto 0" data-action="changed-files">模拟检测到文件变化</button><button class="text-link" style="display:block;margin:12px auto 0" data-action="verify-failed">模拟字节复核失败</button>`);break;
  case 'confirm-clean':for(const key of state.dupSelected){const [g,f]=key.split('-').map(Number),group=duplicateGroups[g],id='duplicate-'+key;state.binRecords[id]={id,asset:group.photo,title:group.files[f],path:group.paths[f],size:group.size+' MB',date:'2026.11.17',type:group.photo==='forest'?'video':'photo',tag:''};state.binItems.add(id);}state.dupCleared=true;state.dupSelected.clear();closeSheet();render();toast('原型演示：已移入模拟回收站，实际流程随后自动重扫');break;
  case 'changed-files':state.stateDemo='changed';go('states');break;
  case 'verify-failed':sheet('字节复核未通过',`${banner('文件内容与扫描时不一致，尚未移动任何文件。','error')}<p class="small-note">请重新扫描，确认文件内容和状态后再处理。此状态不同于移动阶段的部分失败。</p><button class="primary-btn wide" style="margin-top:18px" data-action="restart-scan">重新扫描</button>`);break;
  case 'reading-issues':sheet('文件读取问题',`${banner('1 个文件未参与完整核对，不能据此认定它与其他文件重复。','warning')}<table class="metadata-table"><tr><td>文件</td><td>VID_20261112_083000.mp4</td></tr><tr><td>问题</td><td>读取权限不可用 / 文件已移动（示例）</td></tr></table><button class="primary-btn wide" data-action="permissions">检查存储权限</button>`);break;
  case 'scan-errors':case 'result-more':sheet('扫描与处理状态',`${row('restore','重新扫描','','data-action="restart-scan"')}${row('info','查看文件读取问题','','data-action="reading-issues"')}${row('copy','无重复项状态','','data-action="duplicate-empty"')}${row('restore','扫描后文件变化','','data-action="changed-files"')}${row('close','清理失败状态','','data-action="duplicate-failed"')}${row('shield','关闭回收站时的限制','','data-action="bin-disabled-demo"')}`);break;
  case 'duplicate-empty':state.stateDemo='none';go('states');break;
  case 'duplicate-failed':state.stateDemo='failed';go('states');break;
  case 'bin-disabled-demo':sheet('回收站未启用',`${banner('请先启用回收站，重复清理不会直接永久删除文件。','warning')}<button class="primary-btn wide" data-route="privacy">去设置</button>`);break;
  case 'clean-failed-details':sheet('未完成的项目',`${banner('部分文件处理失败，不能宣称全部成功。','error')}<p class="small-note">示例：Download/旅行留存_01.jpg<br>原因：存储写入权限被拒绝。</p><div class="sheet-footer"><button class="secondary-btn" data-action="permissions">检查权限</button><button class="primary-btn" data-action="restart-scan">重新扫描</button></div>`);break;
  case 'bin-select':state.binSelected.has(d.id)?state.binSelected.delete(d.id):state.binSelected.add(d.id);rerender();break;
  case 'bin-select-all':if(state.binSelected.size===state.binItems.size)state.binSelected.clear();else state.binItems.forEach(id=>state.binSelected.add(id));rerender();break;
  case 'restore-bin':for(const id of state.binSelected)state.binItems.delete(id);state.binSelected.clear();render();toast('原型演示：所选项目已恢复');break;
  case 'delete-bin':confirm('永久删除所选项目？',`将永久删除 ${state.binSelected.size} 项，无法从回收站恢复。此原型只移除示例列表项。`,'delete-bin-confirm','永久删除',true);break;
  case 'delete-bin-confirm':for(const id of state.binSelected)state.binItems.delete(id);state.binSelected.clear();closeSheet();render();toast('原型演示：所选项目已从模拟列表移除');break;
  case 'empty-bin':confirm('清空回收站？',`将永久删除回收站内 ${state.binItems.size} 个示例项目。此操作在真实应用中不可撤销。`,'empty-bin-confirm','永久清空',true);break;
  case 'empty-bin-confirm':state.binItems.clear();state.binSelected.clear();closeSheet();render();toast('原型演示：回收站已清空');break;
  case 'pin-key':if(d.value==='delete')state.pin=state.pin.slice(0,-1);else if(d.value==='bio'){dispatch('vault-bio');break;}else state.pin+=d.value;if(state.pin.length===4){state.pin='';state.vaultOpen=true;render();toast('原型演示：保险库已解锁');}else render();break;
  case 'vault-bio':confirm('模拟系统生物认证','真实应用将调用 Android 认证面板；此原型不读取生物信息。','vault-unlock','模拟认证成功');break;
  case 'vault-password':formSheet('密码认证',[['输入密码','password','','password']],'vault-password-submit','任意非空密码仅用于本次原型交互。');break;
  case 'vault-password-submit':if(!$('#field-password').value){toast('请输入演示密码');break;}dispatch('vault-unlock');break;
  case 'vault-pattern':sheet('图案认证',`<div class="pattern-grid">${[1,2,3,4,5,6,7,8,9].map(n=>`<button data-action="pattern-dot" data-value="${n}" aria-label="图案点${n}">●</button>`).join('')}</div><p class="small-note">点击任意三个点演示图案输入，不记录真实凭据。</p><button class="primary-btn wide" style="margin-top:16px" data-action="vault-unlock">模拟认证成功</button>`);break;
  case 'pattern-dot':toast(`原型图案输入：点 ${d.value}`);break;
  case 'vault-unlock':state.vaultOpen=true;state.pin='';closeSheet();go('vault');toast('原型演示：认证通过');break;
  case 'lock-vault':state.vaultOpen=false;render();break;
  case 'vault-settings':sheet('配置保险库',`${row('lock','认证方式','系统认证、图案、PIN、密码','data-action="new-vault-auth"')}${row('trash','使用回收站','此策略由对应保险库配置','data-action="vault-bin"')}${row('lock','立即锁定','','data-action="vault-lock-now"')}${banner('启用了回收站的保险库会阻止关闭全局回收站。')}`);break;
  case 'vault-bin':choices('保险库回收站策略',['使用回收站','不使用回收站'],'save-choice','真实应用按保险库分别存储；不使用时删除应明确确认。');break;
  case 'vault-lock-now':state.vaultOpen=false;state.pin='';closeSheet();go('vault');break;
  case 'settings-section':openSettingsSection(d.value);break;
  case 'settings-data':sheet('设置数据',`${row('download','导出 JSON','选择封面、动态相册、收藏、设置','data-action="export-settings"')}${row('upload','导入 JSON','从系统文件选择器读取','data-action="import-settings"')}${row('download','自动导出','先选择保存目录','data-action="auto-export"')}`);break;
  case 'export-settings':sheet('导出设置 JSON',`${checkboxes([['相册封面'],['动态相册'],['收藏'],['应用设置']])}<button class="primary-btn wide" style="margin-top:18px" data-action="export-demo">导出所选内容</button>`);break;
  case 'import-settings':sheet('导入设置 JSON',`${banner('真实导入支持旧版无版本号数据；错误文件应显示失败反馈。')}<div class="sheet-footer"><button class="secondary-btn" data-action="import-failed">演示错误文件</button><button class="primary-btn" data-action="import-success">模拟选取有效文件</button></div>`);break;
  case 'import-failed':sheet('导入失败',`${banner('所选 JSON 无法解析或格式不符合要求。设置未被替换。','error')}<button class="primary-btn wide" data-action="import-settings">重新选择文件</button>`);break;
  case 'import-success':saveDemo('原型演示：有效设置导入完成，未读取真实文件');break;
  case 'auto-export':sheet('自动导出设置',`<p class="small-note">需要先通过系统目录选择器指定保存位置；取消或选择失败时不会启用。</p><div class="field"><label>保存目录示例</label><input value="Documents/MediaCraft" readonly></div><div class="sheet-footer"><button class="secondary-btn" data-action="auto-export-cancel">取消选择</button><button class="primary-btn" data-action="auto-export-confirm">模拟目录授权成功</button></div>`);break;
  case 'auto-export-cancel':saveDemo('原型演示：已取消目录选择，自动导出未启用');break;
  case 'auto-export-confirm':saveDemo('原型演示：目录授权成功，自动导出已启用');break;
  case 'home-choice':choices('默认主页',['媒体库','相册','标签','文件夹','当前筛选图库','指定文件夹'],'home-choice-save','地图区域只能创建桌面快捷方式，不作为主页类型。');break;
  case 'home-choice-save':state.settingValues.home=d.value;saveDemo(`原型演示：默认主页改为${d.value}`);break;
  case 'nav-editor':sheet('底部导航',`${checkboxes([['媒体库'],['相册'],['探索'],['工具']])}${banner('这里是建议的默认结构。已有导航编辑能力继续保留，用户可调整支持的页面入口。')}<button class="primary-btn wide" data-action="save-generic">保存导航</button>`);break;
  case 'bookmark-editor':sheet('侧栏与快捷书签',`${checkboxes([['照片与视频类型'],['常用相册'],['标签与地点'],['地图与文件夹'],['关于与维护']])}<button class="primary-btn wide" style="margin-top:15px" data-action="save-generic">保存</button>`,'新版以主导航和快捷入口组织，原有配置能力保留。');break;
  case 'screen-on':choices('保持屏幕常亮',['关闭','查看媒体时','始终'],'save-choice');break;
  case 'setting-toggle':choices(d.value||'设置项',['开启','关闭'],'save-choice');break;
  case 'confirm-settings':sheet('确认对话框',`${checkboxes([['移入回收站'],['永久删除'],['移动文件'],['移除元数据']])}<button class="primary-btn wide" style="margin-top:17px" data-action="save-generic">保存</button>`,'以现有允许配置的确认规则为准；破坏性操作仍需清楚表达。');break;
  case 'quick-actions':case 'viewer-actions':sheet('快速操作',`${checkboxes([['收藏'],['分享'],['编辑'],['详情'],['删除'],['标签',false],['评分',false],['移动',false]])}<button class="primary-btn wide" style="margin-top:15px" data-action="save-generic">保存快捷操作</button>`,'可配置动作顺序，超出当前屏幕宽度的操作收纳到更多菜单。');break;
  case 'thumb-overlay':case 'viewer-overlay':sheet('信息叠加层',`${checkboxes([['媒体类型'],['视频时长'],['评分'],['标签',false],['直方图',false],['迷你地图',false],['拍摄参数',false]])}<button class="primary-btn wide" style="margin-top:15px" data-action="save-generic">保存</button>`);break;
  case 'burst-mode':choices('连拍识别',['关闭','按文件名','按元数据','自动'],'save-choice');break;
  case 'viewer-settings':openSettingsSection('viewer');break;
  case 'video-settings':openSettingsSection('video');break;
  case 'playback-settings':sheet('视频播放行为',`${checkboxes([['自动播放'],['循环播放',false],['记住播放位置'],['保留音量与速度设置']])}<button class="primary-btn wide" style="margin-top:15px" data-action="save-generic">保存</button>`);break;
  case 'video-controls':sheet('播放器控件',`${checkboxes([['前后跳转 10 秒'],['音量与亮度手势'],['逐帧按钮',false],['锁定控件'],['外部播放器入口']])}<button class="primary-btn wide" style="margin-top:15px" data-action="save-generic">保存</button>`);break;
  case 'subtitle-settings':sheet('字幕主题',`<div class="subtitle-preview">风经过森林，时间慢了下来。</div><div class="field"><label>字号</label><input type="range" min="12" max="32" value="18" aria-label="字幕字号"></div><div class="field-grid"><div class="field"><label>文本颜色</label><input type="color" value="#ffffff" aria-label="字幕颜色"></div><div class="field"><label>位置</label><select><option>底部居中</option><option>顶部居中</option></select></div></div>${checkboxes([['描边'],['背景',false]])}<button class="primary-btn wide" style="margin-top:15px" data-action="save-generic">保存字幕主题</button>`);break;
  case 'video-thumbnails':choices('视频缩略图',['系统默认','由应用生成'],'save-choice');break;
  case 'image-background':choices('图片背景',['纯色','棋盘格','动态背景'],'save-choice');break;
  case 'animation-settings':choices('动画',['跟随系统','关闭','开启'],'save-choice');break;
  case 'timeout-settings':choices('操作生效时间',['跟随系统','10 秒','30 秒','1 分钟'],'save-choice');break;
  case 'theme-choice':choices('主题',['明亮','深色','跟随系统'],'theme-save','本次原型以明亮主题为设计交付；深色仅保留设置入口。');break;
  case 'theme-save':state.settingValues.theme=d.value;saveDemo(`原型演示：主题偏好已记录为${d.value}，本稿展示明亮主题`);break;
  case 'accent-choice':sheet('强调色',`<div class="palette" style="gap:18px;justify-content:center;margin:25px 0">${['#356a5d','#967946','#718195','#9a7774'].map(c=>`<button data-action="accent-save" data-value="${c}" style="background:${c};width:44px;height:44px;border-radius:50%" aria-label="选择强调色${c}"></button>`).join('')}</div>`);break;
  case 'accent-save':saveDemo(`原型演示：强调色偏好 ${d.value} 已记录`);break;
  case 'brightness-settings':choices('最高亮度',['跟随系统','查看图片时提高亮度','查看所有媒体时提高亮度'],'save-choice');break;
  case 'refresh-settings':choices('刷新率',['系统默认','最高可用刷新率'],'save-choice');break;
  case 'tv-preview':sheet('TV 界面',`<div class="tv-preview"><div class="tv-rail">${icon('grid','sm')}${icon('album','sm')}${icon('compass','sm')}${icon('settings','sm')}</div><div class="tv-thumb-grid">${photos.slice(0,9).map(p=>image(p.id,p.title)).join('')}</div></div><p class="small-note">保留遥控器焦点、侧边导航和完整操作行。部分触屏专属地图、投屏与手势设置按现有条件隐藏。</p><button class="primary-btn wide" style="margin-top:18px" data-action="save-generic">保存界面偏好</button>`,'这是 TV 能力保留示意，不作为本次手机原型的独立实现。');break;
  case 'locale-choice':choices('界面语言',['简体中文','繁體中文','English','跟随系统'],'save-choice');break;
  case 'calendar-choice':choices('日历',['公历','波斯历','回历'],'save-choice');break;
  case 'coordinate-choice':choices('坐标格式',['十进制度数','度分秒','度与十进制分'],'save-choice');break;
  case 'units-choice':choices('单位制',['公制','英制'],'save-choice');break;
  case 'number-choice':choices('数字格式',['跟随语言','西文数字'],'save-choice');break;
  case 'screen-saver':sheet('屏保设置',`<div class="field"><label>内容集合</label><select><option>全部媒体</option><option>收藏</option><option>山野拾光</option></select></div>${checkboxes([['随机播放'],['循环播放'],['跳过视频',false]])}${row('play','预览屏保布局','沿用独立屏保 AppMode','data-route="slideshow"')}<button class="primary-btn wide" style="margin-top:15px" data-action="save-generic">保存屏保配置</button>`);break;
  case 'widget-settings':sheet('桌面小组件',`<div class="widget-preview">${image('lake','桌面小组件预览')}<span>山野拾光</span></div><div class="field"><label>内容来源</label><select><option>媒体集合</option><option>固定照片</option></select></div><div class="field-grid"><div class="field"><label>形状</label><select><option>圆角矩形</option><option>圆形</option></select></div><div class="field"><label>轮廓</label><select><option>无轮廓</option><option>细边框</option></select></div></div><div class="field"><label>点击后打开</label><select><option>主页</option><option>对应媒体集合</option><option>查看器</option><option>更新小组件</option></select></div><button class="primary-btn wide" data-action="save-generic">保存小组件配置</button>`);break;
  case 'toggle':{
    if(d.key==='bin'&&state.toggles.bin){sheet('关闭回收站前',`${banner('启用回收站策略的保险库仍依赖此功能时，不能关闭。','warning')}<p class="small-note">回收站有内容时，需要确认永久删除；失败或取消都不能关闭回收站。</p><div class="sheet-footer"><button class="secondary-btn" data-action="close-sheet">保留启用</button><button class="primary-btn" data-action="bin-dependency">查看依赖示例</button></div>`);break;}
    if(d.key==='history'&&state.toggles.history){confirm('关闭搜索历史？','关闭会立即清空已有历史。','disable-history','关闭并清空');break;}
    state.toggles[d.key]=!state.toggles[d.key];rerender();break;
  }
  case 'bin-dependency':sheet('回收站依赖',`${banner('“旅行私藏”保险库当前使用回收站。请先在保险库配置中处理该依赖。','warning')}${row('lock','配置保险库','','data-action="vault-settings"')}${row('trash','无保险库依赖的关闭流程','演示确认永久删除后关闭','data-action="disable-bin-flow"')}`);break;
  case 'disable-bin-flow':confirm('关闭并清理回收站？',`此情景演示假定已解除保险库依赖。回收站内 ${state.binItems.size} 项将永久删除；真实操作失败或取消时不得关闭。`,'disable-bin-confirm','确认关闭',true);break;
  case 'disable-bin-confirm':state.toggles.bin=false;state.binItems.clear();state.binSelected.clear();closeSheet();render();toast('原型演示：回收站已关闭');break;
  case 'disable-history':state.toggles.history=false;state.searchHistory=[];closeSheet();render();break;
  case 'permissions':sheet('系统权限',`${row('image','照片与视频','已授权部分媒体 · 示例','data-action="permission-media"')}${row('pin','媒体位置元数据','读取照片中未脱敏的位置','data-action="permission-location"')}${row('shield','管理所有媒体','设备和系统支持时可申请','data-action="permission-manage"')}${row('grid','已安装应用访问','用于识别相册来源 · 可选','data-action="permission-apps"')}${row('bell','通知','系统支持时管理通知权限','data-action="permission-notification"')}${banner('错误报告开关仅 Play flavor 可见，libre / baidu 不显示。')}`,'真实授权在 Android 系统面板完成，此处不会请求权限。');break;
  case 'permission-media':case 'permission-location':case 'permission-manage':case 'permission-apps':case 'permission-notification':choices('系统授权结果演示',[['允许','继续使用对应能力'],['拒绝','保留已授权范围，不强制退出']],'permission-result');break;
  case 'permission-result':saveDemo(`原型演示：已${d.value}。实际权限需在 Android 中确认。`);break;
  case 'storage-grants':sheet('存储访问授权',`${row('folder','Pictures/Import','content://…/tree/primary:Pictures','data-action="revoke-grant"')}${row('folder','Documents/MediaCraft','content://…/tree/primary:Documents','data-action="revoke-grant"')}${row('info','查看加载失败状态','','data-action="grant-failure"')}${banner('SAF 目录授权可以逐个撤销，具体写操作仍可能需要再次申请。')}`);break;
  case 'revoke-grant':confirm('撤销目录访问？','撤销后，该目录的相关写入操作可能需要重新授权。','save-generic','撤销');break;
  case 'grant-failure':sheet('无法加载授权列表',`${banner('存储授权列表读取失败，请重试。','error')}<button class="primary-btn wide" data-action="storage-grants">重新加载</button>`);break;
  case 'hidden-rules':sheet('隐藏规则',`<p class="sheet-description">隐藏只影响可见性。每条规则可以独立停用或移除，不替代保险库认证。</p>${state.hiddenRules.map((rule,index)=>`<div class="hidden-rule"><button data-action="hidden-toggle" data-index="${index}" role="switch" aria-checked="${rule.active}"><span class="row-icon">${icon('eyeoff')}</span><span>${escapeHtml(rule.label)}<small>${rule.active?'已启用':'已暂停'}</small></span><span class="toggle ${rule.active?'on':''}"></span></button><button class="icon-btn" data-action="remove-hidden" data-index="${index}" aria-label="移除隐藏规则${escapeHtml(rule.label)}">${icon('close','sm')}</button></div>`).join('')||'<div class="inline-empty">没有隐藏规则。</div>'}`);break;
  case 'hidden-toggle':state.hiddenRules[Number(d.index)].active=!state.hiddenRules[Number(d.index)].active;dispatch('hidden-rules');break;
  case 'remove-hidden':state.hiddenRuleIndex=Number(d.index);confirm('移除隐藏规则？','符合这一规则的媒体将重新出现在相关图库与建议中。','hidden-remove-confirm','恢复显示');break;
  case 'hidden-remove-confirm':state.hiddenRules.splice(state.hiddenRuleIndex,1);dispatch('hidden-rules');break;
  case 'baidu-privacy':state.baiduConsent?confirm('撤回百度地图授权？','撤回后百度 SDK 不再初始化；你仍可切换到 OpenFreeMap 样式。','baidu-revoke','撤回'):sheet('尚未授权百度地图',`<p class="small-note">只有选择百度普通或百度卫星样式时才需要请求同意。</p><button class="primary-btn wide" style="margin-top:18px" data-route="map">去照片地图</button>`);break;
  case 'baidu-revoke':state.baiduConsent=false;closeSheet();render();toast('原型演示：已撤回百度地图授权');break;
  case 'check-update':sheet('更新检查',`${banner('以下都是状态设计样例，不读取 GitHub 实时发布信息。')}${row('check','已是最新版本','展示无更新状态','data-action="update-current"')}${row('download','发现新版本','展示查看发布详情入口','data-action="update-found"')}${row('info','网络或解析失败','展示重试入口','data-action="update-failed"')}`);break;
  case 'update-current':sheet('当前已是最新版本',`<p class="small-note">更新状态示例，未核验线上发布版本。</p><button class="primary-btn wide" style="margin-top:20px" data-action="close-sheet">完成</button>`);break;
  case 'update-found':sheet('发现新版本 · 示例',`<p class="small-note">真实检查根据 GitHub Latest Release 比较正式版本号，并打开发布详情。</p><button class="primary-btn wide" style="margin-top:18px" data-action="release-page">查看发布详情</button>`);break;
  case 'release-page':saveDemo('原型演示：发布详情入口，不发起更新或下载安装');break;
  case 'update-failed':sheet('更新检查未完成',`${banner('网络请求失败或返回格式无法解析。','error')}<button class="primary-btn wide" data-action="check-update">重试</button>`);break;
  case 'derivation':sheet('产品与源码',`<p class="small-note">MediaCraft 是面向本机照片与视频的媒体管理应用。本页集中展示版本、源码、构建与维护入口。</p>${row('code','项目源码','github.com/404NotFoundYou/MediaCraft','data-action="source-link"')}${row('info','版本与构建','当前版本、flavor 与发布信息','data-action="close-sheet"')}${row('code','开源许可证','第三方来源与许可在许可证页单独列出','data-action="licenses"')}`);break;
  case 'source-link':saveDemo('原型演示：源码外链入口，地址见项目 README');break;
  case 'bug-report':sheet('问题反馈',`<p class="small-note">真实流程会收集设备、Android、Flutter 与版本诊断信息，再由你确认跳转 GitHub Issue。</p><table class="metadata-table"><tr><td>应用</td><td>MediaCraft 0.6.3（示例）</td></tr><tr><td>平台</td><td>Android / 设备信息占位</td></tr><tr><td>诊断</td><td>未读取当前电脑或手机信息</td></tr></table><button class="primary-btn wide" data-action="report-issue">查看报告入口</button>`);break;
  case 'report-issue':saveDemo('原型演示：反馈草稿与 GitHub Issue 入口，未发送报告');break;
  case 'storage-usage':sheet('数据占用',`<table class="metadata-table">${[['回收站','13.7 MB'],['数据库','28.4 MB'],['保险库','84.2 MB'],['其他数据','6.8 MB'],['Flutter','38.6 MB'],['内部缓存','126 MB'],['外部缓存','42 MB']].map(([t,v])=>`<tr><td>${t}</td><td>${v}</td></tr>`).join('')}</table><button class="primary-btn wide" data-action="clear-cache">清理缓存</button>`,'以上均为布局示例数据，不是当前设备的实际占用。');break;
  case 'clear-cache':confirm('清理应用缓存？','真实流程清理临时目录、外部缓存、图片磁盘缓存、地图缓存和内存图片缓存，不删除媒体。','cache-confirm','清理');break;
  case 'cache-confirm':saveDemo('原型演示：缓存清理完成，未操作设备缓存');break;
  case 'credits':sheet('致谢与翻译者',`<p class="small-note">感谢参与 MediaCraft 开发、翻译、测试与维护的贡献者，以及应用使用的第三方开源项目。</p><p class="small-note" style="margin-top:13px">具体版权与许可信息统一在开源许可证页面查看。</p>`);break;
  case 'licenses':sheet('开源许可证',`${row('code','Aves · BSD 3-Clause','原始版权与许可继续保留','data-action="license-text"')}${row('code','第三方通知','THIRD_PARTY_NOTICES.md','data-action="license-text"')}${row('code','依赖许可证','项目 licenses/ 目录','data-action="license-text"')}`);break;
  case 'license-text':sheet('许可证正文入口',`<p class="small-note">正式应用沿用项目现有许可证展示内容。该静态原型不复制可能过期的全部依赖许可文本；证据为仓库 LICENSE、THIRD_PARTY_NOTICES.md 与 licenses/。</p>`);break;
  case 'terms':sheet('使用条款与隐私',`<p class="small-note">MediaCraft 当前用于个人学习、研究和本地测试。完整文本以项目现有条款、隐私政策与来源许可为准。</p><div class="divider"></div><p class="small-note">原型中的条款不是新的法律文本，未替代应用现有的协议内容。</p><button class="primary-btn wide" style="margin-top:20px" data-action="close-sheet">返回</button>`);break;
  case 'picker-mode':choices('系统调用模式',['单选','多选','选择筛选条件','固定范围内部选择','可变范围内部选择'],'picker-mode-choice');break;
  case 'picker-mode-choice':state.pickerMode=d.value;state.pickerSelected.clear();state.pickerFilters=[];state.pickerFilter=d.value==='固定范围内部选择'?'照片':'全部媒体';closeSheet();render();break;
  case 'picker-filter':if(state.pickerMode==='固定范围内部选择'){toast('固定范围不能更改既有条件');break;}choices('选择范围',['全部媒体','收藏','照片','视频'],'picker-filter-choice');break;
  case 'picker-filter-choice':state.pickerFilter=d.value;state.pickerSelected.clear();closeSheet();render();break;
  case 'picker-album':choices('选择相册',['相机','下载','山野拾光'],'picker-filter-choice');break;
  case 'picker-condition-type':choices('选择'+d.value,d.value==='相册'?['相机','山野拾光','旅行手记（组）']:d.value==='标签'?['旅行','山野','日常']:d.value==='地点'?['中国','浙江','杭州']:['照片','视频','RAW'],'picker-add-condition');break;
  case 'picker-add-condition':state.pickerFilters??=[];if(!state.pickerFilters.includes(d.value))state.pickerFilters.push(d.value);closeSheet();render();break;
  case 'picker-remove-condition':state.pickerFilters=state.pickerFilters.filter(f=>f!==d.value);render();break;
  case 'picker-return-filters':sheet('返回组合条件',`<div class="chips">${state.pickerFilters.map(f=>`<span class="chip active">${escapeHtml(f)}</span>`).join('')}</div><p class="small-note" style="margin:18px 0">将筛选条件交还给调用方，静态原型不会跨应用传递数据。</p><button class="primary-btn wide" data-action="picker-finish">完成演示</button>`);break;
  case 'pick-photo':if(state.pickerMode==='选择筛选条件')break;if(state.pickerMode!=='多选')state.pickerSelected.clear();state.pickerSelected.has(d.id)?state.pickerSelected.delete(d.id):state.pickerSelected.add(d.id);rerender();break;
  case 'picker-done':sheet('选择完成',`<div class="photo-strip">${photos.filter(p=>state.pickerSelected.has(p.id)).map(p=>`<button data-action="close-sheet" aria-label="所选${p.title}">${image(p.id,p.title)}</button>`).join('')}</div><p class="small-note" style="margin-top:18px">将向调用方返回 ${state.pickerSelected.size} 项媒体引用；静态原型不会向其他应用发送内容。</p><button class="primary-btn wide" style="margin-top:18px" data-action="picker-finish">完成演示</button>`);break;
  case 'picker-finish':state.pickerSelected.clear();closeSheet();go('library');toast('原型演示：已返回选择结果');break;
  case 'picker-cancel':state.pickerSelected.clear();go('library');break;
  case 'welcome-continue':if(!state.acceptTerms){toast('请先阅读并接受条款');break;}state.stateDemo='permission';go('states');break;
  case 'welcome-flavor':state.welcomeFlavor=d.value;render();break;
  case 'state-demo':state.stateDemo=d.value;render();break;
  case 'retry-library':toast('原型演示：重新扫描完成，载入示例媒体');go('library');break;
  case 'error-details':sheet('初始化错误详情',`<pre style="font-size:10px;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere">示例错误：MediaStoreInitializationError\n无法完成本地数据库初始化\n\n堆栈详情区域（静态占位）</pre><div class="sheet-footer"><button class="secondary-btn" data-action="export-database">导出数据库</button><button class="primary-btn" data-action="bug-report">报告问题</button></div>`);break;
  case 'export-database':confirm('导出数据库用于排查？','真实流程将调用系统文件保存接口，并给出成功或失败反馈。原型不读取数据库。','export-demo','确认导出');break;
  case 'save-choice':saveDemo('原型演示：已选择 '+d.value);break;
  case 'save-generic':saveDemo();break;
  default:console.error('未配置的原型动作：',act);toast('此原型入口尚未配置：'+act);
 }
}

const capabilities=[...(window.MC_NAV_CAPABILITIES||[]),...(window.MC_CAPABILITIES||[]),...(window.MC_TOOL_CAPABILITIES||[])];
function renderCoverage(query=''){
 const list=capabilities.filter(c=>(c.label+c.group+c.detail+c.evidence).toLowerCase().includes(query.toLowerCase()));
 const catalog=window.MC_CAPABILITY_CATALOG||{label:'MediaCraft 功能目录'};
 $('#coverage-count').textContent=`${catalog.label} · ${list.length} 项能力 / ${routes.length} 个页面与状态 / 代码证据索引`;
 const groups=[...new Set(list.map(c=>c.group))];
 $('#coverage-list').innerHTML=groups.map(group=>`<h3 class="coverage-group">${escapeHtml(group)}</h3>${list.filter(c=>c.group===group).map(c=>`<button class="capability-row" data-capability-route="${c.route}"><span>${screenInfo[c.route]?.[0]||c.route} ↗</span><strong>${escapeHtml(c.label)}</strong><p>${escapeHtml(c.detail)}</p><code>${escapeHtml(c.evidence)}</code></button>`).join('')}`).join('');
}
function openCoverage(){renderCoverage();$('#coverage-dialog').showModal();$('#coverage-search').focus();}
document.addEventListener('click',event=>{
 const button=event.target.closest('button,a[data-route]');
 if(!button||button.disabled)return;
 const d=button.dataset;
 if(d.capabilityRoute){$('#coverage-dialog').close();go(d.capabilityRoute);return;}
 if(d.route){go(d.route);return;}
 if(d.toast){toast(d.toast);return;}
 if(d.action){dispatch(d.action,d);return;}
});
document.addEventListener('input',event=>{
 if(event.target.id==='media-search')state.query=event.target.value;
 if(event.target.id==='settings-search'){state.settingsQuery=event.target.value;$('#settings-rows').innerHTML=settingsRows(state.settingsQuery);}
 if(event.target.id==='coverage-search')renderCoverage(event.target.value);
 if(event.target.dataset.action==='video-progress'){
  const t=Number(event.target.value);$('#video-current').textContent=`${String(Math.floor(t/60)).padStart(2,'0')}:${String(t%60).padStart(2,'0')}`;
 }
});
document.addEventListener('change',event=>{
 const d=event.target.dataset;
 if(d.action==='select-duplicate'){
  const [g,f]=d.key.split('-').map(Number);
  if(state.dupKeep[g]===f)return;
  event.target.checked?state.dupSelected.add(d.key):state.dupSelected.delete(d.key);rerender();
 }
 if(d.action==='accept-terms'){state.acceptTerms=event.target.checked;const b=$('[data-action="welcome-continue"]');if(b)b.disabled=!state.acceptTerms;}
 if(d.action==='optional-apps')toast('原型演示：可选应用访问偏好已记录');
 if(d.action==='optional-report')toast('原型演示：可选错误报告偏好已记录，仅 Play 版本显示');
});
$('#sheet-layer').addEventListener('click',e=>{if(e.target===$('#sheet-layer'))closeSheet();});
$('#coverage-button').addEventListener('click',openCoverage);
$('#coverage-button-notes').addEventListener('click',openCoverage);
$('#close-coverage').addEventListener('click',()=>$('#coverage-dialog').close());
$('#fullscreen-button').addEventListener('click',()=>document.body.classList.toggle('focus'));
window.addEventListener('popstate',()=>{const route=location.hash.slice(1);if(routes.includes(route)){if(state.scanTimer){clearInterval(state.scanTimer);state.scanTimer=null;}if(state.routeStack.at(-1)===route)state.routeStack.pop();state.route=route;closeSheet();render();}});
window.addEventListener('hashchange',()=>{const route=location.hash.slice(1);if(routes.includes(route)&&route!==state.route)go(route);});
document.addEventListener('keydown',event=>{
 if(event.key==='Escape'){
  if(!$('#sheet-layer').hidden)closeSheet();
  else document.body.classList.remove('focus');
 }
 if(!$('#sheet-layer').hidden&&event.key==='Tab'){
  const items=[...$('#sheet-layer').querySelectorAll('button:not(:disabled),input:not(:disabled),select,textarea')];
  const first=items[0],last=items.at(-1);
  if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}
  else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}
 }
 if(['INPUT','SELECT','TEXTAREA'].includes(event.target.tagName)||!$('#sheet-layer').hidden||$('#coverage-dialog').open)return;
 if(event.key==='ArrowRight'||event.key==='ArrowLeft'){
  const index=routes.indexOf(state.route),offset=event.key==='ArrowRight'?1:-1;
  go(routes[(index+offset+routes.length)%routes.length]);
 }
});
state.route=routes.includes(location.hash.slice(1))?location.hash.slice(1):'library';
render();
