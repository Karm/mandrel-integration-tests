#!/bin/bash

# Modules differ across versions, so to produce a time series
# spanning very old and very new Quarkus, we need to pick
# just those modules that exist in both. Mind this is a dumb
# script that doesn't take into account renaming.

QTAGS=(
"2.13.3.Final" \
"2.16.12.Final" \
"3.2.12.Final" \
"3.8.4" \
"3.9.5" \
"3.10.0" \
"main"
)

for QTAG in "${QTAGS[@]}"; do
  curl https://raw.githubusercontent.com/quarkusio/quarkus/main/.github/native-tests.json \
    | jq -r '.include | map(."test-modules") | join(",")' > /tmp/$QTAG-modules.txt
done

common_modules=()

for QTAG in "${QTAGS[@]}"; do
    IFS=, read -ra values < <(sed 's/ //g' "/tmp/$QTAG-modules.txt")
    if [[ ${#common_modules[@]} -eq 0 ]]; then
        common_modules=("${values[@]}")
    else
        common_modules=($(comm -1 -2 <(printf "%s\n" "${common_modules[@]}" | tr -d ' ' | sort -u) \
                       <(printf "%s\n" "${values[@]}" | tr -d ' ' | sort -u )))
    fi
done

printf "%s\n" "${common_modules[@]}"
