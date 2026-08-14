import {
  ProtectedRequest,
  ScheduleBreak,
  ScheduleBreakInput,
  ScheduleException,
  ScheduleExceptionInput,
  WorkingRuleInput,
  WorkingScheduleRule,
} from './contracts'

const employeeBase = (businessId: string, employeeId: string) =>
  `/api/v1/businesses/${businessId}/employees/${employeeId}`
const rulesBase = (businessId: string, employeeId: string) =>
  `${employeeBase(businessId, employeeId)}/schedule-rules`
const exceptionsBase = (businessId: string, employeeId: string) =>
  `${employeeBase(businessId, employeeId)}/schedule-exceptions`

export const schedulesApi = {
  listRules: (request: ProtectedRequest, businessId: string, employeeId: string) =>
    request<WorkingScheduleRule[]>(rulesBase(businessId, employeeId)),
  createRule: (request: ProtectedRequest, businessId: string, employeeId: string, body: WorkingRuleInput) =>
    request<WorkingScheduleRule>(rulesBase(businessId, employeeId), { method: 'POST', body: JSON.stringify(body) }),
  updateRule: (request: ProtectedRequest, businessId: string, employeeId: string, ruleId: string, body: WorkingRuleInput) =>
    request<WorkingScheduleRule>(`${rulesBase(businessId, employeeId)}/${ruleId}`, { method: 'PATCH', body: JSON.stringify(body) }),
  deleteRule: (request: ProtectedRequest, businessId: string, employeeId: string, ruleId: string) =>
    request<void>(`${rulesBase(businessId, employeeId)}/${ruleId}`, { method: 'DELETE' }),

  listBreaks: (request: ProtectedRequest, businessId: string, employeeId: string, ruleId: string) =>
    request<ScheduleBreak[]>(`${rulesBase(businessId, employeeId)}/${ruleId}/breaks`),
  createBreak: (request: ProtectedRequest, businessId: string, employeeId: string, ruleId: string, body: ScheduleBreakInput) =>
    request<ScheduleBreak>(`${rulesBase(businessId, employeeId)}/${ruleId}/breaks`, { method: 'POST', body: JSON.stringify(body) }),
  updateBreak: (request: ProtectedRequest, businessId: string, employeeId: string, ruleId: string, breakId: string, body: ScheduleBreakInput) =>
    request<ScheduleBreak>(`${rulesBase(businessId, employeeId)}/${ruleId}/breaks/${breakId}`, { method: 'PATCH', body: JSON.stringify(body) }),
  deleteBreak: (request: ProtectedRequest, businessId: string, employeeId: string, ruleId: string, breakId: string) =>
    request<void>(`${rulesBase(businessId, employeeId)}/${ruleId}/breaks/${breakId}`, { method: 'DELETE' }),

  listExceptions: (request: ProtectedRequest, businessId: string, employeeId: string) =>
    request<ScheduleException[]>(exceptionsBase(businessId, employeeId)),
  createException: (request: ProtectedRequest, businessId: string, employeeId: string, body: ScheduleExceptionInput) =>
    request<ScheduleException>(exceptionsBase(businessId, employeeId), { method: 'POST', body: JSON.stringify(body) }),
  updateException: (request: ProtectedRequest, businessId: string, employeeId: string, exceptionId: string, body: ScheduleExceptionInput) =>
    request<ScheduleException>(`${exceptionsBase(businessId, employeeId)}/${exceptionId}`, { method: 'PATCH', body: JSON.stringify(body) }),
  deleteException: (request: ProtectedRequest, businessId: string, employeeId: string, exceptionId: string) =>
    request<void>(`${exceptionsBase(businessId, employeeId)}/${exceptionId}`, { method: 'DELETE' }),
}
