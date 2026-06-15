# Security Spec

## Data Invariants
- A `session` must be created by a valid, authenticated user.
- A `session` status can only be `waiting`, `running`, or `finished`.
- Only the `ownerId` of the session can update the session's status or `startTime`.
- Any authenticated user can join a session by creating a document in the `clients` subcollection under their own `request.auth.uid`.
- A client can only update their own `elapsedTime` inside the session.

## "Dirty Dozen" Payloads
1. Unauthenticated user creates a session. (Fail)
2. Authenticated user creates session with `ownerId` matching someone else. (Fail)
3. Owner updates session status to an invalid string `hacked`. (Fail)
4. Non-owner updates session `startTime`. (Fail)
5. Client creates registration for another UID. (Fail)
6. Client registers with missing `joinedAt`. (Fail)
7. Client updates someone else's `elapsedTime`. (Fail)
8. Client updates `elapsedTime` with string instead of number. (Fail)
9. Client adds arbitrary ghost fields (e.g. `isAdmin: true`) during registration. (Fail)
10. Owner creates session with ghost field. (Fail)
11. Client queries `clients` list of a session (they can't, unless they are owner... Wait, the controller needs to query the clients. Clients themselves only need to write their time and listen to the session `startTime`!). So we only allow owner to list `clients`. (Fail for non-owner listing clients)
12. Unauthenticated user gets the session. (Fail)
