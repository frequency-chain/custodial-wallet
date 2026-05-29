export enum TokenUnits {
  PLANCK = "PLANCK",
  DOT = "DOT",
  WEI = "WEI",
  ETH = "ETH",
}

export interface TokenBalance {
  unit: TokenUnits
  value: number
}
