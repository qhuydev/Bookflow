export type Branch = { id: string; code: string; name: string; city: string; timeZone: string; phone: string; status: 'ACTIVE' | 'ARCHIVED' }
export type Employee = { id: string; code: string; fullName: string; phone?: string; email?: string; bio: string; branchIds: string[]; status: 'ACTIVE' | 'ARCHIVED' }
export type Member = { id: string; email: string; role: 'OWNER' | 'ADMIN' | 'STAFF'; status: 'ACTIVE' | 'REVOKED'; employeeId?: string }
export type Service = { id: string; name: string; description: string; price: string; currency: string; durationMinutes: number; bufferBeforeMinutes: number; bufferAfterMinutes: number; branchIds: string[]; employeeIds: string[]; status: 'ACTIVE' | 'ARCHIVED' }

export const initialBranches: Branch[] = [
  { id: 'nguyen-hue', code: 'Q1', name: 'Nguyễn Huệ', city: 'Quận 1, TP. Hồ Chí Minh', timeZone: 'Asia/Ho_Chi_Minh', phone: '028 3822 1000', status: 'ACTIVE' },
  { id: 'thao-dien', code: 'TD', name: 'Thảo Điền', city: 'TP. Thủ Đức', timeZone: 'Asia/Ho_Chi_Minh', phone: '028 3744 2200', status: 'ACTIVE' },
]
export const initialEmployees: Employee[] = [
  { id: 'linh', code: 'NV-01', fullName: 'Linh Trần', phone: '0901 234 567', email: 'linh@example.test', bio: 'Tư vấn viên giàu kinh nghiệm, yêu thích trải nghiệm chỉn chu.', branchIds: ['nguyen-hue', 'thao-dien'], status: 'ACTIVE' },
  { id: 'minh', code: 'NV-02', fullName: 'Minh Nguyễn', bio: 'Chuyên viên thân thiện, hỗ trợ khách hàng tận tâm.', branchIds: ['nguyen-hue'], status: 'ACTIVE' },
]
export const initialMembers: Member[] = [
  { id: 'mai', email: 'mai.ngoc@example.test', role: 'OWNER', status: 'ACTIVE' },
  { id: 'linh-member', email: 'linh@example.test', role: 'STAFF', status: 'ACTIVE', employeeId: 'linh' },
]
export const initialServices: Service[] = [
  { id: 'consult', name: 'Tư vấn chuyên sâu', description: 'Một phiên tư vấn riêng, tập trung vào nhu cầu của bạn.', price: '350000', currency: 'VND', durationMinutes: 60, bufferBeforeMinutes: 10, bufferAfterMinutes: 10, branchIds: ['nguyen-hue'], employeeIds: ['linh', 'minh'], status: 'ACTIVE' },
  { id: 'care', name: 'Chăm sóc định kỳ', description: 'Gói dịch vụ linh hoạt cho khách hàng quay lại.', price: '250000', currency: 'VND', durationMinutes: 45, bufferBeforeMinutes: 0, bufferAfterMinutes: 5, branchIds: ['thao-dien'], employeeIds: ['linh'], status: 'ACTIVE' },
]
