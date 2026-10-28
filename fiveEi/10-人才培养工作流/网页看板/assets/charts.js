// 智能制造专业群 · 岗位需求与技能热度智能看板 —— 图表逻辑（预生成数据版）
// 数据来自 window.TALENT_DATA.charts（由 生成页面数据.py 产出）；
// 缺少数据时回落到页面原始的静态示例，保证离线也不白屏。
(function () {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var font = style.getPropertyValue('--font').trim();
  var fontMono = style.getPropertyValue('--font-mono').trim();

  var baseText = { fontFamily: font, color: muted, fontSize: 12 };
  var chartHandles = [];
  var D = window.TALENT_DATA || {};
  var charts = D.charts || {};

  function pick(key, fallback) {
    var rows = charts[key];
    return (rows && rows.length) ? rows : fallback;
  }

  function emptyOption(text) {
    return {
      animation: false,
      textStyle: baseText,
      title: { text: text || '暂无数据', left: 'center', top: 'middle', textStyle: baseText }
    };
  }

  // ============ 图 1 · 重点方向人才缺口规模（对数刻度） ============
  (function () {
    var items = pick('人才缺口', []).map(function (x) {
      return {
        name: x['方向'] || x['指标'] || '',
        value: Number(x['缺口']) || 0,
        unit: x['单位'] || '万',
        level: x['证据强度'] || '',
        note: [x['来源'], x['年份']].filter(Boolean).join(' · ')
      };
    });
    if (!items.length) {
      items = [
        { name: '机器视觉行业人才', value: 12, unit: '万', level: '行业估算', note: '示例兜底数据' },
        { name: '数字孪生领域人才', value: 26, unit: '万', level: '二手引述', note: '示例兜底数据' },
        { name: '具身智能相关新职业', value: 100, unit: '万+', level: '官方', note: '示例兜底数据' },
        { name: '数字经济·智能制造高技能人才', value: 1000, unit: '万+', level: '官方', note: '示例兜底数据' }
      ];
    }
    var maxValue = Math.max.apply(null, items.map(function (d) { return d.value; }).concat([10]));

    var chart = echarts.init(document.getElementById('chart-gap-scale'), null, { renderer: 'svg' });
    chart.setOption({
      animation: false,
      textStyle: baseText,
      grid: { left: 8, right: 78, top: 18, bottom: 34, containLabel: true },
      tooltip: {
        appendToBody: true,
        textStyle: { fontFamily: font },
        formatter: function (p) {
          var it = items[p.value[1]];
          return '<strong>' + it.name + '：' + it.value.toLocaleString('en-US') + ' ' + it.unit + '</strong>'
            + (it.level ? '<br/>证据强度：' + it.level : '')
            + (it.note ? '<br/>' + it.note : '');
        }
      },
      xAxis: {
        type: 'log',
        logBase: 10,
        min: 10,
        max: Math.max(2000, Math.ceil(maxValue * 2)),
        name: '人才缺口（万人，对数刻度）',
        nameLocation: 'middle',
        nameGap: 26,
        nameTextStyle: { fontFamily: font, color: muted, fontSize: 11 },
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { fontFamily: fontMono, color: muted, formatter: function (v) { return v >= 1000 ? (v / 1000) + 'k' : v; } },
        splitLine: { lineStyle: { color: rule, type: 'dashed' } }
      },
      yAxis: {
        type: 'category',
        data: items.map(function (d) { return d.name; }),
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false },
        axisLabel: { fontFamily: font, color: ink, fontSize: 11, width: 180, overflow: 'truncate' }
      },
      series: [{
        type: 'scatter',
        symbolSize: 17,
        itemStyle: { color: accent, borderColor: bg2, borderWidth: 2 },
        label: {
          show: true,
          position: 'right',
          distance: 8,
          fontFamily: fontMono,
          fontSize: 12,
          fontWeight: 700,
          color: ink,
          formatter: function (p) {
            var it = items[p.value[1]];
            return it.value.toLocaleString('en-US') + ' ' + it.unit;
          }
        },
        data: items.map(function (d, i) { return [Math.max(10, d.value), i]; })
      }]
    });
    chartHandles.push(chart);
  })();

  // ============ 图 2 · 先进制造相关岗位招聘需求同比增速 ============
  (function () {
    var items = pick('招聘增速', []).map(function (x) {
      return {
        name: x['岗位'] + (x['指标'] ? '（' + x['指标'] + '）' : ''),
        value: Number(x['增速']) || 0,
        src: [x['来源'], x['统计期']].filter(Boolean).join('，')
      };
    });
    if (!items.length) {
      items = [
        { name: '机器人产业整体招聘职位数', value: 6.0, src: '示例兜底数据' },
        { name: '机器人算法工程师', value: 41.4, src: '示例兜底数据' },
        { name: '机器人调试工程师', value: 57.5, src: '示例兜底数据' }
      ];
    }
    items.sort(function (a, b) { return a.value - b.value; });
    var maxValue = Math.max.apply(null, items.map(function (d) { return d.value; }).concat([10]));

    var chart = echarts.init(document.getElementById('chart-growth'), null, { renderer: 'svg' });
    chart.setOption({
      animation: false,
      textStyle: baseText,
      grid: { left: 8, right: 76, top: 22, bottom: 34, containLabel: true },
      tooltip: {
        trigger: 'axis',
        appendToBody: true,
        axisPointer: { type: 'shadow', shadowStyle: { color: accent + '0f' } },
        textStyle: { fontFamily: font },
        formatter: function (ps) {
          var p = ps[0];
          var it = items[p.dataIndex];
          return '<strong>' + it.name + '</strong><br/>同比增速：+' + it.value.toFixed(1) + '%'
            + (it.src ? '<br/><span style="color:' + muted + '">' + it.src + '</span>' : '');
        }
      },
      xAxis: {
        type: 'value',
        max: Math.ceil(maxValue * 1.25),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { fontFamily: fontMono, color: muted, formatter: '{value}%' },
        splitLine: { lineStyle: { color: rule, type: 'dashed' } }
      },
      yAxis: {
        type: 'category',
        data: items.map(function (d) { return d.name; }),
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false },
        axisLabel: { fontFamily: font, color: ink, fontSize: 11, width: 220, overflow: 'truncate' }
      },
      series: [{
        type: 'bar',
        barWidth: 15,
        itemStyle: {
          borderRadius: [0, 4, 4, 0],
          color: function (p) { return p.value >= 100 ? accent : accent2; }
        },
        label: {
          show: true,
          position: 'right',
          distance: 8,
          fontFamily: fontMono,
          fontSize: 11,
          fontWeight: 700,
          color: ink,
          formatter: function (p) { return '+' + p.value.toFixed(1) + '%'; }
        },
        data: items.map(function (d) { return d.value; })
      }]
    });
    chartHandles.push(chart);
  })();

  // ============ 图 3 · 智能制造新兴岗位需求指数 ============
  (function () {
    var items = pick('需求指数', []).map(function (x) {
      return { name: x['岗位'], value: Number(x['指数']) || 0, src: x['来源'] || '' };
    });
    if (!items.length) {
      items = [
        { name: '工业机器人工程技术人员', value: 1.85, src: '示例兜底数据' },
        { name: '工业机器人系统运维员', value: 2.80, src: '示例兜底数据' },
        { name: '工业机器人系统操作员', value: 7.33, src: '示例兜底数据' }
      ];
    }
    var maxValue = Math.max.apply(null, items.map(function (d) { return d.value; }).concat([1]));

    var chart = echarts.init(document.getElementById('chart-index'), null, { renderer: 'svg' });
    chart.setOption({
      animation: false,
      textStyle: baseText,
      grid: { left: 8, right: 52, top: 22, bottom: 34, containLabel: true },
      tooltip: {
        trigger: 'axis',
        appendToBody: true,
        axisPointer: { type: 'shadow', shadowStyle: { color: accent + '0f' } },
        textStyle: { fontFamily: font },
        formatter: function (ps) {
          var p = ps[0];
          var it = items[p.dataIndex];
          return '<strong>' + it.name + '</strong><br/>人才需求指数：' + it.value
            + (it.src ? '<br/><span style="color:' + muted + '">来源 ' + it.src + '</span>' : '');
        }
      },
      xAxis: {
        type: 'value',
        max: Math.max(8, Math.ceil(maxValue * 1.2)),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { fontFamily: fontMono, color: muted },
        splitLine: { lineStyle: { color: rule, type: 'dashed' } }
      },
      yAxis: {
        type: 'category',
        data: items.map(function (d) { return d.name; }),
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false },
        axisLabel: { fontFamily: font, color: ink, fontSize: 11, width: 200, overflow: 'truncate' }
      },
      series: [{
        type: 'bar',
        barWidth: 16,
        itemStyle: {
          borderRadius: [0, 4, 4, 0],
          color: {
            type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
            colorStops: [
              { offset: 0, color: accent + '80' },
              { offset: 1, color: accent }
            ]
          }
        },
        label: {
          show: true,
          position: 'right',
          distance: 8,
          fontFamily: fontMono,
          fontSize: 12,
          fontWeight: 700,
          color: ink,
          formatter: function (p) { return p.value.toFixed(2); }
        },
        data: items.map(function (d) { return d.value; })
      }]
    });
    chartHandles.push(chart);
  })();

  // ============ 自适应 ============
  window.addEventListener('resize', function () {
    chartHandles.forEach(function (c) { c.resize(); });
  });
})();
