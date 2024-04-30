import { ExplorerProvider, NodeProvider } from '@alephium/web3'
import { PrivateKeyWallet } from '@alephium/web3-wallet'
import  configuration  from './config'

describe('e2e', () => {

  const nodeProvider = new NodeProvider(configuration.nodeUrl)
  const explorerProvider = new ExplorerProvider(configuration.explorerUrl)

  const oneAlph = 10n ** 18n

  const wallet = PrivateKeyWallet.FromMnemonic({
    mnemonic: configuration.mnemonic,
    nodeProvider
  })

  //Check if the balance of the wallet in the node and the explorer is the same
  async function checkSameBalance(address: string) {
    const nodeBalance = await nodeProvider.addresses.getAddressesAddressBalance(address)
    const explorerBalance = await explorerProvider.addresses.getAddressesAddressBalance(address)

    expect(explorerBalance.balance).toEqual(nodeBalance.balance)
  }

  it('simple transfer', async () => {
    const destinationWallet = PrivateKeyWallet.FromMnemonic({
      mnemonic: configuration.mnemonic,
      addressIndex: 1,
      nodeProvider
    })

    checkSameBalance(wallet.account.address)
    checkSameBalance(destinationWallet.account.address)

    const nodeBalance = await nodeProvider.addresses.getAddressesAddressBalance(destinationWallet.account.address)

    await wallet.signAndSubmitTransferTx({
      signerAddress: wallet.account.address,
      destinations: [{
        address: destinationWallet.account.address,
        attoAlphAmount: oneAlph
      }]
    })

    let value:Number = Number((await explorerProvider.addresses.getAddressesAddressBalance(destinationWallet.account.address)).balance)

    const expectedValue:Number = Number(nodeBalance.balance) + Number(oneAlph)

    //Wait until the explorer balance is the same as the expected value
    while (Number(value) !== expectedValue) {
      await new Promise(resolve => setTimeout(resolve, 500)); // Wait for 500ms
      value = Number((await explorerProvider.addresses.getAddressesAddressBalance(destinationWallet.account.address)).balance)
    }

    checkSameBalance(wallet.account.address)
    checkSameBalance(destinationWallet.account.address)
  })
})
