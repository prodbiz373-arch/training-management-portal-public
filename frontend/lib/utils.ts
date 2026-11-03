// Derived from shadcn/ui. Copyright (c) 2023 shadcn.
// MIT license: see /licenses/shadcn-ui-MIT.txt and /THIRD_PARTY_NOTICES.md
import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
