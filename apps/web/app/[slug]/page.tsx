import { PublicCatalog } from '@/components/bookflow/public-catalog'
export default async function Page({ params }: { params: Promise<{ slug: string }> }) { const { slug } = await params; return <PublicCatalog slug={slug} /> }
