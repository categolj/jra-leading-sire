import os
import json
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib import rcParams
import matplotlib.font_manager as fm

# 日本語フォントの設定
# font_path = '/usr/share/fonts/truetype/takao-gothic/TakaoPGothic.ttf'  # Linuxの場合
# font_path = 'C:/Windows/Fonts/YuGothB.ttc'  # Windowsの場合
font_path = '/System/Library/Fonts/ヒラギノ角ゴシック W3.ttc'  # macOSの場合

if not os.path.exists(font_path):
    raise FileNotFoundError("フォントが見つかりません。適切なフォントパスを設定してください。")

# フォントを適用
font_prop = fm.FontProperties(fname=font_path)
rcParams['font.family'] = font_prop.get_name()

# JSONデータが格納されているディレクトリのパス
data_dir = "../report"  # 相対パスで 'report/' フォルダを指定
files = sorted([f for f in os.listdir(data_dir) if f.endswith(".json")])

# トップ数を変数化
TOP_N = 10  # ここでトップ数を指定（例: Top 50）

# データの統合
data = []
for file in files:
    with open(os.path.join(data_dir, file), 'r', encoding='utf-8') as f:
        day_data = json.load(f)
        date = file.split('.')[0]  # yyyy-mm-ddを抽出
        for entry in day_data:
            data.append({"date": date, "name": entry["name"], "prize": entry["prize"], "rank": entry["rank"]})

# DataFrame作成
df = pd.DataFrame(data)
df['date'] = pd.to_datetime(df['date'])

# 最新日付を取得
latest_date = df['date'].max()

# 最新日付時点でのトップNの種牡馬を取得
latest_top_n = (
    df[df['date'] == latest_date]
    .nsmallest(TOP_N, 'rank')  # rankが小さい順にトップNを取得
    ['name']
    .tolist()
)

# トップNの種牡馬に絞り込み
filtered_df = df[df['name'].isin(latest_top_n)]

# 可視化
plt.figure(figsize=(15, 10))
for horse in latest_top_n:
    horse_df = filtered_df[filtered_df['name'] == horse]
    plt.plot(horse_df['date'], horse_df['prize'], marker='o', label=horse)

plt.title(f"リーディングサイヤー賞金の時系列変動（{latest_date.date()}時点のTop {TOP_N}）", fontsize=16)
plt.xlabel("日付", fontsize=12)
plt.ylabel("賞金（円）", fontsize=12)
plt.legend(title="種牡馬", bbox_to_anchor=(1.05, 1), loc='upper left', fontsize=8)
plt.grid(True)
plt.tight_layout()
plt.show()