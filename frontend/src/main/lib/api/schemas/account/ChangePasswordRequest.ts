export interface ChangePasswordRequest {
  //This maps as a BigInteger in kotlin, but I noticed we're using numbers for BigInts elsewhere
  //Should this be a BigInteger type?
  userAccountId: number
  newRawPassword: string
}
