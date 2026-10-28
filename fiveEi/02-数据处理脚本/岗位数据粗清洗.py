import os
import pandas as pd

# ======================== 配置 ========================
# 输入/输出统一放在工程根目录的 04-清洗后数据 里，脚本放在哪个子目录都能跑
_BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_DATA_DIR = os.path.join(_BASE_DIR, "04-清洗后数据")
input_file = os.path.join(_DATA_DIR, 'AI全栈岗位_粗清洗.csv')     # 输入：爬虫清洗后的全量数据
output_file = os.path.join(_DATA_DIR, 'AI全栈岗位_去空技能.csv')   # 输出：剔除技能列全空的行

# ======================== 读取数据 ========================
df = pd.read_csv(input_file, encoding='utf-8-sig')

# 需要检查的技能列（请根据实际列名调整）
skill_columns = ['核心技能标签', '后端技能', '前端技能', 'AI/大模型技能', '工程化/运维技能']

# 判断某一行的所有技能列是否都为空
def is_empty_row(row):
    for col in skill_columns:
        val = row[col]
        if pd.notna(val) and str(val).strip() != '':
            return False
    return True

# 过滤掉全空技能的行
filtered_df = df[~df.apply(is_empty_row, axis=1)].copy()

# 处理序号列：如果已存在则删除，再重新插入连续序号
if '序号' in filtered_df.columns:
    filtered_df = filtered_df.drop(columns=['序号'])

# 在最前面插入新的连续序号（从1开始）
filtered_df.insert(0, '序号', range(1, len(filtered_df) + 1))

# ======================== 保存结果 ========================
filtered_df.to_csv(output_file, index=False, encoding='utf-8-sig')

print(f"处理完成！")
print(f"原始记录数：{len(df)}")
print(f"删除空技能行数：{len(df) - len(filtered_df)}")
print(f"剩余记录数：{len(filtered_df)}")
print(f"结果已保存到：{output_file}")