#!/bin/bash

echo "entrypoint"

echo "Downloading $NETEX_DATA_URL"
wget -O /tmp/netex_data.zip $NETEX_DATA_URL

exec "$@"
