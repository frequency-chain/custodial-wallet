// Noticed this "cn" function when using bits-ui. It looks like this has become a pretty common thing
// This video explains it better than I could: https://www.youtube.com/watch?v=re2JFITR7TI
// The TL;DW is twMerge handles conflicts, clsx handles args, so we combine them.
// PS: cn stands for classname

import { ClassValue, clsx } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
