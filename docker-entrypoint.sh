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

echo "Unzipping to /tmp/netex_data"
mkdir -p /tmp/netex_data
if ! unzip -o /tmp/netex_data.zip -d /tmp/netex_data; then
    echo "ERROR: Failed to unzip NeTEx data" >&2
    exit 1
fi

rm -f /tmp/netex_data.zip

echo "Downloading $STOP_PLACE_DATA_URL"
if ! wget -O /tmp/stop_place_data.zip "$STOP_PLACE_DATA_URL"; then
    echo "ERROR: Failed to download stop place data from $STOP_PLACE_DATA_URL" >&2
    exit 1
fi

if [ ! -s /tmp/stop_place_data.zip ]; then
    echo "ERROR: Downloaded stop place file is empty" >&2
    exit 1
fi

echo "Unzipping to /tmp/stop_place_data"
mkdir -p /tmp/stop_place_data
if ! unzip -o /tmp/stop_place_data.zip -d /tmp/stop_place_data; then
    echo "ERROR: Failed to unzip stop place data" >&2
    exit 1
fi

rm -f /tmp/stop_place_data.zip

exec "$@"
