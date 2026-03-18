#!/bin/bash
set -euo pipefail

echo "entrypoint"

echo "Downloading $NETEX_DATA_URL"
if ! wget -O /tmp/netex_data.zip "$NETEX_DATA_URL"; then
    echo "ERROR: Failed to download NeTEx data from $NETEX_DATA_URL" >&2
    exit 1
fi

if [ ! -s /tmp/netex_data.zip ]; then
    echo "ERROR: Downloaded NeTEx file is empty" >&2
    exit 1
fi

echo "Unzipping to /app/extimeData"
mkdir -p /tmp/netex_data
if ! unzip -o /tmp/netex_data.zip -d /tmp/netex_data; then
    echo "ERROR: Failed to unzip NeTEx data" >&2
    exit 1
fi

echo "Cleaning up zip"
rm -f /tmp/netex_data.zip

exec "$@"
