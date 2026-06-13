<h1 id="npc模组" data-toc-id="npc模组">NPC模组</h1>
<h2 id="基础信息" data-toc-id="基础信息">基础信息</h2>
<p>核心理念：使用ldlib2制作可视化编辑界面，使用KubeJS实现脚本自定义npcai逻辑。成为最自由功能最丰富最现代化的npc模组</p>
<p>版本：1.21.1 NeoForge</p>
<p>模组前置：ldlib2</p>
<p>模组联动：KubeJS</p>
<h2 id="功能清单" data-toc-id="功能清单">功能清单</h2>
<h2 id="标" data-toc-id="标">基础配置</h2>
<ul>
  <li>
    <p>标记 √</p>
  </li>
  <li>
    <p>名称 √</p>
  </li>
  <li>
    <p>副名称 √</p>
  </li>
  <li>
    <p>名称显示 √</p>
  </li>
  <li>
    <p>尺寸</p>
  </li>
  <li>
    <p>材质色调 √</p>
  </li>
  <li>
    <p>材质类型 √</p>
  </li>
  <li>
    <p>材质路径或玩家ID或UUID √</p>
  </li>
  <li>
    <p>披风材质 √</p>
  </li>
  <li>
    <p>是否无敌 √</p>
  </li>
  <li>
    <p>是否关闭AI X</p>
  </li>
  <li>
    <p>是否关闭重力影响 √</p>
  </li>
</ul>
<h2 id="渲染" data-toc-id="渲染">渲染</h2>
<ul>
  <li>
    <p>生物类型 √</p>
  </li>
  <li>
    <p>NBT √</p>
  </li>
  <li>
    <p>头部（x，y，z，xRot，yRot，zRot，宽度，高度，深度，可见）√</p>
  </li>
  <li>
    <p>躯干（x，y，z，xRot，yRot，zRot，宽度，高度，深度，可见）√</p>
  </li>
  <li>
    <p>双臂左右同步 √</p>
  </li>
  <li>
    <p>左臂（x，y，z，xRot，yRot，zRot，宽度，高度，深度，可见）√</p>
  </li>
  <li>
    <p>右臂（x，y，z，xRot，yRot，zRot，宽度，高度，深度，可见）√</p>
  </li>
  <li>
    <p>双腿左右同步 √</p>
  </li>
  <li>
    <p>左腿（x，y，z，xRot，yRot，zRot，宽度，高度，深度，可见）√</p>
  </li>
  <li>
    <p>右腿（x，y，z，xRot，yRot，zRot，宽度，高度，深度，可见）√</p>
  </li>
  <li>
    <p>碰撞箱 √</p>
  </li>
</ul>
<h2 id="ai" data-toc-id="ai">AI</h2>
<p>
  发现敌人时(无，撤退，攻击，恐慌)，与门交互（无，打开，打破），是否可以游泳，寻求庇护（禁用，黑暗，光明），必须看到目标[看到目标才能进攻之类的]，是否可以攻击隐身实体，是否避水，是否返回起点，是否跃向目标，是否支持骑乘控制，移动设置
</p>
<p>后续考虑可以直接用Behavior Designer的表单搞定</p>
<h2 id="属性" data-toc-id="属性">属性</h2>
<ul>
  <li>
    <p>最大生命值 √</p>
  </li>
  <li>
    <p>移动速度 √</p>
  </li>
  <li>
    <p>仇恨范围 X</p>
  </li>
  <li>
    <p>是否免疫火焰伤害 √</p>
  </li>
  <li>
    <p>是否会溺水 √</p>
  </li>
  <li>
    <p>是否免疫药水效果 √</p>
  </li>
  <li>
    <p>是否有摔落伤害 √</p>
  </li>
  <li>
    <p>是否会在白天自燃 √</p>
  </li>
  <li>
    <p>是否忽略蜘蛛网的影响 √</p>
  </li>
  <li>
    <p>是否受亡灵杀手影响 √</p>
  </li>
  <li>
    <p>是否受节肢杀手影响 √</p>
  </li>
  <li>
    <p>是否受穿刺影响 √</p>
  </li>
  <li>
    <p>战时回血 √</p>
  </li>
  <li>
    <p>脱战回血 √</p>
  </li>
</ul>
<h3 id="近战" data-toc-id="近战">近战</h3>
<ul>
  <li>
    <p>伤害 √</p>
  </li>
  <li>
    <p>攻速 √</p>
  </li>
  <li>
    <p>范围 √</p>
  </li>
  <li>
    <p>击退 √</p>
  </li>
  <li>
    <p>攻击额外效果（debuff药水或者着火） √</p>
  </li>
</ul>
<h3 id="抗性提升" data-toc-id="抗性提升">抗性提升</h3>
<ul>
  <li>
    <p>击退抗性 √</p>
  </li>
  <li>
    <p>弹射物抗性 √</p>
  </li>
  <li>
    <p>爆炸抗性 √</p>
  </li>
  <li>
    <p>近战攻击抗性 √</p>
  </li>
</ul>
<h3 id="防御属性" data-toc-id="防御属性">防御属性</h3>
<ul>
  <li>
    <p>护甲值 √</p>
  </li>
  <li>
    <p>护甲韧性 √</p>
  </li>
  <li>
    <p>反弹伤害值 √</p>
  </li>
</ul>
<h3 id="重生" data-toc-id="重生">重生(暂删)</h3>
<ul>
  <li>
    <p>是否重生</p>
  </li>
  <li>
    <p>重生时间</p>
  </li>
</ul>
<h3 id="远程" data-toc-id="远程">远程</h3>
<ul>
  <li>
    <p>命中率</p>
  </li>
  <li>
    <p>单次射击弹射物</p>
  </li>
  <li>
    <p>范围</p>
  </li>
  <li>
    <p>最小攻击延迟</p>
  </li>
  <li>
    <p>弹射物射击音效</p>
  </li>
  <li>
    <p>弹射物落地音效</p>
  </li>
</ul>
<h3 id="t" data-toc-id="t">弹射物</h3>
<ul>
  <li>
    <p>力量</p>
  </li>
  <li>
    <p>击退</p>
  </li>
  <li>
    <p>尺寸</p>
  </li>
  <li>
    <p>速度</p>
  </li>
  <li>
    <p>受重力影响</p>
  </li>
  <li>
    <p>爆炸</p>
  </li>
  <li>
    <p>远程攻击效果</p>
  </li>
  <li>
    <p>尾迹类型</p>
  </li>
</ul>
<h2 id="物品" data-toc-id="物品">物品</h2>
<ul>
  <li>
    <p>头盔 √</p>
  </li>
  <li>
    <p>胸甲 √</p>
  </li>
  <li>
    <p>护腿 √</p>
  </li>
  <li>
    <p>靴子 √</p>
  </li>
  <li>
    <p>主手 √</p>
  </li>
  <li>
    <p>副手 √</p>
  </li>
  <li>
    <p>掉落经验的范围 √</p>
  </li>
  <li>
    <p>战利品配置类型（数据包或者自定义配置）√</p>
  </li>
</ul>
<h3 id="curios" data-toc-id="curios">Curios API联动</h3>
<ul>
  <li>
    <p>饰品 √</p>
  </li>
</ul>
<h2 id="事件" data-toc-id="事件">事件</h2>
<h2 id="联动" data-toc-id="联动">联动</h2>
<p></p>
<p>√完成 X确认有Bug !需要测试是否Bug [空]未完成</p>
<h2 id="问题" data-toc-id="问题">问题</h2>
<p></p>