#!/usr/bin/env bash
# usage: capture.sh <outdir>  — dumps responses of the read endpoints for the demo user
out=$1; mkdir -p "$out"
T=$(curl -s -X POST localhost:18080/api/auth/demo-login | python -I -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")
for p in debts debts/overview-graph debts/priorities debts/monthly-status repayment-plans; do
  curl -s -H "Authorization: Bearer $T" localhost:18080/api/$p | python -I -c "import sys,json;print(json.dumps(json.load(sys.stdin),sort_keys=True,indent=1))" > "$out/$(echo $p | tr / _).json"
done
ID=$(python -I -c "import json;print(json.load(open('$out/debts.json'))[0]['debt']['debtId'])")
curl -s -H "Authorization: Bearer $T" localhost:18080/api/debts/$ID/summary | python -I -c "import sys,json;print(json.dumps(json.load(sys.stdin),sort_keys=True,indent=1))" > "$out/summary.json"
