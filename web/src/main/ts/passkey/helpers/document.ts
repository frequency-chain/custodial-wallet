export const getElementByIdOrThrow = (elementId: string): HTMLElement => {
  const element = document.getElementById(elementId)
  if (element === null) {
    throw new Error(`Unable to locate element with id '${elementId}'`)
  }

  return element
}
