# CSV destination addresses

CSV exports omit change addresses separately for ALPH and each token. An address
present in the inputs is still a destination when its total output amount for
that asset exceeds its total input amount. Repeated outputs are summed before
making this decision; destination addresses remain distinct and retain output
order. Input addresses with zero or negative net amounts are omitted as change.
Amounts, token decimal formatting, and the FromAddresses column are unchanged.

This fixes contract withdrawals where the recipient also supplies an input to
pay transaction fees, and token swaps where the recipient supplies the same token
in an input. The address columns summarize net transfers; they do not describe
individual contract calls or attribute every output to a particular input.

`TransactionCsvSpec` loads `csv-contract-537.json`, a local snapshot of mainnet
transaction `bdc1527ccd296d2f2676604c247ee864e13a3bfd8149d860a6538371f2235319`
linked in issue #537. The fixture was obtained from the public explorer API at
`https://backend.mainnet.alephium.org/transactions/` followed by that hash; only
the outer Accepted type tag was removed to read it as a Transaction. Tests never
contact the network. Derived token cases cover duplicate outputs, positive net
receipts, unchanged balances, burns, and independent ALPH/token destinations.
`ApiModelSpec` also checks that ordinary transfer change remains excluded.

Run the focused regressions with:

```sh
sbt 'app/testOnly org.alephium.explorer.api.model.TransactionCsvSpec org.alephium.explorer.api.model.ApiModelSpec'
```
