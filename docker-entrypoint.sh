#!/bin/bash

echo "entrypoint"

: ${NETEX_DATA_URL="https://storage.googleapis.com/marduk-test/outbound/netex/rb_avi-aggregated-netex.zip"}

echo "Downloading $NETEX_DATA_URL"
wget -O /tmp/netex_data.zip $NETEX_DATA_URL

echo "Unzipping to /app/extimeData"
mkdir -p /tmp/netex_data
unzip -o /tmp/netex_data.zip -d /tmp/netex_data

echo "Cleaning up zip"
rm /tmp/netex_data.zip

exec "$@"
