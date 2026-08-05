# Security review: Authentication và refresh token

- **Ticket:** BF-010
- **Ngày review:** 2026-08-03
- **Phạm vi:** thiết kế email/password, access JWT, refresh session và browser security
- **Quyết định nguồn:** [ADR 0001](../adr/0001-authentication-and-refresh-token.md)

BF-010 chỉ hoàn tất security design. Mọi mitigation dưới đây là yêu cầu cho ticket triển khai tương lai, không phải xác nhận rằng runtime hiện đã có authentication.

## Phạm vi và trust boundaries

### Tài sản

- Password và Argon2id hash.
- Raw refresh token, token hash và authentication session.
- Access JWT và signing private key.
- Global user identity và account status.
- CSRF token/cookie, audit event và metadata thiết bị tối thiểu.

### Trust boundaries

- Browser ↔ API qua HTTPS.
- API ↔ PostgreSQL, nơi giữ authority của session.
- API ↔ Redis, chỉ hỗ trợ rate limit/cache.
- Signer/verifier ↔ secret manager hoặc mounted secret.
- Admin/security operations ↔ audit store và key lifecycle.

Authorization nghiệp vụ và tenant isolation chưa thuộc review này. JWT BF-010 không mang role, permission, `business_id` hoặc `tenant_id`.

## Abuse cases

1. Attacker thử credential đã lộ trên nhiều account và né giới hạn IP bằng botnet.
2. Attacker đo khác biệt response/timing để dò email tồn tại.
3. XSS đọc access token trong memory hoặc gửi request dưới phiên nạn nhân.
4. Refresh cookie bị lấy từ thiết bị, proxy/log sai cấu hình hoặc malware rồi bị replay.
5. Hai tab đồng thời refresh cùng cookie và làm family bị revoke nhầm.
6. Attacker sửa `alg`, `kid`, issuer, audience hoặc token type để bypass JWT validation.
7. CSRF buộc browser login vào account attacker, refresh, logout hoặc logout-all.
8. CORS wildcard/reflection cho origin không tin cậy đọc response có credentials.
9. Operator hoặc code ghi password/token/cookie/private key vào log, URL hoặc telemetry.
10. Password change/account disable nhưng session cũ tiếp tục cấp access token.
11. Permanent account lockout bị lạm dụng để từ chối dịch vụ cho nạn nhân.
12. Race giữa rotate/revoke làm nhiều refresh token active hoặc cấp hai access token.

## Risk and mitigation matrix

Likelihood/Impact dùng thang Low/Medium/High theo bối cảnh MVP. Cột “Owner” là ticket/lĩnh vực phải hiện thực hóa control.

