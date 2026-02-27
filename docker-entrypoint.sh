#!/bin/bash

echo "entrypoint"

: ${NETEX_DATA_URL="https://storage.googleapis.com/marduk-test/outbound/netex/rb_avi-aggregated-netex.zip"}

echo "Downloading $NETEX_DATA_URL"
wget -O /app/netex_data.zip $NETEX_DATA_URL

exec "$@"
