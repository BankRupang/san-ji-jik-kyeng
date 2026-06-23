#!/bin/bash
# Usage: bash .github/scripts/extract_context.sh <changed_files> <diff_file>
# 변경된 파일들의 전체 내용(또는 변경 구간)을 stdout으로 출력

CHANGED_FILES="$1"
DIFF_FILE="$2"

RANGES_TMP=$(mktemp)
trap "rm -f $RANGES_TMP" EXIT

while IFS= read -r file; do
  [ -z "$file" ] && continue
  [ -f "$file" ] || continue

  total_lines=$(wc -l < "$file")
  echo "=== FULL FILE: $file ==="

  if [ "$total_lines" -le 500 ]; then
    cat "$file"
  else
    echo "[파일 크기: ${total_lines}줄 - 선언부 및 변경 구간만 표시]"
    echo "--- 선언부 (1-80줄) ---"
    sed -n '1,80p' "$file"

    # 1단계: diff에서 이 파일의 hunk 시작 줄 수집 후 범위 병합
    # - 파일명은 "diff --git a/... b/..." 헤더에서 b-side 경로를 잘라내어 정확 비교
    # - 선언부(1-80줄)와 겹치는 구간은 81줄부터 시작하도록 조정
    # - 인접/겹치는 구간은 하나로 병합하여 중복 출력 방지
    awk -v f="$file" -v total="$total_lines" '
      /^diff --git/ {
        bpath = $0
        sub(/^.* b\//, "", bpath)
        in_file = (bpath == f)
      }
      in_file && /^@@ / {
        hunk = $0
        sub(/ @@.*$/, "", hunk)
        split(hunk, parts, " ")
        new_part = parts[3]
        sub(/\+/, "", new_part)
        sub(/,.*/, "", new_part)
        val = new_part + 0
        if (val > 0) hunks[++n] = val
      }
      END {
        for (i = 2; i <= n; i++) {
          key = hunks[i]; j = i - 1
          while (j >= 1 && hunks[j] > key) { hunks[j+1] = hunks[j]; j-- }
          hunks[j+1] = key
        }
        prev_s = -1; prev_e = -1
        for (i = 1; i <= n; i++) {
          s = (hunks[i] > 30 ? hunks[i] - 30 : 1)
          if (s < 81) s = 81
          e = (hunks[i] + 100 > total ? total : hunks[i] + 100)
          if (s > e) continue
          if (prev_s == -1) { prev_s = s; prev_e = e }
          else if (s <= prev_e) { if (e > prev_e) prev_e = e }
          else { print prev_s " " prev_e; prev_s = s; prev_e = e }
        }
        if (prev_s != -1) print prev_s " " prev_e
      }
    ' "$DIFF_FILE" > "$RANGES_TMP"

    # 2단계: 파일을 한 번만 읽어 병합된 구간 출력
    if [ -s "$RANGES_TMP" ]; then
      awk '
        NR == FNR { start[NR] = $1+0; end[NR] = $2+0; n = NR; next }
        FNR == 1  { cur = 1 }
        cur <= n  {
          if (FNR == start[cur])
            printf "\n--- 변경 구간 (%d-%d줄) ---\n", start[cur], end[cur]
          if (FNR >= start[cur] && FNR <= end[cur]) print
          if (FNR >= end[cur]) cur++
        }
      ' "$RANGES_TMP" "$file"
    fi
  fi

  echo "=== END FILE: $file ==="
  echo ""
done < "$CHANGED_FILES"
