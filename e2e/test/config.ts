export interface Configuration {
  nodeUrl: string,
  explorerUrl: string,
  mnemonic: string
}

const mnemonic = 'vault alarm sad mass witness property virus style good flower rice alpha viable evidence run glare pretty scout evil judge enroll refuse another lava'


const configuration: Configuration = {
  nodeUrl: 'http://127.0.0.1:22973',
  explorerUrl: 'http://127.0.0.1:9090',
  mnemonic: mnemonic
}

export default configuration
