#!/bin/sh

function checkAddressBalance() {
  if [[ ! -z "$1" ]]; then
    node_balance=`curl -s "http://127.0.0.1:12973/addresses/$1/balance" | jq -r '.balance'`
    explorer_balance=`curl -s "http://127.0.0.1:9090/addresses/$1/balance" | jq -r '.balance'`
    if [[ $node_balance != $explorer_balance ]]; then
      echo "$1 wrong balance. Node: $node_balance, explorer-backend: $explorer_balance"
    fi
  fi
}

export -f checkAddressBalance

psql -U postgres -d devnet -t -c \
  "SELECT address FROM transaction_per_addresses ORDER BY RANDOM() limit $1" | \
  xargs -I {} sh -c "checkAddressBalance {}"
