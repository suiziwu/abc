# -*- coding: utf-8 -*-
"""工作流自测：注入假客户端，不消耗真实 API 额度。

运行：python -m unittest discover -s tests -v
"""
import json
import os
import re
import sys
import unittest

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE)

from talent_workflow.workflow import generate, load_config, WorkflowError, _fill  # noqa: E402
from talent_workflow.render import render_table  # noqa: E402


def _load_sample():
    with open(os.path.join(BASE, "示例输入.json"), "r", encoding="utf-8-sig") as f:
        return json.load(f)


GOOD_ADVICE = {
    "预警": [{
        "等级": "紧急",
        "标题": "机器视觉缺口明确，课程需对标补齐",
        "关键指标": "行业缺口约12万人",
        "建议动作": "增设《机器视觉检测与调试》实训模块 48 学时",
        "依据": ["S5"],
        "证据": "预计2025年机器视觉行业人才需求缺口达12万人",
    }],
    "建议": [{
        "序号": 1,
        "方向": "课程内容",
        "建议内容": "在智能制造专业群增设机器视觉检测实训模块，48 学时，先在 2 个班试点。",
        "依据": ["S5", "S7"],
        "优先级": "高",
        "预期效果": "覆盖工业视觉系统运维员四级技能要求",
    }],
    "数据缺口": [{
        "缺口项": "月度技能热度曲线",
        "说明": "公开招聘平台没有公开的月度技能提及率",
        "影响": "无法做月度趋势预警",
    }],
}

BAD_ADVICE = json.loads(json.dumps(GOOD_ADVICE, ensure_ascii=False))
BAD_ADVICE["预警"][0]["依据"] = ["S99"]


class FakeClient(object):
    """按调用标签返回固定结构，模拟 DeepSeek。"""

    def __init__(self, advice):
        self.advice = advice
        self.tags = []
        self.stats = {"调用次数": 0, "失败次数": 0, "输入tokens": 0, "输出tokens": 0}

    def chat_json(self, system, user, tag="", temperature=None, max_tokens=None):
        self.tags.append(tag)
        self.stats["调用次数"] += 1
        if tag.startswith("抽取"):
            match = re.search(r"来源编号：(\S+)", user)
            sid = match.group(1) if match else "S1"
            metrics = []
            if sid == "S1":
                metrics = [{"指标": "高技能人才缺口", "数值": 1000, "单位": "万人",
                            "口径": "数字经济、智能制造相关领域高技能人才", "年份": "2025",
                            "来源": "S1", "证据": "高技能人才缺口已突破千万"}]
            elif sid == "S5":
                metrics = [{"指标": "机器视觉人才缺口", "数值": 12, "单位": "万人",
                            "口径": "机器视觉行业", "年份": "2025",
                            "来源": "S5", "证据": "人才需求缺口达12万人"}]
            elif sid == "S6":
                metrics = [{"指标": "数字孪生人才缺口", "数值": 26, "单位": "万人",
                            "口径": "数字孪生领域", "年份": "2026",
                            "来源": "S6", "证据": "数字孪生领域人才缺口约26万"}]
            trends = []
            if sid == "S3":
                trends = [{"岗位": "工业机器人系统操作员", "指标": "需求指数", "数值": 7.33,
                           "单位": "", "统计期": "2024", "来源": "S3", "证据": "需求指数分别为7.33"}]
            if sid == "S4":
                trends = [{"岗位": "机器人调试工程师", "指标": "招聘职位数同比", "数值": 6,
                           "单位": "%", "统计期": "2025-01~05", "来源": "S4",
                           "证据": "招聘职位数同比增长6%"}]
            return {
                "岗位": [{"名称": "工业视觉系统运维员", "职业编码": "6-31-07-02",
                          "来源": sid, "证据": "工业视觉系统运维员"}],
                "技能": [
                    {"技能": "机器视觉", "能力域": "视觉检测", "关联岗位": ["工业视觉系统运维员"],
                     "来源": sid, "证据": "机器视觉检测"},
                    {"技能": "PLC", "能力域": "PLC与自动化", "关联岗位": ["工业机器人系统运维员"],
                     "来源": sid, "证据": "PLC"},
                ],
                "指标": metrics,
                "趋势": trends,
            }
        if tag.startswith("技能归类"):
            return {"分类": []}
        if tag.startswith("建议生成"):
            return self.advice
        if tag.startswith("校验修复"):
            return GOOD_ADVICE
        raise AssertionError("未知调用：%s" % tag)


class WorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cfg = load_config(os.path.join(BASE, "config.json"))
        cls.sample = _load_sample()

    def test_generate_returns_tables(self):
        client = FakeClient(GOOD_ADVICE)
        result = generate(self.sample, config=self.cfg, client=client)

        self.assertIn("tables", result)
        self.assertGreaterEqual(len(result["kpi"]), 4)
        for name in ("岗位需求表", "技能热度表", "预警表", "培养建议表", "数据缺口表"):
            self.assertIn(name, result["tables"])
            self.assertTrue(result["tables"][name]["columns"])
        self.assertTrue(result["tables"]["技能热度表"]["rows"])
        self.assertTrue(result["tables"]["岗位需求表"]["rows"])

        # 岗位样本里的技能也要进入热度表
        skill_names = [row["技能"] for row in result["tables"]["技能热度表"]["rows"]]
        self.assertIn("机器视觉", skill_names)

        # 引用校验应通过
        self.assertTrue(result["validation"]["通过"], result["validation"])
        self.assertEqual(result["meta"]["片段数"], len(self.sample["数据源"]))

    def test_generate_triggers_repair(self):
        client = FakeClient(BAD_ADVICE)
        result = generate(self.sample, config=self.cfg, client=client)
        self.assertIn("校验修复", client.tags)
        self.assertTrue(result["validation"]["通过"])
        self.assertEqual(result["validation"]["轮次"], 2)

    def test_empty_input_raises(self):
        with self.assertRaises(WorkflowError):
            generate({"专业群": "空专业群"}, config=self.cfg, client=FakeClient(GOOD_ADVICE))

    def test_render_table(self):
        text = render_table(["技能", "次数"], [{"技能": "机器视觉", "次数": 3}])
        self.assertIn("技能", text)
        self.assertIn("机器视觉", text)

    def test_fill_chinese_placeholders(self):
        # string.Template 不支持中文占位符，这里锁住自定义替换的行为
        self.assertEqual(_fill("专业群：${专业群}；区域：${区域}", 专业群="智能制造", 区域="东莞"),
                         "专业群：智能制造；区域：东莞")


if __name__ == "__main__":
    unittest.main()