| Threat | Asset và attack scenario | Likelihood | Impact | Mitigation bắt buộc | Residual risk | Test dự kiến | Owner |
|---|---|---|---|---|---|---|---|
| Credential stuffing | Account; bot dùng credential breach | High | High | Blocklist, IP + account rate limit, progressive delay, generic 401, audit | Botnet phân tán vẫn có thể thử chậm | HTTP 429, account/IP counters, log event | Auth implementation + Redis rate limit |
| Brute-force password | Password/account; đoán nhiều password | Medium | High | Argon2id, 15-char minimum, rate limit, no permanent lock | Slow distributed attempts | Unit hash cost; integration throttle | Auth implementation |
| User/email enumeration | Identity/privacy; so response/timing | High | Medium | Cùng 401/detail, dummy Argon2id verify, không lộ account status | Timing side channel nhỏ vẫn có thể tồn tại | So response schema và timing distribution | Auth implementation |
| Database password-hash leak | Password hash; offline cracking | Medium | High | Argon2id 19 MiB/t=2/p=1 tối thiểu, random salt, blocklist, rehash policy | Password yếu vẫn có thể bị crack | Unit encoded parameters, unique salts | Auth implementation |
| Refresh-token theft | Long-lived session; cookie/device bị lấy | Medium | High | 256-bit opaque token, HttpOnly/Secure/host-only, hash at rest, rotation | Malware/browser compromise vẫn có thể lấy/use cookie | Cookie HTTP test, DB không chứa raw token | Auth implementation + frontend |
| Refresh-token replay | Session family; token cũ dùng lại | Medium | High | Rotation, parent chain, reuse detection, family compromise/revoke, audit | Reuse trong 5 giây không revoke nhưng không cấp token | Integration replay trước/sau window | Auth implementation |
| Concurrent refresh nhiều tab | Availability/session; hai request dùng token active | High | Medium | Atomic conditional update, 5-second tolerance, 409 retryable, single-flight client | Response ordering có thể cần retry UX | Barrier test với hai transaction thật | Auth implementation + frontend |
| Access-token theft | API access; JWT bị lấy qua XSS/memory | Medium | High | Memory-only, HTTPS, 10-minute TTL, no URL/log, CSP/output encoding | Token dùng được tới `exp` | HTTP/log scan; expiry test | Auth + frontend security |
| JWT algorithm confusion | Identity; attacker đổi algorithm/key type | Low | Critical | Allow chính xác RS256, từ chối `none`, không tin header algorithm, đúng key type | Library/config regression | `alg=none`, HS/RS confusion tests | Auth implementation |
| Signing-key compromise | Toàn bộ identity; attacker tự ký JWT | Low | Critical | Secret manager, signer isolation, `kid`, rotation/runbook, no logs | JWT đã giả mạo trước detection | Key rotation/unknown kid tests, operational drill | Security operations |
| Session fixation | Session identity; ép victim dùng family attacker | Low | High | Login luôn tạo family/token mới, không nhận session ID client cung cấp | Login CSRF nếu CSRF control sai | Login creates new unique family | Auth implementation |
| CSRF login | Victim browser; login vào account attacker | Medium | Medium | Spring CSRF cho login, Origin/Fetch Metadata defense, SameSite | XSS bypass được browser control | Missing/invalid CSRF rejected | Auth implementation + frontend |
| CSRF refresh/logout | Session availability/token; forged state change | Medium | High | Spring CSRF cookie/header, SameSite=Lax, exact origin checks, no GET mutation | Same-site compromised origin | HTTP CSRF and Origin tests | Auth implementation |
| XSS token theft/action | Access token/session action qua script độc | Medium | High | Memory access token, HttpOnly refresh, CSP, encoding, avoid unsafe HTML | XSS vẫn gửi request hợp lệ trong page | Frontend security tests/CSP checks | Frontend implementation |
| CORS misconfiguration | API response; malicious origin reads credentials | Medium | High | Exact allowlist, never wildcard with credentials, no origin reflection | Environment config drift | CORS matrix across trusted/untrusted origins | Auth/platform implementation |
| Token/credential leakage qua log | Credential; exception/access log/telemetry ghi secret | Medium | Critical | Redaction, no body/cookie/token logging, safe ProblemDetail/audit | Third-party agent misconfiguration | Captured-log negative assertions | Auth + operations |
| Token leakage qua URL/referrer | Access token; token trong query/history/referrer | Low | High | Chỉ Authorization header/cookie, reject/document no token URLs | Client bug | HTTP/client contract tests and log scan | Auth + frontend |
| Stolen device | Refresh session; browser profile bị lấy | Medium | High | Per-device family, logout device/all, inactivity/absolute expiry, future session UI | Attacker dùng session trước revoke | Integration logout current/all | Auth implementation |
| Account disabled nhưng session sống | Account status; refresh tiếp tục | Medium | High | Revoke all sessions transactionally on disable; refresh checks session/user | Access JWT còn sống tối đa 10 phút | Disable/revoke integration test | Auth + user management ticket |
| Password changed nhưng session cũ sống | Account; attacker giữ session sau credential reset | Medium | High | Revoke all family on change/reset, audit event | Access JWT còn sống tối đa 10 phút | Password-change revocation test | Auth + password lifecycle ticket |
| Clock skew/expired acceptance | JWT/session; token quá hạn vẫn được nhận | Medium | High | UTC, strict `exp`/`iat`, skew ≤30s, absolute expiry in DB | Clock infrastructure drift | Boundary tests ±30s, expired/not-yet-valid | Auth + operations |
| Tenant/role claim confusion | Tenant data; client/token tự mang authority chưa thiết kế | Medium | Critical | Không có role/tenant claim trong BF-010; BF-011 quyết định server-side authority | BF-011 implementation có thể sai | Assert claims absent; future tenant tests | BF-011 |
| DoS qua account lockout | Availability; attacker khóa account nạn nhân | Medium | Medium | Temporary throttle/progressive delay, không permanent lock, IP + account signals | Temporary degradation | Repeated failure then recovery test | Auth implementation |
| Race rotate/revoke | Session consistency; hai transaction cùng thắng hoặc resurrect token | Medium | Critical | PostgreSQL transaction, conditional update/row lock, constraints; Redis không là authority | Deadlock/retry handling cần cẩn thận | Deterministic barrier test, rollback test | Auth implementation |

