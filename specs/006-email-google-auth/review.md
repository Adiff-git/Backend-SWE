# Review and decision history

## Decision history

- Email/password registration derives username from email. Google verified email is accepted for new social accounts; existing accounts explicitly Connect Google, never auto-link by matching email. Google-only accounts may optionally set a first password after fresh linked-Google proof and revoke all sessions.
- Registration remains pending until initiating credential, mailbox code and authoritative confirmation password create verified account/session atomically. Legacy access remains. Linking requires fresh original-session CONNECT_GOOGLE proof plus separate provider identity proof. Callbacks never mutate correlation cookies; initiation requests serialize.
- 2026-11-05: User requested removal of all endpoint rate limiting, resend cooldowns and failed-attempt lockouts. Earlier U2/I3 counter/accounting decisions are retired. Applied V13 stays immutable; V14 removes their persistence while preserving business records. No new dependencies.

## Finding disposition

I1/U1: pending ownership, rotation, atomic creation and concurrent conflicts remain covered. I2: fresh original-session proof lifecycle is unchanged. D1: primary artifacts retain separate responsibilities. U3: callback non-mutation, generations, serialized initiation and safe restart remain required. D2: obsolete rate-only tests are removed; email tests retain challenge/outbox rollback, delivery and unrestricted-request coverage. U2/I3 accounting implementations are removed under the latest approved scope.

## Validation ownership

Stable T013-T019 now cover removal and regressions. Current executed evidence belongs in quickstart.md; reviewer checklists assess document quality only. T070 remains external frontend/deployment/real Google/SMTP and five-person walkthrough validation. No fabricated completion is recorded.
