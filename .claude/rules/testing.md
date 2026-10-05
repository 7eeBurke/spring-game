# Testing Rules

Deterministic game rules require automated tests.

## Generation Tests

For seeded generation, test:
- reproducibility from the same `runSeed` + `generationContextSnapshot`;
- variation from different seeds;
- all invariants and hard constraints;
- min/max limits;
- invalid-state rejection;
- graph reachability and progression guarantees.

## Combat Tests

Test:
- stat modifier mapping;
- DC composition;
- suitability adjustments;
- success-band boundaries;
- HP damage;
- trauma thresholds;
- body-part escalation;
- condition eligibility;
- defensive mitigation;
- cancelled multi-step actions.

## AI Integration Tests

Do not call a live model from unit tests.
Use mocked responses or stored contract fixtures.
Test malformed JSON, unknown IDs, hidden-state leakage attempts, prompt-injection text and unsupported references.

## Persistence Tests

Verify:
- scene state survives reload;
- discovered secrets remain discovered;
- removed items do not respawn;
- dead enemies stay dead;
- generated introductions do not change;
- the same `runSeed` + `generationContextSnapshot` can reproduce generated structure during test/debug workflows.

Database integration tests use Testcontainers PostgreSQL and never call a live LLM.