## Residual risks

- JWT đã phát hành không bị logout/revoke tức thì và có thể dùng đến 10 phút cộng tối đa 30 giây skew.
- XSS vẫn có thể hành động trong context người dùng dù refresh cookie là HttpOnly.
- Tolerance 5 giây giảm false-positive nhưng trì hoãn family-compromise signal cho replay trong window; request thua không được cấp token.
- Không có MFA/passkey, email verification, compromised-account recovery hoặc high-risk re-authentication trong BF-010.
- Rate limiting phân tán, secret-manager integration và key-compromise response chưa có runtime implementation.
- Device label/user-agent/IP chỉ là tín hiệu không đáng tin tuyệt đối và phải giới hạn retention.
- Tenant authorization chưa được thiết kế; không được hiểu global identity là quyền vào business.

## Security review checklist

### Quyết định đã chốt

- [x] Authentication, authorization và tenant isolation được tách ranh giới.
- [x] JWT access token 10 phút, claims tối thiểu, không có tenant/role/PII.
- [x] RS256 allowlist, `kid`, issuer/audience/type validation và skew 30 giây.
- [x] Opaque refresh token ≥256-bit, hash SHA-256 unique, không lưu raw token.
- [x] Session family, rotation, replay revocation và concurrent window 5 giây.
- [x] Cookie HttpOnly, production Secure, SameSite=Lax, host-only, scoped Path.
- [x] Spring Security CSRF và exact-origin CORS.
- [x] Argon2id baseline và NIST-style password/email policy.
- [x] Login generic error, layered rate limit và không permanent-lock.
- [x] Logout, logout-all, account/password revocation semantics.
- [x] PostgreSQL là session source of truth; Redis không là final authority.
- [x] Data retention, log redaction và privacy boundary được xác định.
- [x] API contract, threat matrix, tests, alternatives và residual risks được ghi nhận.

### Gate cho ticket triển khai

- [x] Không còn quyết định thiết kế bắt buộc chưa được chốt.
- [x] Mọi control chưa triển khai được diễn đạt là requirement/test tương lai, không phải runtime guarantee.
- [x] Không có credential, private key hoặc token mẫu thật trong tài liệu.
- [x] BF-011 vẫn chịu trách nhiệm authorization và tenant isolation.

## Test matrix cho ticket triển khai

