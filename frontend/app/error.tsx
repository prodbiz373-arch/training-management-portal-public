'use client'
import Link from 'next/link'
export default function Error(){return <main className="flex min-h-svh items-center justify-center p-6"><div className="text-center"><p className="text-6xl font-bold text-[#054A99]">500</p><h1 className="mt-3 text-xl font-bold">エラーが発生しました</h1><p className="mt-2 text-sm text-muted-foreground">時間をおいて再度お試しください。</p><Link href="/home" className="mt-5 inline-flex rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground">ホームへ戻る</Link></div></main>}
