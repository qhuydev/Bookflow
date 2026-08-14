import { ScheduleFeature } from '@/components/bookflow/schedule-feature'

export default async function Page({ params }: { params: Promise<{ employeeId: string }> }) {
  const { employeeId } = await params
  return <ScheduleFeature employeeId={employeeId} />
}