| Nhóm | Scenario | Kỳ vọng |
|---|---|---|
| Password unit | Hash cùng password hai lần | Hai encoded hash khác nhau; cả hai verify đúng |
| Password unit | Argon2 parameters | Không thấp hơn 19 MiB, t=2, p=1 |
| Password unit | Unicode password | NFC trước hash; không trim/lowercase |
| Email unit | Whitespace/case/Unicode | Normalized ổn định; không áp dụng Gmail dot/plus rule |
| JWT unit | Claims hợp lệ | Có iss/sub/aud/iat/exp/jti/sid và không có tenant/role/PII |
| JWT negative | `alg=none`, algorithm khác, sai signature | Bị từ chối |
| JWT negative | Sai/thiếu issuer, audience, subject, type | Bị từ chối |
| JWT boundary | Expired/not-yet-valid/skew | Chỉ tolerance tối đa 30 giây |
| Key rotation | Current/previous/unknown `kid` | Current và previous trong window hợp lệ; unknown/expired old key bị từ chối |
| Login HTTP | Account thiếu và password sai | Cùng 401 `AUTH_INVALID_CREDENTIALS`, không enumeration |
| Login HTTP | Validation/rate limit | 400 hoặc 429 `AUTH_RATE_LIMITED`; response/log sạch |
| Login integration | Login thành công | Tạo family duy nhất, DB chỉ có token hash, body không có refresh token |
| Cookie HTTP | Login/refresh | HttpOnly, production Secure, SameSite=Lax, host-only, Path đúng, expiry bounded |
| CSRF HTTP | Login/refresh/logout thiếu hoặc sai CSRF | Bị từ chối; Origin không tin cậy bị từ chối |
| CORS HTTP | Credentialed trusted/untrusted origin | Exact allowlist; không wildcard/reflection |
| Refresh integration | Happy path | Token cũ ROTATED, token mới ACTIVE cùng family, access token mới |
| Refresh integration | Token cũ lần hai trong 5 giây | 409, không cấp token, không revoke family, không xóa cookie mới |
| Refresh integration | Replay sau 5 giây | Không cấp token; family COMPROMISED/revoked; audit event |
| Refresh concurrency | Hai request qua executor/barrier | Hai transaction cạnh tranh thật; đúng một rotation thắng |
| Refresh rollback | Lỗi sau update trước commit | Không để family không nhất quán hoặc hai ACTIVE token |
| Session integration | Inactivity/absolute expiry | Không refresh quá 7/30 ngày; sliding không vượt absolute |
| Revocation integration | Logout current | Chỉ family hiện tại revoked; 204 idempotent |
| Revocation integration | Logout-all/password/account disabled | Mọi family của user bị revoke |
| Token-type HTTP | Access token làm refresh và ngược lại | Bị từ chối an toàn |
| Logging security | Capture success/error logs | Không password, raw token, cookie, hash, key hoặc internal ProblemDetail |
| OpenAPI regression | Auth docs tương lai | Không credential example; BF-009 metadata/UI vẫn hoạt động |
| Error regression | Unknown route | BF-008 ProblemDetail contract không đổi |
| Cleanup integration | Terminal/expired token records | Chỉ record vượt retention bị xóa; active family không bị ảnh hưởng |

Concurrency test phải dùng executor/barrier hoặc công cụ tương đương, bắt đầu hai transaction trước cùng điểm tranh chấp và assert database state cuối. Không dùng hai lời gọi tuần tự hoặc sleep dài để giả lập race.

## Security events tối thiểu trong tương lai

- Login thành công/thất bại hoặc throttle đáng ngờ.
- Refresh concurrent và refresh replay.
- Family/session revoked, compromised hoặc expired.
- Logout-all, password change/reset và account disabled.
- Signing-key rotation hoặc validation failure bất thường.

Event chỉ chứa identifier tối thiểu, timestamp, outcome, reason enum và metadata sanitize. Không chứa password, raw token, cookie, private key, token hash hoặc decoded claim nhạy cảm.

## Kết luận review

Thiết kế BF-010 đáp ứng gate để chuyển sang ticket triển khai authentication: các quyết định về token, session, browser security, password, data lifecycle, concurrency và lỗi đã được chốt. Trạng thái “đạt” ở đây chỉ áp dụng cho thiết kế; runtime hiện chưa có authentication endpoint hoặc security control.
