/* 预生成模式渲染：把 数据.js 里的数据表刷到页面既有区块上。
   页面本身仍是静态 HTML，JS 只负责替换内容；没有 数据.js 时保持原始静态内容不变。 */
(function () {
  var D = window.TALENT_DATA;
  if (!D) {
    if (window.console) console.warn("[看板] 未找到 数据.js，保留页面原有静态内容");
    return;
  }

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"]/g, function (m) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[m];
    });
  }

  function num(v) {
    if (typeof v === "number" && isFinite(v)) return v.toLocaleString("en-US");
    return esc(v);
  }

  function refs(text) {
    return String(text || "").split(/[、,，;；\s]+/).filter(Boolean).map(function (id) {
      if (!SOURCE_IDS[id]) return "<sup>[" + esc(id) + "]</sup>";   // 岗位样本等没有单独来源卡片
      return '<sup><a href="#cite-' + esc(id) + '">[' + esc(id) + "]</a></sup>";
    }).join("");
  }

  function rowsOf(name) {
    var t = D.tables && D.tables[name];
    return t && t.rows ? t.rows : [];
  }

  var SOURCE_IDS = {};
  (D.sources || []).forEach(function (s) { SOURCE_IDS[s.id] = true; });

  /* ---------- 顶部 KPI ---------- */
  var KPI_CLASS = { "最大人才缺口": "kpi-danger", "官方来源": "kpi-ok", "数据来源": "kpi-warn" };
  function renderKpi() {
    var box = document.querySelector(".kpis");
    if (!box || !D.kpi) return;
    box.innerHTML = D.kpi.map(function (k) {
      var cls = KPI_CLASS[k["指标"]] || "";
      var unit = k["单位"] ? "<small>" + esc(k["单位"]) + "</small>" : "";
      return '<div class="kpi ' + cls + '">' +
        '<div class="kpi-label">' + esc(k["指标"]) + "</div>" +
        '<div class="kpi-value">' + num(k["数值"]) + unit + "</div>" +
        '<div class="kpi-delta">' + esc(k["口径"]) + "</div>" +
        "</div>";
    }).join("");
  }

  /* ---------- 表 1 · 岗位需求对照表 ---------- */
  var TAG_CLASS = {
    "官方": "tag-official", "国家职业标准": "tag-official",
    "媒体": "tag-media", "行业": "tag-industry",
    "招聘平台": "tag-industry", "企业": "tag-industry"
  };
  function renderJobs() {
    var tbody = document.querySelector("table tbody");
    var rows = rowsOf("岗位需求表");
    if (!tbody) return;
    if (!rows.length) { tbody.innerHTML = '<tr><td colspan="5">暂无岗位数据</td></tr>'; return; }
    tbody.innerHTML = rows.map(function (r) {
      var tag = TAG_CLASS[r["证据强度"]] || "tag-industry";
      return "<tr>" +
        "<td>" + esc(r["核心岗位"]) + "</td>" +
        "<td>" + esc(r["职业编码/依据"]) + "</td>" +
        '<td title="证据原文：' + esc(r["证据原文"]) + '">' + esc(r["需求信号"]) + "</td>" +
        '<td class="src">' + esc(r["来源与统计时间"]) + "</td>" +
        '<td><span class="tag ' + tag + '">' + esc(r["证据强度"]) + "</span></td>" +
        "</tr>";
    }).join("");
  }

  /* ---------- 04 · 专业预警 ---------- */
  var ALERT_CLASS = { "紧急": "critical", "关注": "watch", "提示": "watch", "良好": "good" };
  function renderAlerts() {
    var box = document.querySelector(".alert-grid");
    var rows = rowsOf("预警表");
    if (!box) return;
    if (!rows.length) { box.innerHTML = '<div class="alert"><div class="alert-body"><h3>暂无预警</h3><p>材料中未发现可支撑预警的信号。</p></div></div>'; return; }
    box.innerHTML = rows.map(function (r) {
      return '<div class="alert ' + (ALERT_CLASS[r["等级"]] || "") + '">' +
        '<span class="alert-badge">' + esc(r["等级"]) + "</span>" +
        '<div class="alert-body">' +
        "<h3>" + esc(r["标题"]) + "</h3>" +
        '<div class="alert-metrics">' + esc(r["关键指标"]) + "</div>" +
        "<p>" + esc(r["建议动作"]) + refs(r["依据"]) + "</p>" +
        "</div></div>";
    }).join("");
  }

  /* ---------- 05 · 培养建议 ---------- */
  function renderAdvice() {
    var box = document.querySelector(".advice-grid");
    var rows = rowsOf("培养建议表");
    if (!box) return;
    if (!rows.length) { box.innerHTML = '<div class="advice"><p>暂无培养建议。</p></div>'; return; }
    box.innerHTML = rows.map(function (r, i) {
      var no = ("0" + (r["序号"] || i + 1)).slice(-2);
      return '<div class="advice">' +
        '<div class="advice-top">' +
        '<span class="advice-no">' + no + "</span>" +
        "<h3>" + esc(r["方向"]) + "</h3>" +
        '<span class="advice-tag">AI 生成 · ' + esc(r["优先级"]) + "优先级</span>" +
        "</div>" +
        "<p>" + esc(r["建议内容"]) + "</p>" +
        '<div class="basis">依据：' + refs(r["依据"]) +
        "；预期效果：" + esc(r["预期效果"]) + "</div>" +
        "</div>";
    }).join("");
  }

  /* ---------- 06 · 数据缺口 ---------- */
  function renderGaps() {
    // 页面里有两个 .gap-list：02 节的是静态说明，06 节的才是数据缺口，取最后一个
    var lists = document.querySelectorAll(".gap-list");
    var list = lists.length ? lists[lists.length - 1] : null;
    var rows = rowsOf("数据缺口表");
    if (!list) return;
    if (!rows.length) { list.innerHTML = "<li>暂未发现数据缺口。</li>"; return; }
    list.innerHTML = rows.map(function (r) {
      return "<li><strong>" + esc(r["缺口项"]) + "：</strong>" + esc(r["说明"]) +
        "（影响：" + esc(r["影响"]) + "）</li>";
    }).join("");
  }

  /* ---------- 页脚来源 ---------- */
  function renderSources() {
    var ol = document.querySelector("footer .sources ol");
    if (!ol || !D.sources) return;
    var original = ol.innerHTML;   // 03 节政策时间线仍在引用原来的 [1]..[13]
    var items = D.sources.map(function (s) {
      return '<li id="cite-' + esc(s.id) + '">' +
        '<span class="src-title">' + esc(s["标题"] || s["来源"]) + " —— " + esc(s["来源"]) +
        "（" + esc(s["类型"]) + "，" + esc(s["发布时间"]) + "）</span>" +
        '<span class="src-url">来源编号 ' + esc(s.id) + "</span></li>";
    }).join("");
    if (original) {
      // 放进嵌套 <ol>，让背景资料从 1 重新编号，和正文里的 [1]..[13] 对得上
      items += '<li style="list-style:none;margin-top:1.3rem">' +
        '<div style="font-weight:700;color:var(--ink);margin-bottom:.4rem">背景资料（原看板保留）</div>' +
        '<ol style="padding-left:1.2rem">' + original + "</ol></li>";
    }
    ol.innerHTML = items;
  }

  /* ---------- 顶部信息条与说明 ---------- */
  function renderMeta() {
    var meta = D.meta || {};
    var row = document.querySelector(".meta-row");
    if (row) {
      var passed = D.validation && D.validation["通过"];
      var sourceText = (D.sources ? D.sources.length : 0) + " 条公开材料";
      if (meta["岗位样本数"]) sourceText += " + " + meta["岗位样本数"] + " 条岗位样本";
      row.innerHTML =
        '<span class="pill">数据来源 <strong>' + sourceText + "</strong></span>" +
        '<span class="pill">生成时间 <strong>' + esc(meta["生成时间"] || "") + "</strong></span>" +
        '<span class="pill">调用 <strong>' + esc((meta["调用统计"] || {})["调用次数"] || 0) + " 次 · " + esc(meta["耗时秒"] || "") + "s</strong></span>" +
        '<span class="pill pill-ai">AI 生成内容 · ' + (passed ? "引用校验通过" : "待人工核对") + "</span>";
    }

    var lede = document.querySelector(".lede");
    if (lede) {
      lede.innerHTML = "面向专业负责人的人才培养方案动态决策看板：数据由 Python 工作流调用 DeepSeek，从公开材料中抽取岗位、技能、缺口与趋势，" +
        "自动生成专业预警与优化建议。本页为<strong>预生成快照</strong>，重新运行 <code>生成页面数据.py</code> 即可更新数据，页面本身不需要改。";
    }

    var methods = document.querySelectorAll(".method p");
    if (methods[2]) {
      methods[2].textContent = "本页由 Python 工作流预生成：材料 → DeepSeek 抽取 → 数据表 → 数据.js。更新数据只需重跑 生成页面数据.py，无需改动页面代码。";
    }

    var heads = document.querySelectorAll(".sec-head");
    if (heads[3]) {
      var note = heads[3].querySelector(".sec-note");
      if (note) note.textContent = "按「需求信号强度 × 证据可信度」排序，共 " + (D.tables && D.tables["预警表"] ? D.tables["预警表"].rows.length : 0) + " 条";
    }

    var compliance = document.querySelector(".compliance");
    if (compliance) {
      compliance.innerHTML = "<strong>合规说明：</strong>本看板数据由工作流从用户提供的公开材料中抽取，逐条保留来源编号与原文证据；" +
        "图表与建议由 AI 生成，已标注「AI 生成内容」；不含真实个人数据。";
    }

    if (meta["专业群"]) document.title = meta["专业群"] + " · 岗位需求与技能热度智能看板";
  }

  renderKpi();
  renderJobs();
  renderAlerts();
  renderAdvice();
  renderGaps();
  renderSources();
  renderMeta();

  window.dispatchEvent(new Event("talent-data-ready"));
})();
