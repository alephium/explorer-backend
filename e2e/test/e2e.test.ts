import { ExplorerProvider, NodeProvider, ONE_ALPH } from '@alephium/web3'
import { PrivateKeyWallet } from '@alephium/web3-wallet'
import { getSigner, testPrivateKeyWallet } from '@alephium/web3-test'
import  configuration  from './config'
import  { eventually }  from './utils'

describe('e2e', function () {

  let wallet: PrivateKeyWallet

  beforeAll(async () => {
    wallet = await getSigner()
  })

  const nodeProvider = new NodeProvider(configuration.nodeUrl)
  const explorerProvider = new ExplorerProvider(configuration.explorerUrl)

  //Check if the balance of the wallet in the node and the explorer is the same
  async function checkSameBalance(address: string) {
    const nodeBalance = await nodeProvider.addresses.getAddressesAddressBalance(address)
    const explorerBalance = await explorerProvider.addresses.getAddressesAddressBalance(address)

    expect(explorerBalance.balance).toEqual(nodeBalance.balance)
  }

  it('simple transfer', async () => {
    const destinationWallet = await getSigner()

    await eventually(async () => {
      await checkSameBalance(wallet.account.address)
      await checkSameBalance(destinationWallet.account.address)
    })

    const nodeBalance = await nodeProvider.addresses.getAddressesAddressBalance(destinationWallet.account.address)

    await wallet.signAndSubmitTransferTx({
      signerAddress: wallet.account.address,
      destinations: [{
        address: destinationWallet.account.address,
        attoAlphAmount: ONE_ALPH
      }]
    })

    let value:Number = Number((await explorerProvider.addresses.getAddressesAddressBalance(destinationWallet.account.address)).balance)

    const expectedValue:Number = Number(nodeBalance.balance) + Number(ONE_ALPH)

    //Wait until the explorer balance is the same as the expected value
    eventually(async () => Number((await explorerProvider.addresses.getAddressesAddressBalance(destinationWallet.account.address)).balance) === expectedValue)

    //Check explorer-backend is synced with the node
    eventually(async () => {
      checkSameBalance(wallet.account.address)
      checkSameBalance(destinationWallet.account.address)
    })
  })
})
