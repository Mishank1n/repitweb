#!/bin/sh
# Держит SSH-туннель к localhost.run и пишет выданный адрес в лог и в /tunnel/url.
# Адрес меняется при каждом подключении; при обрыве туннель переподключается через 5 секунд.
set -u
while true; do
  rm -f /tunnel/url
  ssh -T \
      -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/tmp/known_hosts \
      -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -o ExitOnForwardFailure=yes \
      -R 80:app:8080 nokey@localhost.run 2>&1 |
  while IFS= read -r line; do
    url=$(printf '%s\n' "$line" | grep -oE 'https://[a-z0-9-]+\.lhr\.life' | head -n 1)
    if [ -n "$url" ]; then
      printf '%s\n' "$url" > /tunnel/url.tmp && mv /tunnel/url.tmp /tunnel/url
      echo "Адрес сайта для телефона: $url"
    fi
  done
  echo "Туннель отключился, переподключение через 5 с"
  sleep 5
done
